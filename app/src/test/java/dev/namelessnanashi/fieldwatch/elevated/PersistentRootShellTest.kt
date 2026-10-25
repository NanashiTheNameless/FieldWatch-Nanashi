package dev.namelessnanashi.fieldwatch.elevated

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PersistentRootShellTest {
    @Test fun reusesOneProcessAndFramesCommandsWithoutTrailingNewlines() = runBlocking {
        var starts = 0
        PersistentRootShell(startProcess = { starts++; ProcessBuilder("sh").redirectErrorStream(true).start() }).use { shell ->
            assertEquals(ShellResult(0, "first"), shell.run("printf first"))
            assertEquals(ShellResult(7, "second"), shell.run("printf second; exit 7"))
            assertEquals(ShellResult(0, "third"), shell.run("printf third"))
            assertEquals(1, starts)
        }
    }

    @Test fun concurrentCommandsAreSerializedWithoutMixingOutput() = runBlocking {
        PersistentRootShell(startProcess = { ProcessBuilder("sh").redirectErrorStream(true).start() }).use { shell ->
            val first = async { shell.run("printf alpha") }
            val second = async { shell.run("printf beta") }
            assertEquals("alpha", first.await().output)
            assertEquals("beta", second.await().output)
        }
    }

    @Test fun excessiveOutputClosesTheSessionInsteadOfGrowingMemory() = runBlocking {
        PersistentRootShell(startProcess = { ProcessBuilder("sh").redirectErrorStream(true).start() }).use { shell ->
            assertFalse(shell.run("head -c 100000 /dev/zero | tr '\\000' x").ok)
            assertFalse(shell.run("printf should-not-run").ok)
        }
    }

    @Test fun timeoutAndCancellationReleaseTheProcess() = runBlocking {
        val process = ProcessBuilder("sh").redirectErrorStream(true).start()
        PersistentRootShell(startProcess = { process }, timeoutMs = 100).use { shell ->
            assertFalse(shell.run("sleep 1").ok)
            assertFalse(shell.run("printf should-not-run").ok)
        }
        process.waitFor()
        assertFalse(process.isAlive)
        val other = ProcessBuilder("sh").redirectErrorStream(true).start()
        PersistentRootShell(startProcess = { other }).use { shell ->
            val command = async { shell.run("sleep 1") }
            yield()
            command.cancel()
            command.join()
        }
        other.waitFor()
        assertFalse(other.isAlive)
    }
}
