package dev.namelessnanashi.fieldwatch.elevated

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** One command's exit code and combined stdout/stderr. */
data class ShellResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0

    /** Wire form for the Shizuku binder: exit code, newline, output. */
    fun encode(): String = "$code\n$output"

    companion object {
        fun failure(message: String) = ShellResult(-1, message)

        fun decode(wire: String): ShellResult {
            val nl = wire.indexOf('\n')
            val head = if (nl < 0) wire else wire.substring(0, nl)
            val code = head.trim().toIntOrNull() ?: return failure(wire)
            return ShellResult(code, if (nl < 0) "" else wire.substring(nl + 1))
        }
    }
}

/** A shell that runs as root or as the adb shell user. */
interface ElevatedShell {
    val label: String

    suspend fun run(command: String): ShellResult
}

private const val MAX_OUTPUT_BYTES = 64 * 1024

/**
 * Runs [argv], collecting at most 64 KiB of combined output. A process still running
 * after [timeoutMs] is killed and reported as a failure.
 */
internal fun runProcess(argv: List<String>, timeoutMs: Long): ShellResult {
    val process = try {
        ProcessBuilder(argv).redirectErrorStream(true).start()
    } catch (e: Exception) {
        return ShellResult.failure(e.message ?: "could not start ${argv.first()}")
    }
    val out = ByteArrayOutputStream()
    val reader = Thread {
        runCatching {
            process.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(out) {
                        if (out.size() < MAX_OUTPUT_BYTES) {
                            out.write(buf, 0, minOf(n, MAX_OUTPUT_BYTES - out.size()))
                        }
                    }
                }
            }
        }
    }.apply {
        isDaemon = true
        start()
    }
    process.outputStream.close()
    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        return ShellResult.failure("${argv.first()} timed out after ${timeoutMs / 1000} s")
    }
    reader.join(1_000)
    val text = synchronized(out) { out.toString(Charsets.UTF_8.name()) }
    return ShellResult(process.exitValue(), text.trimEnd())
}
