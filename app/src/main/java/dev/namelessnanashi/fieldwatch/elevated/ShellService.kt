package dev.namelessnanashi.fieldwatch.elevated

import kotlin.system.exitProcess

/**
 * Shizuku user service. Shizuku starts it in its own process as the adb shell user, so
 * the commands it runs have shell privileges (pm grant, settings, cmd appops, ...).
 */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String =
        runProcess(listOf("sh", "-c", command), timeoutMs = 60_000).encode()
}
