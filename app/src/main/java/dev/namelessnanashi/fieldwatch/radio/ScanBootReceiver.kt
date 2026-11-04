package dev.namelessnanashi.fieldwatch.radio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.namelessnanashi.fieldwatch.BuildConfig
import dev.namelessnanashi.fieldwatch.FieldwatchApp
import dev.namelessnanashi.fieldwatch.MainActivity
import dev.namelessnanashi.fieldwatch.data.AppUpdateAuto
import dev.namelessnanashi.fieldwatch.domain.AppUpdateResume
import dev.namelessnanashi.fieldwatch.domain.LanguageText
import dev.namelessnanashi.fieldwatch.domain.disclaimerOk
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Android delivers BOOT_COMPLETED after unlock; preferences stay in credential-protected storage. */
class ScanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val replaced = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && !replaced) return
        val app = context.applicationContext as FieldwatchApp
        // An automatic update leaves a marker. Read it once, even if the rest bails out.
        val resume = if (replaced) AppUpdateResume.take(context.filesDir, System.currentTimeMillis()) else null
        if (app.pendingCrash != null) return
        val pending = goAsync()
        app.scope.launch {
            var resumed = false
            try {
                // Keep the broadcast bounded, while sharing the activity/service startup load.
                if (withTimeoutOrNull(8_000L) { app.awaitConfiguration() } != true) return@launch
                val settings = app.config.settings
                if (!settings.disclaimerOk() || !RadioPermissions.granted(context)) return@launch
                val wanted = settings.scanOnBoot || resume?.scanning == true
                if (!wanted) return@launch
                // Boot needs background location. The resume after an update tries either way.
                if (resume?.scanning != true &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) return@launch
                resumed = runCatching { app.startScanning() }
                    .onFailure { Log.w("FieldwatchBoot", "Automatic scan start refused", it) }
                    .isSuccess
            } finally {
                if (resume != null) announceUpdate(context, resume.scanning && resumed)
                pending.finish()
            }
        }
    }

    private fun announceUpdate(context: Context, resumed: Boolean) {
        val values = mapOf("version" to BuildConfig.VERSION_NAME)
        AppUpdateAuto.post(
            context,
            AppUpdateAuto.NOTIF_DONE,
            LanguageText.text("update.done.title"),
            LanguageText.text(if (resumed) "update.done.resumed" else "update.done.body", values),
            Intent(context, MainActivity::class.java),
        )
    }
}
