package dev.namelessnanashi.fieldwatch.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val notes =
        "A language file that comes with the app says it was written for this version. " +
            "An imported file reports the keys added after the version it was written for. " +
            "A translated line from an older file still shows."

    private val note = AppUpdateNote(50, "1.1.40", "A newer build.")

    @Test
    fun aCompleteFileParses() {
        val parsed = AppUpdate.parse(
            """{"versionCode":49,"versionName":"1.1.39","notes":"$notes","extra":true}""",
        )
        assertEquals(AppUpdateNote(49, "1.1.39", notes), parsed)
    }

    @Test
    fun updatesAreOnHourlyAndAtLaunchByDefault() {
        val defaults = AppSettings()
        assertTrue(defaults.updateAutoInstall)
        assertTrue(defaults.updateCheckOnLaunch)
        assertTrue(defaults.updateElevatedInstall)
        assertEquals(UpdateCheckEvery.HOURLY, defaults.updateCheckEvery)
        assertEquals(3_600_000L, UpdateCheckEvery.HOURLY.periodMs)
        assertEquals(0L, UpdateCheckEvery.OFF.periodMs)
        for (every in UpdateCheckEvery.entries) {
            assertTrue(EnglishCopy.byKey.containsKey(every.labelKey))
        }
    }

    @Test
    fun anInstallWaitsForFiveQuietMinutes() {
        val idle = AppUpdateIdle.IDLE_MS
        assertEquals(0L, AppUpdateIdle.waitMs(lastTouchMs = 0L, nowMs = 1_000L))
        assertEquals(idle, AppUpdateIdle.waitMs(lastTouchMs = 10_000L, nowMs = 10_000L))
        assertEquals(idle - 60_000L, AppUpdateIdle.waitMs(lastTouchMs = 10_000L, nowMs = 70_000L))
        assertEquals(0L, AppUpdateIdle.waitMs(lastTouchMs = 10_000L, nowMs = 10_000L + idle))
        assertEquals(idle, AppUpdateIdle.waitMs(lastTouchMs = 10_000L, nowMs = 5_000L))
    }

    @Test
    fun theResumeMarkerIsReadOnceAndOnlyWhileFresh() {
        val marker = AppUpdateResume.Marker(scanning = true, fromVersion = "1.2.0-abc1234", atMs = 1_000_000L)
        val text = AppUpdateResume.encode(marker)
        assertEquals(marker, AppUpdateResume.decode(text, nowMs = 1_000_000L + 60_000L))
        assertNull(AppUpdateResume.decode(text, nowMs = 1_000_000L + AppUpdateResume.MAX_AGE_MS + 1))
        assertNull(AppUpdateResume.decode(text, nowMs = 999_999L))
        assertNull(AppUpdateResume.decode("scanning=maybe\nat=1", nowMs = 2L))
        val dir = kotlin.io.path.createTempDirectory("resume").toFile()
        try {
            AppUpdateResume.write(dir, marker)
            assertEquals(marker, AppUpdateResume.take(dir, nowMs = 1_000_500L))
            assertNull(AppUpdateResume.take(dir, nowMs = 1_000_500L))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aNightlyNameWithACommitSuffixIsRead() {
        val text = """{"versionCode":26784512,"versionName":"1.2.0-abc1234","notes":"- A commit."}"""
        assertEquals(AppUpdateNote(26784512, "1.2.0-abc1234", "- A commit."), AppUpdate.parse(text))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.2.0-","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.2.0-a b","notes":"Hi"}"""))
    }

    @Test
    fun aFileThatIsNotAReleaseIsRefused() {
        assertNull(AppUpdate.parse(""))
        assertNull(AppUpdate.parse("{"))
        assertNull(AppUpdate.parse("[]"))
        assertNull(AppUpdate.parse("""{"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":"49","versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49.5,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":0,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":-1,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"v1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":"  "}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":""}"""))
        val huge = "x".repeat(AppUpdate.MAX_NOTES_CHARS + 1)
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":"$huge"}"""))
        assertNull(AppUpdate.parse("{" + " ".repeat(AppUpdate.MAX_NOTE_BYTES) + "}"))
    }

    @Test
    fun aNewerVersionAsksUntilItIsSetAside() {
        assertEquals(AppUpdateDecision.Newer(note), AppUpdate.decide(49, note, skippedCode = null))
        assertEquals(AppUpdateDecision.Newer(note), AppUpdate.decide(49, note, skippedCode = 49))
        assertTrue(AppUpdate.decide(50, note, skippedCode = null) is AppUpdateDecision.Quiet)
        assertTrue(AppUpdate.decide(51, note, skippedCode = null) is AppUpdateDecision.Quiet)
        assertTrue(AppUpdate.decide(49, note, skippedCode = 50) is AppUpdateDecision.Quiet)
    }

    @Test
    fun theSkipFileRemembersOneVersionCode() {
        val dir = java.nio.file.Files.createTempDirectory("fw-update").toFile()
        assertNull(AppUpdateSkip.read(dir))
        AppUpdateSkip.write(dir, 50)
        assertEquals(50, AppUpdateSkip.read(dir))
        File(dir, "app-update-skip.txt").writeText("nope")
        assertNull(AppUpdateSkip.read(dir))
        AppUpdateSkip.write(dir, 0)
        assertNull(AppUpdateSkip.read(dir))
    }

    @Test
    fun anApkStartsWithAZipLocalHeader() {
        assertTrue(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0)))
        assertFalse(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B)))
    }

    @Test
    fun updateLinesBelongToTheNextRelease() {
        val keys = listOf(
            "update.allow.body",
            "update.allow.title",
            "update.auto",
            "update.auto.note",
            "update.available.body",
            "update.available.title",
            "update.channel",
            "update.checking",
            "update.confirm.body",
            "update.current.body",
            "update.current.title",
            "update.debug.note",
            "update.done.body",
            "update.done.resumed",
            "update.done.title",
            "update.downloading",
            "update.elevated",
            "update.elevated.note",
            "update.every",
            "update.every.daily",
            "update.every.hourly",
            "update.every.note",
            "update.every.off",
            "update.every.six.hours",
            "update.every.twelve.hours",
            "update.every.weekly",
            "update.failed.body",
            "update.failed.title",
            "update.later",
            "update.launch",
            "update.needs.allow.body",
            "update.now",
            "update.notes",
            "update.settings",
            "update.settings.note",
            "update.unreachable.body",
            "update.unreachable.title",
        )
        assertEquals(keys.size, AppUpdateLines.all.size)
        for (key in keys) {
            val line = EnglishCopy.byKey.getValue(key)
            assertEquals(key, EnglishCopy.ADDED_IN, line.addedIn)
            assertTrue(line.text.isNotBlank())
        }
        assertEquals("Update now", EnglishCopy.text("update.now"))
        assertEquals("Release Notes", EnglishCopy.text("update.notes"))
        assertEquals("Later", EnglishCopy.text("update.later"))
        assertTrue(EnglishCopy.text("update.current.body").contains("{version}"))
    }
}
