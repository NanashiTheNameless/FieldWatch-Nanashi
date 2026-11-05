package dev.namelessnanashi.fieldwatch.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.namelessnanashi.fieldwatch.BuildConfig
import dev.namelessnanashi.fieldwatch.FieldwatchApp
import dev.namelessnanashi.fieldwatch.MainActivity
import dev.namelessnanashi.fieldwatch.R
import dev.namelessnanashi.fieldwatch.domain.AppUpdate
import dev.namelessnanashi.fieldwatch.domain.AppUpdateIdle
import dev.namelessnanashi.fieldwatch.domain.AppUpdateNote
import dev.namelessnanashi.fieldwatch.domain.AppUpdateResume
import dev.namelessnanashi.fieldwatch.domain.AppUpdateSkip
import dev.namelessnanashi.fieldwatch.domain.LanguageText
import dev.namelessnanashi.fieldwatch.domain.UpdateCheckEvery
import dev.namelessnanashi.fieldwatch.elevated.RootShell
import dev.namelessnanashi.fieldwatch.elevated.ShizukuShell
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background side of the updater: periodic checks, the idle wait, the silent self-update, and the
 * notifications around it. Debug builds never get here, because the nightly is a different package.
 */
object AppUpdateAuto {
    private const val TAG = "FieldwatchUpdate"
    private const val CHANNEL = "fieldwatch_update"
    private const val NOTIF_AVAILABLE = 4101
    const val NOTIF_DONE = 4102
    const val NOTIF_ACTION = 4103
    private const val JOB_PERIODIC = 7301
    private const val JOB_RETRY = 7302

    /** Elapsed-realtime of the last touch on the app. 0 means none in this process. */
    @Volatile
    var lastTouchMs: Long = 0L
        private set

    /** Started activities. Above zero means the app is on screen. */
    @Volatile
    var visibleActivities: Int = 0

    private val lock = Mutex()

    val available: Boolean get() = !BuildConfig.DEBUG

    fun touch() {
        lastTouchMs = SystemClock.elapsedRealtime()
    }

    enum class Outcome { NONE, NOTIFIED, WAITING, INSTALLING, FAILED }

    /**
     * Looks for a newer nightly and acts on it. With automatic updates on, it downloads, then
     * installs once the app has been idle; otherwise it posts a notification when [announce] is set.
     * Call off the main thread.
     */
    suspend fun run(app: FieldwatchApp, announce: Boolean): Outcome = lock.withLock {
        if (!available) return@withLock Outcome.NONE
        val settings = app.config.settings
        if (!PlaceLookup.online(app)) return@withLock Outcome.NONE
        val note = AppUpdateClient.fetchNote(userAgent = userAgent())?.let(AppUpdate::parse)
            ?: return@withLock Outcome.NONE
        if (note.versionCode <= BuildConfig.VERSION_CODE) return@withLock Outcome.NONE
        if (!settings.updateAutoInstall) {
            if (announce && AppUpdateSkip.read(app.filesDir) != note.versionCode) {
                notifyAvailable(app, note)
                return@withLock Outcome.NOTIFIED
            }
            return@withLock Outcome.NONE
        }
        val apk = cachedApk(app, note) ?: download(app, note) ?: return@withLock Outcome.FAILED
        val wait = AppUpdateIdle.waitMs(lastTouchMs, SystemClock.elapsedRealtime())
        if (wait > 0L) {
            scheduleRetry(app, wait)
            return@withLock Outcome.WAITING
        }
        val scanning = app.isReady && app.devices.stats.value.scanning
        AppUpdateResume.write(
            app.filesDir,
            AppUpdateResume.Marker(scanning, BuildConfig.VERSION_NAME, System.currentTimeMillis()),
        )
        if (!AppUpdateInstall.allowed(app)) {
            // Without the install switch the session would fail. Shizuku or root does not need it.
            if (settings.updateElevatedInstall && elevatedInstall(app, apk)) return@withLock Outcome.INSTALLING
            AppUpdateResume.clear(app.filesDir)
            notifyNeedsAllow(app)
            return@withLock Outcome.FAILED
        }
        if (!commit(app, apk)) {
            AppUpdateResume.clear(app.filesDir)
            return@withLock Outcome.FAILED
        }
        Outcome.INSTALLING
    }

    /** The verified APK left by the last download, for the Shizuku or root fallback. */
    fun downloadedApk(context: Context): File? =
        File(File(context.cacheDir, "update"), "Fieldwatch.apk").takeIf { it.isFile }

