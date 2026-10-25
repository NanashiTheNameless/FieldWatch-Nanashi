package dev.namelessnanashi.fieldwatch.elevated

import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal interface RootSession : ElevatedShell, Closeable

/** One root shell for an opt-in reader session; bounded output and cancellable command waits. */
internal class PersistentRootShell(
    startProcess: () -> Process = { ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start() },
    private val timeoutMs: Long = 30_000L,
) : RootSession {
    override val label = "Root"
    private val process = startProcess()
    private val closed = AtomicBoolean(false)
    private val chunks = Channel<ByteArray>(16)
    private val mutex = Mutex()
    private val writer = process.outputStream.bufferedWriter()
    private val reader = Thread({
        try {
            process.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (!closed.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (chunks.trySend(buffer.copyOf(count)).isFailure) {
                        throw IOException("Root output buffer exceeded")
                    }
                }
            }
            chunks.close()
        } catch (e: Exception) {
            chunks.close(e)
        }
    }, "FieldwatchRootOutput").apply { isDaemon = true; start() }

    override suspend fun run(command: String): ShellResult = mutex.withLock {
        if (closed.get()) return@withLock ShellResult.failure("Root session is closed")
        val marker = "FIELDWATCH_${UUID.randomUUID()}"
        try {
            withTimeout(timeoutMs) {
                writer.write("(\n$command\n)\nprintf '\\n$marker:%s\\n' \"\$?\"\n")
                writer.flush()
                val output = StringBuilder()
                var result: ShellResult? = null
                while (result == null) {
                    output.append(chunks.receive().toString(Charsets.UTF_8))
                    check(output.length <= MAX_OUTPUT + 8192) { "Root command output exceeded limit" }
                    val boundary = output.indexOf("\n$marker:")
                    if (boundary >= 0) {
                        val end = output.indexOf("\n", boundary + 1)
                        if (end >= 0) {
                            check(boundary <= MAX_OUTPUT) { "Root command output exceeded limit" }
                            val code = output.substring(boundary + marker.length + 2, end).toInt()
                            result = ShellResult(code, output.substring(0, boundary).trimEnd())
                        }
                    } else {
                        check(output.length <= MAX_OUTPUT) { "Root command output exceeded limit" }
                    }
                }
                result
            }
        } catch (_: TimeoutCancellationException) {
            close()
            ShellResult.failure("Root command timed out")
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            ShellResult.failure(e.message ?: "Root session ended")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // EOF also terminates the privileged shell on root implementations that cannot be signalled.
        runCatching { process.outputStream.close() }
        runCatching { process.destroy() }
        runCatching { if (process.isAlive) process.destroyForcibly() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        chunks.close()
        reader.interrupt()
    }

    companion object { private const val MAX_OUTPUT = 64 * 1024 }
}
