package dev.namelessnanashi.fieldwatch.alert

import org.junit.Assert.*
import org.junit.Test

class AudioWakeupTest {
    private class Harness {
        var now = 0L
        var played = 0L
        var opens = 0
        var closes = 0
        var available = true
        val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        val wake = AudioWakeup(
            now = { now },
            later = { ms, run -> tasks += (now + ms) to run },
            open = {
                opens++
                if (!available) null else object : AudioWakeup.Playback {
                    override val playedMs: Long get() = played
                    override fun close() { closes++ }
                }
            },
        )
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val task = tasks.minByOrNull { it.first } ?: break
                if (task.first > end) break
                tasks.remove(task)
                now = task.first
                task.second()
            }
            now = end
        }
    }

    @Test fun startupDelayDoesNotConsumeTheSilentLeadIn() {
        val h = Harness()
        var started = false
        h.wake.run(500) { started = true }
        h.advance(600)
        assertFalse("Elapsed wall time does not mean silence reached the audio route", started)
        h.played = 499
        h.advance(25)
        assertFalse(started)
        h.played = 500
        h.advance(25)
        assertTrue(started)
        assertEquals("Keep silence alive through the sound's start", 0, h.closes)
        h.advance(500)
        assertEquals(1, h.closes)
    }

    @Test fun concurrentAlertsBothWaitForTheSameWarmup() {
        val h = Harness()
        val started = mutableListOf<Int>()
        h.wake.run(500) { started += 1 }
        h.advance(100)
        h.wake.run(500) { started += 2 }
        assertTrue(started.isEmpty())
        assertEquals(1, h.opens)
        h.played = 500
        h.advance(25)
        assertEquals(listOf(1, 2), started)
    }

    @Test fun handoffReusesTheWarmRouteAndExtendsItsLifetime() {
        val h = Harness()
        h.wake.run(500) {}
        h.played = 500
        h.advance(25)
        h.advance(400)
        var started = false
        h.wake.run(500) { started = true }
        assertTrue(started)
        assertEquals(1, h.opens)
        h.advance(100)
        assertEquals("Earlier cleanup must not close the newer handoff", 0, h.closes)
        h.advance(400)
        assertEquals(1, h.closes)
    }

    @Test fun stalledAudioHasABoundedWaitAndReleasesItsTrack() {
        val h = Harness()
        var started = false
        h.wake.run(500) { started = true }
        h.advance(2500)
        assertTrue(started)
        h.advance(500)
        assertEquals(1, h.closes)
    }

    @Test fun disabledWarmupOrUnavailableAudioDoesNotLoseTheAlert() {
        val h = Harness()
        var started = 0
        h.wake.run(0) { started++ }
        assertEquals(0, h.opens)
        h.available = false
        h.wake.run(500) { started++ }
        assertEquals(2, started)
        assertTrue(h.tasks.isEmpty())
    }
}
