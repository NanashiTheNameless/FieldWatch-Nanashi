package dev.namelessnanashi.fieldwatch.alert

/** Serializes route warm-up on the audio thread. Timers alone do not prove audio has played. */
internal class AudioWakeup(
    private val now: () -> Long,
    private val later: (Long, () -> Unit) -> Unit,
    private val open: () -> Playback?,
) {
    interface Playback {
        val playedMs: Long
        fun close()
    }

    private class Session(val playback: Playback, val duration: Int, val deadline: Long) {
        val waiting = mutableListOf<() -> Unit>()
        var ready = false
        var releaseVersion = 0
    }

    private var active: Session? = null

    fun run(durationMs: Int, action: () -> Unit) {
        if (durationMs <= 0) { action(); return }
        val current = active
        if (current != null) {
            if (current.ready) {
                try { action() } finally { releaseLater(current) }
            } else {
                current.waiting += action
            }
            return
        }
        val playback = open()
        if (playback == null) { action(); return }
        val session = Session(playback, durationMs, now() + durationMs + START_TIMEOUT_MS)
        active = session
        session.waiting += action
        poll(session)
    }

    private fun poll(session: Session) {
        if (active !== session) return
        val played = runCatching { session.playback.playedMs }.getOrDefault(0L)
        if (played < session.duration && now() < session.deadline) {
            later(25) { poll(session) }
            return
        }
        session.ready = true
        val waiting = session.waiting.toList()
        session.waiting.clear()
        try { waiting.forEach { it() } } finally { releaseLater(session) }
    }

    private fun releaseLater(session: Session) {
        val version = ++session.releaseVersion
        // Overlap the real sound's startup; releasing silence before start() reopens the gap.
        later(HANDOFF_MS) {
            if (active === session && session.releaseVersion == version) {
                active = null
                runCatching { session.playback.close() }
            }
        }
    }

    companion object {
        private const val START_TIMEOUT_MS = 2_000L
        private const val HANDOFF_MS = 500L
    }
}
