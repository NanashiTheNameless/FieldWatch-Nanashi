package dev.namelessnanashi.fieldwatch.domain

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The small GitHub file that says which version is newest, and whether this phone should ask.
 * The notes are the publisher's sentences, shown as written.
 */
data class AppUpdateNote(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
)

sealed class AppUpdateDecision {
    data class Newer(val note: AppUpdateNote) : AppUpdateDecision()
    data object Quiet : AppUpdateDecision()
}

/** What the open app is asking the person to do. Null means no update dialog. */
sealed class AppUpdatePrompt {
    data object Checking : AppUpdatePrompt()
    data class Offer(val note: AppUpdateNote) : AppUpdatePrompt()
    data class Current(val versionName: String) : AppUpdatePrompt()
    data object Unreachable : AppUpdatePrompt()
    data object Downloading : AppUpdatePrompt()
    data object Failed : AppUpdatePrompt()
    data object Allow : AppUpdatePrompt()
}

object AppUpdate {
    const val MAX_NOTE_BYTES = 16 * 1024
    const val MAX_NOTES_CHARS = 6_000
    const val MAX_APK_BYTES = 32 * 1024 * 1024

    private val versionName = Regex("""\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?""")
    private val json = Json { ignoreUnknownKeys = true }

    /** Null when a required field is missing, the wrong type, or the notes are blank. */
    fun parse(text: String): AppUpdateNote? {
        if (text.isBlank() || text.length > MAX_NOTE_BYTES) return null
        val obj = try {
            json.parseToJsonElement(text.trim().trimStart('\uFEFF')) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val codeEl = obj["versionCode"] as? JsonPrimitive ?: return null
        if (codeEl.isString) return null
        val code = codeEl.intOrNull ?: return null
        if (code <= 0) return null
        val nameEl = obj["versionName"] as? JsonPrimitive ?: return null
        if (!nameEl.isString) return null
        val name = nameEl.content.trim()
        if (!versionName.matches(name) || name.length > 32) return null
        val notesEl = obj["notes"] as? JsonPrimitive ?: return null
        if (!notesEl.isString) return null
        val notes = notesEl.content.trim()
        if (notes.isEmpty() || notes.length > MAX_NOTES_CHARS) return null
        return AppUpdateNote(code, name, notes)
    }

    /**
     * A higher versionCode asks. The same code, or an older one, stays quiet.
     * [skippedCode] is the version the person already set aside. Settings passes null and asks anyway.
     */
    fun decide(installedCode: Int, note: AppUpdateNote, skippedCode: Int?): AppUpdateDecision {
        if (note.versionCode > installedCode && skippedCode != note.versionCode) {
            return AppUpdateDecision.Newer(note)
        }
        return AppUpdateDecision.Quiet
    }

    /** An APK is a zip. The first four bytes are the local-file signature. */
    fun apkLooksLikeZip(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }
}

/**
 * How often this phone looks for a newer nightly in the background. [OFF] leaves only the launch
 * check, when that is on, and the Settings button. Missing JSON is hourly.
 */
@Serializable
enum class UpdateCheckEvery(val hours: Int) {
    OFF(0),
    HOURLY(1),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    DAILY(24),
    WEEKLY(168),
    ;

    val periodMs: Long get() = hours * 3_600_000L

    val labelKey: String
        get() = when (this) {
            OFF -> "update.every.off"
            HOURLY -> "update.every.hourly"
            SIX_HOURS -> "update.every.six.hours"
            TWELVE_HOURS -> "update.every.twelve.hours"
            DAILY -> "update.every.daily"
            WEEKLY -> "update.every.weekly"
        }
}

/** An automatic install waits for [IDLE_MS] without hearing a watched radio or signature. */
object AppUpdateIdle {
    const val IDLE_MS = 5 * 60_000L

    /**
     * Milliseconds left before an install may start. Initialize [lastWatchedMs] when the process
     * starts so a restart also observes a full quiet period. Both clocks are elapsed-realtime.
     */
    fun waitMs(lastWatchedMs: Long, nowMs: Long): Long {
        val since = nowMs - lastWatchedMs
        if (since < 0L) return IDLE_MS
        return (IDLE_MS - since).coerceAtLeast(0L)
    }

