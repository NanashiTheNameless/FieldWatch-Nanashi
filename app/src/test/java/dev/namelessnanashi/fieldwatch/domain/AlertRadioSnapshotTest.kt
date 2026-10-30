package dev.namelessnanashi.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class AlertRadioSnapshotTest {
    private val radio = SitRadio(
        key = "BLE:00:11:22:33:44:55", kind = RadioKind.BLE,
        mac = "00:11:22:33:44:55", name = "Saved alert radio",
        firstSeen = 1_000L, lastSeen = 2_000L, hitCount = 3,
        rssi = -55, rssiMin = -70, rssiMax = -40,
    ).toSighting()

    @Test
    fun restoresTheAlertedIdentityAsLastHeardWithoutALiveEntry() {
        val saved = AlertRadioSnapshot.decode(AlertRadioSnapshot.encode(radio), radio.key)!!
        assertEquals(radio.key, saved.key)
        assertEquals(radio.name, saved.name)
        assertEquals(radio.kind, saved.kind)
        assertEquals(radio.mac, saved.mac)
        assertEquals(radio.rssi, saved.rssi)
        assertEquals(radio.lastSeen, saved.lastSeen)
        assertTrue(saved.gone)
    }

    @Test
    fun omitsLargePayloadsAndPathsAndBoundsNames() {
        val big = radio.copy(name = "x".repeat(20_000), rawHex = "AA".repeat(100_000),
            gpsTrail = List(1200) { GpsSample(it.toLong(), 36.2, -115.08, -55) })
        val encoded = AlertRadioSnapshot.encode(big)!!
        assertTrue(encoded.length < 2_000)
        val saved = AlertRadioSnapshot.decode(encoded, radio.key)!!
        assertEquals(512, saved.name.length)
        assertTrue(saved.rawHex.isEmpty())
        assertTrue(saved.gpsTrail.isEmpty())
    }

    @Test
    fun ignoresMissingMalformedOversizedAndMismatchedSnapshots() {
        assertNull(AlertRadioSnapshot.decode(null, radio.key))
        assertNull(AlertRadioSnapshot.decode("{", radio.key))
        assertNull(AlertRadioSnapshot.decode("x".repeat(16_385), radio.key))
        assertNull(AlertRadioSnapshot.decode(AlertRadioSnapshot.encode(radio), "BLE:00:11:22:33:44:66"))
        val invalid = radio.copy(key = "WIFI:00:11:22:33:44:55")
        assertNull(AlertRadioSnapshot.decode(AlertRadioSnapshot.encode(invalid), invalid.key))
    }
}
