package dev.namelessnanashi.fieldwatch.radio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.namelessnanashi.fieldwatch.FieldwatchApp
import dev.namelessnanashi.fieldwatch.domain.disclaimerOk
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Android delivers BOOT_COMPLETED after unlock; preferences stay in credential-protected storage. */
class ScanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FieldwatchApp
        if (app.pendingCrash != null) return
        val pending = goAsync()
        app.scope.launch {
            try {
                // Keep the broadcast bounded, while sharing the activity/service startup load.
                if (withTimeoutOrNull(8_000L) { app.awaitConfiguration() } != true) return@launch
                val settings = app.config.settings
                if (!settings.scanOnBoot || !settings.disclaimerOk()) return@launch
                if (!RadioPermissions.granted(context) ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) return@launch
                runCatching { app.startScanning() }
                    .onFailure { Log.w("FieldwatchBoot", "Automatic scan start refused", it) }
            } finally {
                pending.finish()
            }
        }
    }
}