    /** Names alone and radios marked Mine do not count as watches. Live filters and mute do not apply. */
    fun watched(devices: List<Sighting>, watchlist: List<WatchTarget>): Boolean {
        val radios = RadioBookmarks.alertDeviceKeys(watchlist)
        val fleets = RadioBookmarks.watchedFleetIds(watchlist)
        val mine = RadioBookmarks.mineKeys(watchlist)
        return devices.any { device ->
            device.key !in mine && (device.key in radios || device.fleetIds.any { it in fleets })
        }
    }
}

/**
 * Written just before an automatic install. After Android replaces the app, the new copy reads it
 * once to resume the scan that was running. A marker older than [MAX_AGE_MS] is ignored.
 */
object AppUpdateResume {
    const val NAME = "app-update-resume.txt"
    const val MAX_AGE_MS = 60 * 60_000L

    data class Marker(val scanning: Boolean, val fromVersion: String, val atMs: Long)

    fun encode(marker: Marker): String =
        "scanning=${if (marker.scanning) 1 else 0}\nfrom=${marker.fromVersion.replace('\n', ' ')}\nat=${marker.atMs}\n"

    /** Null when the text is not a marker, or it is too old to trust. */
    fun decode(text: String, nowMs: Long): Marker? {
        val fields = text.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (k, v) -> k.trim() to v.trim() }
        val scanning = when (fields["scanning"]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val at = fields["at"]?.toLongOrNull() ?: return null
        if (at > nowMs || nowMs - at > MAX_AGE_MS) return null
        return Marker(scanning, fields["from"].orEmpty(), at)
    }

    fun write(filesDir: File, marker: Marker) {
        runCatching { File(filesDir, NAME).writeText(encode(marker)) }
    }

    fun clear(filesDir: File) {
        runCatching { File(filesDir, NAME).delete() }
    }

    /** Reads and removes the marker, so a later restart does not resume again. */
    fun take(filesDir: File, nowMs: Long): Marker? {
        val file = File(filesDir, NAME)
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        file.delete()
        return decode(text, nowMs)
    }
}

/** Version the person chose Later for. Not part of exported settings. */
object AppUpdateSkip {
    private const val NAME = "app-update-skip.txt"

    fun read(filesDir: File): Int? {
        val text = runCatching { File(filesDir, NAME).readText() }.getOrNull() ?: return null
        return text.trim().toIntOrNull()?.takeIf { it > 0 }
    }

