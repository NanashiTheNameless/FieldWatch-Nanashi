package dev.namelessnanashi.fieldwatch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AppStartupTest {
    @Test fun callersShareOneLoadAndWaitForAllData() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val loader = AppStartup(this, { loads++; release.await() }, { throw AssertionError(it) })
        loader.start()
        loader.start()
        val activity = async { loader.awaitReady() }
        val service = async { loader.awaitReady() }
        yield()
        assertEquals(1, loads)
        assertEquals(StartupState.LOADING, loader.state.value)
        assertFalse(activity.isCompleted)
        assertFalse(service.isCompleted)
        release.complete(Unit)
        assertTrue(activity.await())
        assertTrue(service.await())
        assertEquals(StartupState.READY, loader.state.value)
        loader.start()
        yield()
        assertEquals(1, loads)
    }

    @Test fun failedLoadUnblocksWaitersAndCanBeRetried() = runBlocking {
        var attempts = 0
        var failures = 0
        val loader = AppStartup(this, {
            if (++attempts == 1) error("unreadable store")
        }, { failures++ })
        loader.start()
        assertFalse(loader.awaitReady())
        assertEquals(StartupState.FAILED, loader.state.value)
        loader.start()
        assertTrue(loader.awaitReady())
        assertEquals(2, attempts)
        assertEquals(1, failures)
    }

    @Test fun leavingAnActivityDoesNotCancelSharedStartup() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val loader = AppStartup(this, { release.await() }, { throw AssertionError(it) })
        loader.start()
        val activity = async { loader.awaitReady() }
        yield()
        activity.cancel()
        release.complete(Unit)
        assertTrue(loader.awaitReady())
    }
}
