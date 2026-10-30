package dev.namelessnanashi.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class MacPrivacyTest {
    @Test fun locallyAssignedPrefixesArePrivateInEveryTextForm() {
        for (mac in listOf("AA:BB:CC:DD:EE:FF", "aabbccddeeff", "02:11:22:33:44:55")) {
            assertEquals("**:**:**:**:**:**", MacUtil.screenMac(mac, true))
            val text = "$mac ${MacUtil.normalize(mac)} ${MacUtil.normalize(mac).replace(":", "")}".lowercase()
            val masked = MacUtil.redactMacIn(text, mac, true)
            assertFalse(masked.contains(mac, true))
            assertFalse(masked.contains(MacUtil.normalize(mac).take(8), true))
        }
    }
    @Test fun universalPrefixesAndOrdinaryModeArePreserved() {
        assertEquals("00:11:22:**:**:**", MacUtil.screenMac("00:11:22:33:44:55", true))
        val raw = "aa:bb:cc:dd:ee:ff"
        assertEquals(raw, MacUtil.screenMac(raw, false))
        assertEquals(raw, MacUtil.redactMacIn(raw, raw, false))
        assertEquals("", MacUtil.screenMac("", true))
    }
}
