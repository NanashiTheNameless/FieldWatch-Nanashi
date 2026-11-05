package dev.namelessnanashi.fieldwatch.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.namelessnanashi.fieldwatch.FieldwatchApp
import dev.namelessnanashi.fieldwatch.domain.AppUpdateResume
import dev.namelessnanashi.fieldwatch.domain.LanguageText

/** Result of an automatic install. Success replaces this process, so only the other outcomes land here. */
class AppUpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                val app = context.applicationContext as FieldwatchApp
                val pending = goAsync()
                app.scope.launch {
                    try {
                        // Shizuku or root can finish it with no tap. Otherwise Android's prompt stays.
                        val elevated = app.awaitConfiguration() && app.config.settings.updateElevatedInstall &&
                            withContext(Dispatchers.IO) {
                                AppUpdateAuto.downloadedApk(context)?.let { AppUpdateAuto.elevatedInstall(app, it) } == true
                            }
                        if (!elevated) askToConfirm(context, confirm)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> {
                AppUpdateResume.clear(context.filesDir)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("FieldwatchUpdate", "Automatic install failed ($status): $message")
                // Aborted means the prompt was cancelled. That was a choice, not a failure to report.
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) AppUpdateAuto.notifyFailed(context, message)
            }
        }
    }

    /** On screen the prompt can open at once. Otherwise a notification waits for a tap. */
    private fun askToConfirm(context: Context, confirm: Intent) {
        val opened = AppUpdateAuto.visibleActivities > 0 && runCatching {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (opened) return
        AppUpdateAuto.post(
            context,
            AppUpdateAuto.NOTIF_ACTION,
            LanguageText.text("update.available.title"),
            LanguageText.text("update.confirm.body"),
            confirm,
        )
    }
}