    fun write(filesDir: File, versionCode: Int) {
        if (versionCode <= 0) return
        runCatching { File(filesDir, NAME).writeText(versionCode.toString()) }
    }
}

internal object AppUpdateLines {
    val all: List<EnglishLine> = listOf(
        en("update.auto", "Automatic updates", LanguageSlot.CONTROL),
        en("update.auto.note", "Downloads a newer nightly and installs it after 5 minutes without hearing a watched radio or signature. Allow installs from this app or set up Shizuku or root first. If Android blocks a silent install, it waits and retries without opening a confirmation prompt. A running scan starts again after the update.", LanguageSlot.BODY),
        en("update.available.body", "Fieldwatch {version} is ready. Open the app to read the notes and update.", LanguageSlot.BODY),
        en("update.available.title", "Update available", LanguageSlot.CONTROL),
        en("update.channel", "Updates", LanguageSlot.CONTROL),
        en("update.confirm.body", "Android needs you to confirm this update. Tap to finish.", LanguageSlot.BODY),
        en("update.done.body", "Now on {version}.", LanguageSlot.BODY),
        en("update.done.resumed", "Now on {version}. Scanning started again.", LanguageSlot.BODY),
        en("update.done.title", "Fieldwatch updated", LanguageSlot.CONTROL),
        en("update.elevated", "Use Shizuku or root when Android asks", LanguageSlot.CONTROL),
        en("update.elevated.note", "If Android wants a tap to confirm, or installs from this app are off, the update installs through Shizuku or root when either is set up. It also makes this app the installer, so later updates need no help.", LanguageSlot.BODY),
        en("update.every", "Check for updates", LanguageSlot.CONTROL),
        en("update.every.daily", "Every day", LanguageSlot.CONTROL),
        en("update.every.hourly", "Every hour", LanguageSlot.CONTROL),
        en("update.every.off", "Off", LanguageSlot.CONTROL),
        en("update.every.note", "Looks for a newer nightly in the background while this phone is online.", LanguageSlot.BODY),
        en("update.launch", "Check when the app opens", LanguageSlot.CONTROL),
        en("update.cellular", "Download updates over mobile data", LanguageSlot.CONTROL),
        en("update.cellular.note", "Off waits for Wi-Fi or another network without a data limit before an automatic update downloads. Update now downloads either way.", LanguageSlot.BODY),
        en("update.topic.auto", "Automatic", LanguageSlot.CONTROL),
        en("update.topic.manual", "Manual", LanguageSlot.CONTROL),
        en("update.topic.debug", "Debug build", LanguageSlot.CONTROL),
        en("update.own.title", "Update ownership", LanguageSlot.CONTROL),
        en("update.own.installer", "Installed by: {name}", LanguageSlot.BODY),
        en("update.own.owner", "Updates owned by: {name}", LanguageSlot.BODY),
        en("update.own.nobody", "Nobody", LanguageSlot.CONTROL),
        en("update.own.button", "Make Fieldwatch its own updater", LanguageSlot.CONTROL),
        en("update.own.already", "Fieldwatch already updates itself", LanguageSlot.CONTROL),
        en("update.own.note", "Installs this copy over itself so Android lets Fieldwatch update without asking. Shizuku or root does it with no tap; otherwise Android asks once. The app restarts, and a running scan starts again.", LanguageSlot.BODY),
        en("update.own.done", "Installed. Fieldwatch restarts now.", LanguageSlot.BODY),
        en("update.own.confirm", "Confirm in Android's prompt to finish.", LanguageSlot.BODY),
        en("update.own.allow", "Allow installs from Fieldwatch, then come back and tap the button again.", LanguageSlot.BODY),
        en("update.own.failed", "Android did not start the install. Nothing changed.", LanguageSlot.BODY),
        en("update.downloading.body", "Fieldwatch {version}", LanguageSlot.BODY),
        en("update.channel.progress", "Update downloads", LanguageSlot.CONTROL),
        en("update.failed.reason", "Android did not install the update: {reason}. The app on this phone is unchanged.", LanguageSlot.BODY),
        en("update.every.six.hours", "Every 6 hours", LanguageSlot.CONTROL),
        en("update.every.twelve.hours", "Every 12 hours", LanguageSlot.CONTROL),
        en("update.every.weekly", "Every week", LanguageSlot.CONTROL),
        en("update.needs.allow.body", "Allow installs from Fieldwatch so updates can install on their own. Tap to open Android settings.", LanguageSlot.BODY),
        en("update.allow.body", "Android asks once. Then come back and tap Update now again.", LanguageSlot.BODY),
        en("update.allow.title", "Allow installs from Fieldwatch", LanguageSlot.CONTROL),
        en("update.checking", "Checking for an update", LanguageSlot.CONTROL),
        en("update.current.body", "Fieldwatch {version} is the newest one on GitHub.", LanguageSlot.BODY),
        en("update.debug.note", "Not available in debug builds. Install the nightly release to get updates here.", LanguageSlot.BODY),
        en("update.current.title", "This phone is current", LanguageSlot.CONTROL),
        en("update.downloading", "Downloading the update", LanguageSlot.CONTROL),
        en("update.failed.body", "The file from GitHub could not be installed. The app on this phone is unchanged.", LanguageSlot.BODY),
        en("update.failed.title", "Could not install the update", LanguageSlot.CONTROL),
        en("update.later", "Later", LanguageSlot.CONTROL),
        en("update.now", "Update now", LanguageSlot.CONTROL),
        en("update.notes", "Release Notes", LanguageSlot.CONTROL),
        en("update.settings", "Check for an update", LanguageSlot.CONTROL),
        en("update.settings.note", "Looks on GitHub when this phone is online. Update now installs without a prompt when Android allows it.", LanguageSlot.BODY),
        en("update.unreachable.body", "GitHub did not answer. The app on this phone is unchanged.", LanguageSlot.BODY),
        en("update.unreachable.title", "Could not check for an update", LanguageSlot.CONTROL),
    )
}
