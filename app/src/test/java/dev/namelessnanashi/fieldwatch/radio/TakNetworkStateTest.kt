package dev.namelessnanashi.fieldwatch.radio
import dev.namelessnanashi.fieldwatch.domain.TakBirth
import org.junit.Assert.*
import org.junit.Test

class TakNetworkStateTest {
    @Test fun localLanArrivalAndLossPreserveOtherLinks() {
        val state = TakNetworkState<String>()
        assertTrue(state.update("cell", TakNetworkState.Link(false, false, true)))
        assertEquals(TakBirth.CELLULAR, state.facts().birth)
        assertFalse(state.facts().lan)
        assertTrue(state.update("local-wifi", TakNetworkState.Link(true, false, false)))
        assertEquals(TakBirth.WIFI, state.facts().birth)
        assertTrue(state.facts().lan)
        assertTrue(state.lost("local-wifi"))
        assertEquals(TakBirth.CELLULAR, state.facts().birth)
        assertTrue(state.facts().online)
        state.lost("cell")
        assertFalse(state.facts().online)
    }
    @Test fun duplicateCapabilitiesDoNotReconnectButReplacementLinksDo() {
        val state = TakNetworkState<String>()
        val link = TakNetworkState.Link(true, false, false)
        assertTrue(state.update("wifi-a", link))
        assertFalse(state.update("wifi-a", link))
        assertTrue(state.update("wifi-b", link))
        state.lost("wifi-a")
        assertTrue(state.facts().lan)
        state.clear()
        assertFalse(state.facts().online)
        assertTrue(state.update("wifi-b", link))
    }
    @Test fun vpnCountsAsOnlineAndEthernetAsLan() {
        val state = TakNetworkState<String>()
        state.update("vpn", TakNetworkState.Link(false, false, false, true))
        assertTrue(state.facts().online)
        assertFalse(state.facts().lan)
        state.update("ethernet", TakNetworkState.Link(false, true, false))
        assertEquals(TakBirth.ETHERNET, state.facts().birth)
    }
}
