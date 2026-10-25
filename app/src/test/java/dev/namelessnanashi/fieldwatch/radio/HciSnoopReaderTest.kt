package dev.namelessnanashi.fieldwatch.radio

import dev.namelessnanashi.fieldwatch.domain.Observation
import dev.namelessnanashi.fieldwatch.elevated.RootSession
import dev.namelessnanashi.fieldwatch.elevated.ShellResult
import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HciSnoopReaderTest {
    private fun capture(): ByteArray {
        val packet = byteArrayOf(4, 0x3e, 17, 2, 1, 0, 0, 6, 5, 4, 3, 2, 1, 5, 4, 9, 84, 97, 103, -60)
        return "btsnoop\u0000".toByteArray() + ByteBuffer.allocate(8).putInt(1).putInt(1002).array() +
            ByteBuffer.allocate(24).putInt(packet.size).putInt(packet.size).putInt(3).putInt(0)
                .putLong(HciSnoopParser.EPOCH_US + 1_000_000L).array() + packet
    }
    private class Stop : CancellationException()
    private class Fake(private val reply: (String) -> ShellResult) : RootSession {
        override val label = "fake"
        var closed = false
        override suspend fun run(command: String) = reply(command)
        override fun close() { closed = true }
    }
    private fun meta(data: ByteArray, inode: Int = 1) = ShellResult(0,
        "$inode:${data.size}\n" + Base64.getEncoder().encodeToString(data.copyOfRange(0, 40)))

    @Test fun interruptedReadReconnectsWithoutLosingOrReplayingPackets() = runBlocking {
        val data = capture()
        val found = mutableListOf<Observation>()
        val sessions = mutableListOf<Fake>()
        var tails = 0
        val delays = mutableListOf<Long>()
        val reader = HciSnoopReader(
            openShell = {
                Fake { command -> when {
                    command == "id -u" -> ShellResult(0, "0")
                    command.startsWith("test ") -> meta(data)
                    else -> {
                        tails++
                        when (tails) {
                            1 -> ShellResult(0, Base64.getEncoder().encodeToString(data.copyOfRange(0, 20)))
                            2 -> ShellResult.failure("connection lost")
                            else -> {
                                assertTrue("Retain the partial-record cursor across reconnects", command.contains("+21"))
                                ShellResult(0, Base64.getEncoder().encodeToString(data.copyOfRange(20, data.size)))
                            }
                        }
                    }
                } }.also { sessions += it }
            },
            waitMs = { ms -> delays += ms; if (found.isNotEmpty()) throw Stop() },
            now = { 0L }, emit = found::add,
        )
        try { reader.read() } catch (_: Stop) {}
        assertEquals(2, sessions.size)
        assertTrue(sessions.all { it.closed })
        assertEquals(listOf(3000L, 1000L, 3000L), delays)
        assertEquals("Tag", found.single().name)
    }

    @Test fun repeatedIoFailuresHaveBoundedExponentialRetries() = runBlocking {
        var sessions = 0
        val waits = mutableListOf<Long>()
        HciSnoopReader(openShell = {
            sessions++
            Fake { if (it == "id -u") ShellResult(0, "0") else ShellResult.failure("I/O") }
        }, waitMs = { waits += it }, now = { 0L }, emit = {}).read()
        assertEquals(4, sessions)
        assertEquals(listOf(1000L, 2000L, 4000L), waits)
        assertTrue(HciSnoopReader.status.value.contains("read failed"))
    }

    @Test fun rootDenialDoesNotRepeatedlyPrompt() = runBlocking {
        var sessions = 0
        HciSnoopReader(openShell = {
            sessions++
            Fake { ShellResult.failure("denied") }
        }, waitMs = { fail("Root denial must not retry") }, emit = {}).read()
        assertEquals(1, sessions)
        assertTrue(HciSnoopReader.status.value.contains("denied"))
    }

    @Test fun corruptCaptureWaitsForRotationThenResumes() = runBlocking {
        var data = ByteArray(40)
        var inode = 1
        var tails = 0
        val found = mutableListOf<Observation>()
        val session = Fake { command -> when {
            command == "id -u" -> ShellResult(0, "0")
            command.startsWith("test ") -> meta(data, inode)
            else -> { tails++; ShellResult(0, Base64.getEncoder().encodeToString(data)) }
        } }
        val reader = HciSnoopReader(openShell = { session }, waitMs = {
            if (found.isNotEmpty()) throw Stop()
            assertTrue(HciSnoopReader.status.value.contains("rotation"))
            data = capture(); inode++
        }, now = { 0L }, emit = found::add)
        try { reader.read() } catch (_: Stop) {}
        assertEquals(2, tails)
        assertEquals("Tag", found.single().name)
        assertTrue(session.closed)
    }
}
