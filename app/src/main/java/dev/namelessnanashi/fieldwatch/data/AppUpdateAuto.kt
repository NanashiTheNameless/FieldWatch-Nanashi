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
import dev.namelessnanashi.fieldwatch.domain.Sighting
import dev.namelessnanashi.fieldwatch.domain.UpdateCheckEvery
import dev.namelessnanashi.fieldwatch.elevated.ElevatedMemory
import dev.namelessnanashi.fieldwatch.elevated.RootShell
import dev.namelessnanashi.fieldwatch.elevated.ShizukuShell
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private const val NOTIF_PROGRESS = 4105
    private const val CHANNEL_PROGRESS = "fieldwatch_update_progress"
    private const val JOB_PERIODIC = 7301
    private const val JOB_RETRY = 7302
    const val EXTRA_AUTOMATIC = "dev.namelessnanashi.fieldwatch.extra.AUTOMATIC_UPDATE"

    /** Begin with a full observation window, including when a background job starts the process. */
    @Volatile
    var lastWatchedMs: Long = SystemClock.elapsedRealtime()
        private set

    /** Started activities. Above zero means the app is on screen. */
    @Volatile
    var visibleActivities: Int = 0

    private val lock = Mutex()
    private var retryWork: Job? = null

    val available: Boolean get() = !BuildConfig.DEBUG

    fun noteWatched(app: FieldwatchApp, devices: List<Sighting>) {
        if (AppUpdateIdle.watched(devices, app.config.watchlist)) {
            watching()
        }
    }

    /** A fresh hear during Hunt also protects a radio that was not bookmarked. */
    fun watching() {
        lastWatchedMs = SystemClock.elapsedRealtime()
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
        // A downloaded update can install while offline. Retries need neither GitHub nor Wi-Fi.
        if (settings.updateAutoInstall) {
            val cached = downloadedApk(app)
            val version = cached?.let { AppUpdateInstall.archiveVersion(app, it) }
            if (cached != null && version != null &&
                AppUpdateInstall.trusted(app, cached, version.first, version.second)
            ) return@withLock installWhenQuiet(app, cached)
        }
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
        val apk = cached ?: download(app, note, announce = true) ?: return@withLock Outcome.FAILED
        installWhenQuiet(app, apk)
    }

    private fun quietWait(): Long = AppUpdateIdle.waitMs(lastWatchedMs, SystemClock.elapsedRealtime())

    private suspend fun installWhenQuiet(app: FieldwatchApp, apk: File): Outcome {
        // Settings and watch activity may have changed during the download or the retry delay.
        if (!app.config.settings.updateAutoInstall) return Outcome.NONE
        val wait = quietWait()
        if (wait > 0L) {
            retryInstall(app, wait)
            return Outcome.WAITING
        }
        return install(app, apk, automatic = true)
    }

    /**
     * Installs a verified [apk] now, with no prompt wherever Android allows it. Used by
     * "Update now" too, so a tapped update is as quiet as an automatic one. Call off the main thread.
     */
    suspend fun installNow(app: FieldwatchApp, apk: File): Outcome = lock.withLock {
        if (!available) return@withLock Outcome.NONE
        install(app, apk, automatic = false)
    }

    private suspend fun install(app: FieldwatchApp, apk: File, automatic: Boolean): Outcome {
        // Another callback/check must not submit a second session while Android is installing.
        if (app.packageManager.packageInstaller.mySessions.any { it.isCommitted }) return Outcome.INSTALLING
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
            if (settings.updateElevatedInstall && elevatedInstall(app, apk, automatic)) return Outcome.INSTALLING
            if (automatic || !allowed) {
                // Without the install switch the session would fail.
                AppUpdateResume.clear(app.filesDir)
                if (automatic) {
                    retryInstall(app, AppUpdateIdle.IDLE_MS)
                    return Outcome.WAITING
                }
                return Outcome.FAILED
            }
        }
        if (automatic && (!app.config.settings.updateAutoInstall || quietWait() > 0L)) {
            AppUpdateResume.clear(app.filesDir)
            retryInstall(app, quietWait())
            return Outcome.WAITING
        }
        if (!commit(app, apk, automatic)) {
            AppUpdateResume.clear(app.filesDir)
            if (automatic) retryInstall(app, AppUpdateIdle.IDLE_MS)
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
    suspend fun elevatedInstall(app: FieldwatchApp, apk: File, automatic: Boolean = false): Boolean {
        if (!available || !apk.isFile) return false
        val archive = AppUpdateInstall.archiveVersion(app, apk) ?: return false
        if (!AppUpdateInstall.trusted(app, apk, archive.first, archive.second)) return false
        return shellInstall(app, apk, automatic)
    }

    /**
     * `pm install -r -i <this app>` through Shizuku, else root, skipping root when the last
     * Check root said no. The caller has already checked [apk].
     */
    private suspend fun shellInstall(app: FieldwatchApp, apk: File, automatic: Boolean = false): Boolean {
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
            val root = ElevatedMemory.root(app.filesDir)
            if (root == true || (!automatic && root == null)) add(RootShell)
        }
        for (shell in shells) {
            if (automatic && (!app.config.settings.updateAutoInstall || quietWait() > 0L)) break
            val result = try {
                shell.run(command)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
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

    /** [announce] shows a quiet progress notification while it runs; automatic updates set it. */
    private fun download(
        context: Context,
        note: AppUpdateNote,
        running: () -> Boolean = { true },
        announce: Boolean = false,
    ): File? {
        val progress = if (announce) DownloadNotice(context, note.versionName) else null
        progress?.show(0L, 0L)
        val got = try {
            AppUpdateClient.downloadApk(
                context.cacheDir,
                userAgent = userAgent(),
                running = running,
                progress = { done, total -> progress?.show(done, total) },
            )
        } finally {
            progress?.clear()
        }
            ?: return null
        if (!AppUpdateInstall.trusted(context, got, note.versionCode, note.versionName)) {
            got.delete()
            return null
        }
        return got
    }

    /** Silent self-update. Android 12+ skips the prompt when the app updates itself. */
    private fun commit(context: Context, apk: File, automatic: Boolean): Boolean =
        commit(context, listOf(apk), automatic)

    private fun commit(context: Context, apks: List<File>, automatic: Boolean = false): Boolean {
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
                val status = Intent(context, AppUpdateStatusReceiver::class.java)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_AUTOMATIC, automatic)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    status,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                if (automatic) {
                    val app = context.applicationContext as FieldwatchApp
                    if (!app.config.settings.updateAutoInstall || quietWait() > 0L) {
                        session.abandon()
                        return false
                    }
                }
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

    /** One more try after the quiet period. A later watched hear pushes it back again. */
    private fun scheduleRetry(context: Context, waitMs: Long, network: Int = JobInfo.NETWORK_TYPE_ANY) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_RETRY, ComponentName(context, AppUpdateJobService::class.java))
            .setMinimumLatency(waitMs + 5_000L)
            .setRequiredNetworkType(network)
            .build()
        runCatching { js.schedule(job) }.onFailure { Log.w(TAG, "Could not schedule the install retry", it) }
    }

    /** The live process installs on time; JobScheduler covers process death and background limits. */
    private fun retryInstall(app: FieldwatchApp, waitMs: Long) {
        scheduleRetry(app, waitMs, JobInfo.NETWORK_TYPE_NONE)
        retryWork?.cancel()
        retryWork = app.scope.launch(Dispatchers.IO) {
            delay(waitMs.coerceAtLeast(1_000L))
            retryWork = null
            try {
                run(app, announce = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not retry the automatic update", e)
            }
        }
    }

    /** An automatic session never opens Android's confirmation UI. Retry silently when eligible. */
    suspend fun pendingAutomatic(app: FieldwatchApp, sessionId: Int) = lock.withLock {
        runCatching { app.packageManager.packageInstaller.abandonSession(sessionId) }
        if (app.awaitConfiguration() && app.config.settings.updateAutoInstall) {
            val apk = downloadedApk(app)
            if (quietWait() == 0L && app.config.settings.updateElevatedInstall && apk != null &&
                elevatedInstall(app, apk, automatic = true)
            ) return@withLock
            retryInstall(app, quietWait().coerceAtLeast(AppUpdateIdle.IDLE_MS))
        }
        AppUpdateResume.clear(app.filesDir)
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

    /**
     * "Downloading the update" with a progress bar, on its own low-importance channel so it makes no
     * sound. Redrawn at most every whole percent or half second; removed when the download ends.
     */
    private class DownloadNotice(private val context: Context, versionName: String) {
        private val nm = context.getSystemService(NotificationManager::class.java)
        private var lastPercent = -1
        private var lastMs = 0L
        private val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(LanguageText.text("update.downloading"))
            .setContentText(LanguageText.text("update.downloading.body", mapOf("version" to versionName)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIF_PROGRESS,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

        init {
            if (nm?.getNotificationChannel(CHANNEL_PROGRESS) == null) {
                runCatching {
                    nm?.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_PROGRESS,
                            LanguageText.text("update.channel.progress"),
                            NotificationManager.IMPORTANCE_LOW,
                        ),
                    )
                }
            }
        }

        fun show(done: Long, total: Long) {
            nm ?: return
            val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else -1
            val now = SystemClock.elapsedRealtime()
            if (lastPercent != -1 && (percent == lastPercent || now - lastMs < 500L)) return
            lastPercent = percent.coerceAtLeast(0)
            lastMs = now
            if (percent < 0) builder.setProgress(0, 0, true) else builder.setProgress(100, percent, false)
            runCatching { nm.notify(NOTIF_PROGRESS, builder.build()) }
        }

        fun clear() {
            runCatching { nm?.cancel(NOTIF_PROGRESS) }
        }
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
