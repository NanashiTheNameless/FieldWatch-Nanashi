package dev.namelessnanashi.fieldwatch.domain

import dev.namelessnanashi.fieldwatch.data.DeviceStore
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LiveDeviceOrderTest {
    @Test fun cachedKeysPreserveEveryExistingSortModeAndTieBreaker() {
        val random = Random(45)
        val store = DeviceStore()
        val devices = (0 until 80).map { n ->
            val mac = "00:11:22:33:44:%02X".format(n)
            val rssi = -30 - random.nextInt(60)
            val row = store.ingest(Observation(
                kind = RadioKind.BLE, mac = mac, name = if (n % 3 == 0) "" else "Tag ${n % 7}",
                rssi = rssi, channel = 0, frequencyMhz = 2402, hiddenSsid = false,
                serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "",
                rawHex = "", extras = "", at = 1000L,
            ), emptyList(), 60)
            row.copy(firstSeen = random.nextInt(5).toLong(), lastSeen = random.nextInt(5).toLong(),
                fleetIds = if (n % 2 == 0) setOf("f") else emptySet(),
                rssiHistory = listOf(RssiSample(500L, rssi - 10), RssiSample(1000L, rssi)))
        }
        val names = mapOf("f" to "Headset")
        val labels = devices.filterIndexed { i, _ -> i % 4 == 0 }.associate { it.key to "Custom ${it.mac.last()}" }
        val alerts = devices.filterIndexed { i, _ -> i % 6 == 0 }.associate { it.key to 20L }
        for (mode in ListSort.entries) for (signal in StrengthSort.entries) for (line in ListLine.entries) {
            val settings = AppSettings(listSort = mode, strengthSort = signal, listTitleLine = line)
            val window = settings.averageWindowSec.coerceIn(10, 180) * 1000L
            // Reference comparator from before the optimization; asserts identical stable ordering.
            val comparator = when (mode) {
                ListSort.STRENGTH -> compareByDescending<Sighting> { it.sortRssi(signal, window, 1000L) }
                ListSort.NEWEST -> compareByDescending<Sighting> { it.lastSeen }.thenByDescending { it.sortRssi(signal, window, 1000L) }
                ListSort.NEWEST_ALERT -> compareByDescending<Sighting> { alerts[it.key] ?: 0L }.thenByDescending { it.lastSeen }
                ListSort.FIRST_SEEN -> compareByDescending<Sighting> { it.firstSeen }.thenByDescending { it.lastSeen }
                ListSort.ARRIVAL -> compareBy<Sighting> { it.firstSeen }.thenBy { it.mac }
                ListSort.NAME -> compareBy<Sighting> {
                    it.listLineText(line, it.fleetIds.map { id -> names[id] ?: id }, labels[it.key]).lowercase()
                }.thenBy { it.mac }
                ListSort.SIGNATURES -> compareByDescending<Sighting> { it.fleetIds.isNotEmpty() }.thenByDescending { it.sortRssi(signal, window, 1000L) }
            }
            assertEquals("$mode / $signal / $line", devices.sortedWith(comparator).map { it.key },
                LiveDeviceOrder.sorted(devices, settings, 1000L, names, labels, alerts).map { it.key })
        }
    }
}
