package dev.namelessnanashi.fieldwatch.radio

import dev.namelessnanashi.fieldwatch.domain.Observation
import dev.namelessnanashi.fieldwatch.elevated.ElevatedItems
import dev.namelessnanashi.fieldwatch.elevated.PersistentRootShell
import dev.namelessnanashi.fieldwatch.elevated.RootSession
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/** Bounded, read-only capture access. Transient I/O retries retain the parser and file cursor. */
internal class HciSnoopReader(
    private val openShell: () -> RootSession = { PersistentRootShell() },
    private val waitMs: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val emit: (Observation) -> Unit,
) {
    suspend fun read() {
        val since = now()
        var identity = ""
        var offset = 0L
        var previousSize = 0L
        var hits = 0L
        fun newParser() = HciSnoopParser(since) { hits++; emit(it) }
        var parser = newParser()
        var blockedIdentity: String? = null
        var failures = 0
        var authorized = false
        while (currentCoroutineContext().isActive) {
            try {
                openShell().use { shell ->
                    val uid = shell.run("id -u")
                    if (!uid.ok || uid.output.trim() != "0") {
                        state.value = "HCI reader: root unavailable or denied. Check root access, then switch the reader off and on."
                        return
                    }
                    authorized = true
                    while (currentCoroutineContext().isActive) {
                        val path = ElevatedItems.SNOOP_LOG_PATH
                        val meta = shell.run(
                            "test \"\$(getprop ${ElevatedItems.SNOOP_MODE_PROP})\" = full && " +
                                "stat -c '%i:%s' $path && head -c 40 $path | base64",
                        )
                        if (meta.code == -1) throw IOException("Root session unavailable")
                        val lines = meta.output.lines()
                        val stat = lines.firstOrNull()?.split(':').orEmpty()
                        val size = stat.getOrNull(1)?.toLongOrNull()
                        if (!meta.ok || size == null || size < 40) {
                            state.value = "HCI reader: waiting for snoop logging and a readable capture."
                            waitMs(15_000)
                            continue
                        }
                        val id = stat[0] + ":" + lines.drop(1).joinToString("")
                        if (id != identity || size < previousSize) {
                            identity = id
                            offset = 0L
                            parser = newParser()
                            blockedIdentity = null
                        }
                        previousSize = size
                        if (blockedIdentity == id) {
                            state.value = "HCI reader: damaged or unsupported capture; waiting for log rotation."
                            waitMs(15_000)
                            continue
                        }
                        if (size > offset) {
                            val result = shell.run("tail -c +${offset + 1} $path | head -c 32768 | base64")
                            if (!result.ok) throw IOException("Capture read interrupted")
                            val chunk = try {
                                Base64.getDecoder().decode(result.output.filterNot { it.isWhitespace() })
                            } catch (_: IllegalArgumentException) {
                                throw IOException("Incomplete capture read")
                            }
                            if (chunk.size > 32768) throw IOException("Oversized capture read")
                            try {
                                if (chunk.isNotEmpty()) parser.feed(chunk)
                            } catch (_: IllegalArgumentException) {
                                // Do not repeatedly replay earlier packets from a corrupt file.
                                blockedIdentity = id
                                continue
                            }
                            offset += chunk.size
                            failures = 0
                            state.value = "HCI reader: $hits discovery reports · $offset / $size bytes"
                            if (chunk.size == 32768) { waitMs(100); continue }
                        } else {
                            failures = 0
                            state.value = "HCI reader: $hits discovery reports · waiting for packets"
                        }
                        waitMs(3_000)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!authorized || failures >= MAX_RETRIES) {
                    state.value = "HCI reader: read failed. Check root access and the capture; switch the reader off and on to retry."
                    return
                }
                val backoff = 1_000L shl failures
                failures++
                state.value = "HCI reader: read interrupted; retrying ($failures/$MAX_RETRIES)."
                waitMs(backoff)
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private val state = MutableStateFlow("HCI reader off")
        val status = state.asStateFlow()
        fun stopped() { state.value = "HCI reader off" }
    }
}