    /**
     * Installs [apk] through Shizuku, else root, as `pm install`. The shell user cannot read this
     * app's private cache, so the file is copied to its external files first. `-i` names this app
     * as the installer, so Android lets later self-updates through without a prompt.
     * The APK is checked again first: the same package, a newer version, the installed certificate.
     */
    suspend fun elevatedInstall(app: FieldwatchApp, apk: File): Boolean {
        if (!available || !apk.isFile) return false
        val archive = AppUpdateInstall.archiveVersion(app, apk) ?: return false
        if (!AppUpdateInstall.trusted(app, apk, archive.first, archive.second)) return false
        val dir = app.getExternalFilesDir("update") ?: return false
        val staged = File(dir, "Fieldwatch.apk")
        val copied = runCatching {
            dir.mkdirs()
            apk.copyTo(staged, overwrite = true)
            true
        }.getOrDefault(false)
        if (!copied) return false
        val pkg = app.packageName
        val path = staged.absolutePath
        val command = "pm install -r -i $pkg --user current '$path' || pm install -r --user current '$path'"
        val shells = buildList {
            if (app.shizuku.state() == ShizukuShell.State.READY) add(app.shizuku)
            add(RootShell)
        }
        for (shell in shells) {
            val result = runCatching { shell.run(command) }.getOrNull() ?: continue
            if (result.ok && result.output.contains("Success")) return true
            Log.w(TAG, "${shell.label} install failed: ${result.output.take(200)}")
        }
        staged.delete()
        return false
    }

    private fun cachedApk(context: Context, note: AppUpdateNote): File? {
        val file = File(File(context.cacheDir, "update"), "Fieldwatch.apk")
        if (!file.isFile) return null
        return file.takeIf { AppUpdateInstall.trusted(context, it, note.versionCode, note.versionName) }
            ?: run {
                file.delete()
                null
            }
    }

    private fun download(context: Context, note: AppUpdateNote): File? {
        val got = AppUpdateClient.downloadApk(context.cacheDir, userAgent = userAgent()) ?: return null
        if (!AppUpdateInstall.trusted(context, got, note.versionCode, note.versionName)) {
            got.delete()
            return null
        }
        return got
    }

    /** Silent self-update. Android 12+ skips the prompt when the app updates itself. */
    private fun commit(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(context, AppUpdateStatusReceiver::class.java).setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    status,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Automatic install did not start", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    /** Keeps the periodic job in step with Settings. Safe to call often. */
    fun sync(context: Context, every: UpdateCheckEvery) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!available || every.periodMs <= 0L) {
            js.cancel(JOB_PERIODIC)
            js.cancel(JOB_RETRY)
            return
        }
        val existing = js.getPendingJob(JOB_PERIODIC)
        if (existing != null && existing.intervalMillis == every.periodMs) return
        val job = JobInfo.Builder(JOB_PERIODIC, ComponentName(context, AppUpdateJobService::class.java))
            .setPeriodic(every.periodMs)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(job) }.onFailure { Log.w(TAG, "Could not schedule update checks", it) }
    }

    /** One more try after the idle wait. A later touch pushes it back again. */
    private fun scheduleRetry(context: Context, waitMs: Long) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_RETRY, ComponentName(context, AppUpdateJobService::class.java))
            .setMinimumLatency(waitMs + 5_000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        runCatching { js.schedule(job) }.onFailure { Log.w(TAG, "Could not schedule the install retry", it) }
    }

    private fun notifyAvailable(context: Context, note: AppUpdateNote) {
        post(
            context,
            NOTIF_AVAILABLE,
            LanguageText.text("update.available.title"),
            LanguageText.text("update.available.body", mapOf("version" to note.versionName)),
            Intent(context, MainActivity::class.java),
        )
    }

    private fun notifyNeedsAllow(context: Context) {
        post(
            context,
            NOTIF_ACTION,
            LanguageText.text("update.allow.title"),
            LanguageText.text("update.needs.allow.body"),
            // Explicit: MainActivity opens the settings page itself, so no implicit Intent sits in the PendingIntent.
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_INSTALL_SETTINGS, true),
        )
    }

    fun post(context: Context, id: Int, title: String, body: String, tap: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, LanguageText.text("update.channel"), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { nm.notify(id, notification) }
    }

    private fun userAgent(): String = "Fieldwatch/${BuildConfig.VERSION_NAME}"
}
