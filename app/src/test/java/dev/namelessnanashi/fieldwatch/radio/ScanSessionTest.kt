package dev.namelessnanashi.fieldwatch.radio

import org.junit.Assert.*
import org.junit.Test

class ScanSessionTest {
    @Test fun manualStopBlocksAutomaticResumeUntilExplicitStart() {
        val session = ScanSession()
        assertTrue(session.requestStart(false))
        assertEquals(ScanRunState.STARTING, session.state.value)
        assertTrue(session.started())
        session.requestStop()
        assertEquals(ScanRunState.STOPPING, session.state.value)
        assertFalse(session.requestStart(false))
        assertFalse(session.requestStart(true))
        session.stopped()
        assertFalse(session.requestStart(false))
        assertTrue(session.requestStart(true))
        assertFalse(session.stoppedByUser)
        assertTrue(session.started())
    }

    @Test fun persistedStopAlsoBlocksBootAndProcessRecreation() {
        val session = ScanSession()
        session.load(true)
        assertFalse(session.requestStart(false))
        assertFalse(session.started())
        assertTrue(session.requestStart(true))
    }

    @Test fun cancellingStartupCannotBeUndoneByTheLoadedPreferenceOrLateServiceCallback() {
        val session = ScanSession()
        assertTrue(session.requestStart(false))
        session.requestStop()
        session.load(false)
        assertTrue(session.stoppedByUser)
        assertFalse(session.started())
        session.stopped()
        assertFalse(session.requestStart(false))
    }

    @Test fun duplicateStartsAreIgnoredAndOrdinaryServiceStopsCanResume() {
        val session = ScanSession()
        assertTrue(session.requestStart(false))
        assertFalse(session.requestStart(false))
        assertTrue(session.started())
        session.stopped()
        assertTrue(session.requestStart(false))
    }
}
