package dev.namelessnanashi.fieldwatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import dev.namelessnanashi.fieldwatch.ui.StartupLogo
import dev.namelessnanashi.fieldwatch.ui.screen.StartupFailureScreen
import dev.namelessnanashi.fieldwatch.alert.Alerter
import dev.namelessnanashi.fieldwatch.data.AppUpdateAuto
import dev.namelessnanashi.fieldwatch.data.AppUpdateInstall
import dev.namelessnanashi.fieldwatch.domain.AlertRadioSnapshot
import dev.namelessnanashi.fieldwatch.radio.RadioPermissions
import dev.namelessnanashi.fieldwatch.ui.FieldwatchRoot
import dev.namelessnanashi.fieldwatch.ui.FieldwatchViewModel
import dev.namelessnanashi.fieldwatch.ui.screen.CrashNoteScreen
import dev.namelessnanashi.fieldwatch.ui.theme.FieldwatchTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_INSTALL_SETTINGS = "dev.namelessnanashi.fieldwatch.extra.OPEN_INSTALL_SETTINGS"
    }

    private val vm: FieldwatchViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val app = application as FieldwatchApp
        // A permission result can be restored before an asynchronously recreated app is ready.
        lifecycleScope.launch {
            if (app.pendingCrash != null || !app.awaitStartup()) return@launch
            lifecycle.withResumed {
                vm.refreshPermissions()
                if (RadioPermissions.granted(this@MainActivity)) {
                    app.refreshFix()
                    vm.resumeScan()
                }
                askInstallPermissionOnce(app)
            }
        }
    }

    /** Android's Install unknown apps page. Nothing comes back; the switch is read when needed. */
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    /**
     * Automatic updates need Install unknown apps for this app. Asked once, after the radio
     * permissions, and only while automatic updates are on. Settings → Elevated access can grant it too.
     */
    private fun askInstallPermissionOnce(app: FieldwatchApp) {
        if (!AppUpdateAuto.available || !app.config.settings.updateAutoInstall) return
        if (AppUpdateInstall.allowed(this)) return
        val asked = java.io.File(filesDir, "install-permission-asked.txt")
        if (asked.exists()) return
        runCatching { asked.writeText("1") }
        runCatching { installPermissionLauncher.launch(AppUpdateInstall.settingsIntent(this)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FieldwatchApp
        if (app.pendingCrash != null) {
            showCrashNote(app)
            return
        }
        showApp()
        if (savedInstanceState == null) openInstallSettingsIfAsked(intent)
    }

    override fun onStart() {
        super.onStart()
        AppUpdateAuto.visibleActivities++
        AppUpdateAuto.touch()
        // Every return from the background looks for a newer nightly. Before startup is ready the
        // view model cannot be built yet; the READY branch in showApp covers that launch.
        val app = application as FieldwatchApp
        if (app.pendingCrash == null && app.isReady) vm.checkForUpdateOnOpen()
    }

    override fun onStop() {
        AppUpdateAuto.visibleActivities = (AppUpdateAuto.visibleActivities - 1).coerceAtLeast(0)
        super.onStop()
    }

    /** Any touch or key pushes an automatic install back by the idle wait. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        AppUpdateAuto.touch()
    }

    override fun onResume() {
        super.onResume()
        val app = application as FieldwatchApp
        if (app.pendingCrash != null || !app.isReady) return
        vm.refreshPermissions()
        if (RadioPermissions.granted(this) && !app.devices.stats.value.scanning) {
            vm.resumeScan()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openInstallSettingsIfAsked(intent)
        if (!(application as FieldwatchApp).isReady) return
        openAlerted(intent)
    }

    /** The "allow installs" notification lands here; the settings page opens from inside the app. */
    private fun openInstallSettingsIfAsked(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_INSTALL_SETTINGS, false) != true) return
        intent.removeExtra(EXTRA_OPEN_INSTALL_SETTINGS)
        runCatching { startActivity(AppUpdateInstall.settingsIntent(this)) }
    }

    /** Opens the radio an alert was about. The key is dropped so a rotation does not open it again. */
    private fun openAlerted(intent: android.content.Intent?) {
        val key = intent?.getStringExtra(Alerter.EXTRA_DEVICE_KEY) ?: return
        val snapshot = AlertRadioSnapshot.decode(intent.getStringExtra(Alerter.EXTRA_DEVICE_SNAPSHOT), key)
        intent.removeExtra(Alerter.EXTRA_DEVICE_KEY)
        intent.removeExtra(Alerter.EXTRA_DEVICE_SNAPSHOT)
        vm.openFromAlert(key, snapshot)
    }

    private fun showCrashNote(app: FieldwatchApp) {
        val pending = app.pendingCrash
        if (pending == null) {
            showApp()
            return
        }
        setContent {
            FieldwatchTheme {
                CrashNoteScreen(
                    note = pending.text,
                    onCopy = { copyNote(pending.text) },
                    onContinue = {
                        app.dismissCrashNote()
                        showApp()
                    },
                )
            }
        }
    }

    private fun showApp() {
        val app = application as FieldwatchApp
        setContent {
            val startup by app.startup.collectAsStateWithLifecycle()
            when (startup) {
                StartupState.LOADING -> FieldwatchTheme { StartupLogo() }
                StartupState.FAILED -> FieldwatchTheme { StartupFailureScreen(app::retryStartup) }
                StartupState.READY -> {
                    LaunchedEffect(Unit) {
                        vm.checkForUpdateOnOpen()
                        vm.refreshPermissions()
                        if (RadioPermissions.granted(this@MainActivity)) {
                            vm.resumeScan()
                            // Already set up from an older version: the radio prompt will not show.
                            askInstallPermissionOnce(app)
                        }
                        openAlerted(intent)
                    }
                    FieldwatchRoot(vm) {
                        permissionLauncher.launch(RadioPermissions.required())
                    }
                }
            }
        }
    }

    private fun copyNote(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Fieldwatch crash", text))
    }
}
