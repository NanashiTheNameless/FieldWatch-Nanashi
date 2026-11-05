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
import android.net.ConnectivityManager
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
import dev.namelessnanashi.fieldwatch.elevated.ElevatedMemory
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
    const val NOTIF_FAILED = 4104
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
        val cached = cachedApk(app, note)
        if (cached == null && !settings.updateCellular && metered(app)) {
            // Mobile data is off for updates. Try again once an unmetered network is up.
            scheduleRetry(app, 0L, JobInfo.NETWORK_TYPE_UNMETERED)
            return@withLock Outcome.WAITING
        }
        val apk = cached ?: download(app, note) ?: return@withLock Outcome.FAILED
        val wait = AppUpdateIdle.waitMs(lastTouchMs, SystemClock.elapsedRealtime())
        if (wait > 0L) {
            scheduleRetry(app, wait)
            return@withLock Outcome.WAITING
        }
        install(app, apk, notifyAllow = true)
    }

    /**
     * Installs a verified [apk] now, with no prompt wherever Android allows it. Used by
     * "Update now" too, so a tapped update is as quiet as an automatic one. Call off the main thread.
     */
    suspend fun installNow(app: FieldwatchApp, apk: File): Outcome = lock.withLock {
        if (!available) return@withLock Outcome.NONE
        install(app, apk, notifyAllow = false)
    }

    private suspend fun install(app: FieldwatchApp, apk: File, notifyAllow: Boolean): Outcome {
        val settings = app.config.settings
        val scanning = app.isReady && app.devices.stats.value.scanning
        AppUpdateResume.write(
            app.filesDir,
            AppUpdateResume.Marker(scanning, BuildConfig.VERSION_NAME, System.currentTimeMillis()),
        )
        val allowed = AppUpdateInstall.allowed(app)
        // Android asks every time while another app owns updates. Shizuku or root goes around it.
        val ownedElsewhere = otherUpdateOwner(app) != null
        if (!allowed || ownedElsewhere) {
            if (settings.updateElevatedInstall && elevatedInstall(app, apk)) return Outcome.INSTALLING
            if (!allowed) {
                // Without the install switch the session would fail.
                AppUpdateResume.clear(app.filesDir)
                if (notifyAllow) notifyNeedsAllow(app)
                return Outcome.FAILED
            }
        }
        if (!commit(app, apk)) {
            AppUpdateResume.clear(app.filesDir)
            return Outcome.FAILED
        }
        return Outcome.INSTALLING
    }

    /**
     * The package that owns this app's updates on Android 14+, when it is not this app.
     * Android shows its prompt for every update while that holds.
     */
    fun otherUpdateOwner(context: Context): String? {
        if (Build.VERSION.SDK_INT < 34) return null
        val owner = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).updateOwnerPackageName
        }.getOrNull() ?: return null
        return owner.takeIf { it != context.packageName }
    }

    /** True on mobile data, or on Wi-Fi the phone treats as metered. */
    private fun metered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.isActiveNetworkMetered
    }

    /**
     * Drops the downloaded APK and the copy staged for Shizuku or root. Called once the app has been
     * replaced: the installed build is at least as new, so neither file can install again.
     */
    fun clearDownloads(context: Context) {
        runCatching { File(context.cacheDir, "update").listFiles()?.forEach { it.delete() } }
        runCatching { context.getExternalFilesDir("update")?.listFiles()?.forEach { it.delete() } }
    }

    /** Android refused an install that had started. The reason is Android's own text, when it gives one. */
    fun notifyFailed(context: Context, reason: String?) {
        val body = reason?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { LanguageText.text("update.failed.reason", mapOf("reason" to it)) }
            ?: LanguageText.text("update.failed.body")
        post(
            context,
            NOTIF_FAILED,
            LanguageText.text("update.failed.title"),
            body,
            Intent(context, MainActivity::class.java),
        )
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
        return shellInstall(app, apk)
    }

    /**
     * `pm install -r -i <this app>` through Shizuku, else root, skipping root when the last
     * Check root said no. The caller has already checked [apk].
     */
    private suspend fun shellInstall(app: FieldwatchApp, apk: File): Boolean {
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
        val plain = "pm install -r -i $pkg --user current '$path' || pm install -r --user current '$path'"
        // Android 14+: claim update ownership too, so later self-updates skip the prompt.
        val command = if (Build.VERSION.SDK_INT >= 34) {
            "pm install -r -i $pkg --update-ownership --user current '$path' || $plain"
        } else {
            plain
        }
        val shells = buildList {
            if (app.shizuku.state() == ShizukuShell.State.READY) add(app.shizuku)
            // Root that was checked and refused stays skipped until Check root says otherwise.
            if (ElevatedMemory.root(app.filesDir) != false) add(RootShell)
        }
        for (shell in shells) {
            val result = runCatching { shell.run(command) }.getOrNull() ?: continue
            if (result.ok && result.output.contains("Success")) return true
            Log.w(TAG, "${shell.label} install failed: ${result.output.take(200)}")
        }
        staged.delete()
        return false
    }

    /** Who put this app on the phone, and who Android lets update it without asking. */
    data class InstallSource(val installer: String?, val updateOwner: String?) {
        fun ownedHere(pkg: String): Boolean =
            installer == pkg && (Build.VERSION.SDK_INT < 34 || updateOwner == null || updateOwner == pkg)
    }

    fun installSource(context: Context): InstallSource {
        val info = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull()
        return InstallSource(
            installer = info?.installingPackageName,
            updateOwner = if (Build.VERSION.SDK_INT >= 34) info?.updateOwnerPackageName else null,
        )
    }

    enum class Claim { DONE, CONFIRM, NEEDS_ALLOW, FAILED }

    /**
     * Makes this app its own installer and, on Android 14+, its own update owner, by installing the
     * copy already on the phone over itself. Through Shizuku or root it needs no tap. Otherwise
     * Android asks once. The app restarts either way; a running scan starts again.
     */
    suspend fun claimOwnership(app: FieldwatchApp): Claim = lock.withLock {
        val info = app.applicationInfo
        val apks = buildList {
            add(File(info.sourceDir))
            info.splitSourceDirs?.forEach { add(File(it)) }
        }
        if (apks.any { !it.isFile }) return@withLock Claim.FAILED
        val scanning = app.isReady && app.devices.stats.value.scanning
        AppUpdateResume.write(
            app.filesDir,
            AppUpdateResume.Marker(scanning, BuildConfig.VERSION_NAME, System.currentTimeMillis()),
        )
        // Split installs need install-multiple; the nightly is one APK, so the shell only takes that.
        if (apks.size == 1 && shellInstall(app, apks[0])) return@withLock Claim.DONE
        if (!AppUpdateInstall.allowed(app)) {
            AppUpdateResume.clear(app.filesDir)
            return@withLock Claim.NEEDS_ALLOW
        }
        if (!commit(app, apks)) {
            AppUpdateResume.clear(app.filesDir)
            return@withLock Claim.FAILED
        }
        Claim.CONFIRM
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

    /**
     * The verified APK for [note]: the cached one, else a fresh download. Shares the updater's lock,
     * so "Update now" and an automatic update never download over each other; whichever comes
     * second waits and reuses the file. [running] false stops the download.
     */
    suspend fun fetchApk(context: Context, note: AppUpdateNote, running: () -> Boolean = { true }): File? =
        lock.withLock {
            cachedApk(context, note) ?: if (running()) download(context, note, running) else null
        }

    private fun download(context: Context, note: AppUpdateNote, running: () -> Boolean = { true }): File? {
        val got = AppUpdateClient.downloadApk(context.cacheDir, userAgent = userAgent(), running = running)
            ?: return null
        if (!AppUpdateInstall.trusted(context, got, note.versionCode, note.versionName)) {
            got.delete()
            return null
        }
        return got
    }

    /** Silent self-update. Android 12+ skips the prompt when the app updates itself. */
    private fun commit(context: Context, apk: File): Boolean = commit(context, listOf(apk))

    private fun commit(context: Context, apks: List<File>): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apks.sumOf { it.length() })
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            // Take over update ownership, so once this install lands the next ones need no prompt,
            // even when a store or another installer put the app on the phone.
            if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
        }
        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apks.forEachIndexed { i, apk ->
                    val name = if (i == 0) "base.apk" else "split$i.apk"
                    session.openWrite(name, 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
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
    private fun scheduleRetry(context: Context, waitMs: Long, network: Int = JobInfo.NETWORK_TYPE_ANY) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_RETRY, ComponentName(context, AppUpdateJobService::class.java))
            .setMinimumLatency(waitMs + 5_000L)
            .setRequiredNetworkType(network)
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
