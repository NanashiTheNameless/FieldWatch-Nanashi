package dev.namelessnanashi.fieldwatch.elevated

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** su -c, as provided by Magisk, KernelSU, or APatch. The root manager prompts the first time. */
object RootShell : ElevatedShell {
    override val label = "Root"

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        // Long enough for the root manager's grant prompt.
        runProcess(listOf("su", "-c", command), timeoutMs = 30_000)
    }

    /** True when su runs and reports uid 0. */
    suspend fun check(): Boolean = run("id -u").let { it.ok && it.output.trim() == "0" }
}
