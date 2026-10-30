package dev.namelessnanashi.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class WatchlistExchangeTest {
    private val row = WatchTarget(id = "local", deviceKey = "BLE:AA:BB:CC:DD:EE:FF", label = "My tag",
        alert = false, mine = true, observerNotes = "Local notes")
    private fun pack(vararg rows: WatchTarget) = WatchlistExchange.parse(WatchlistExchange.encode(rows.toList()))

    @Test fun sharingRoundTripsNamesNotesAndFlagsWithoutSettings() {
        val text = WatchlistExchange.encode(listOf(row))
        assertFalse(text.contains("settings"))
        assertEquals(row, WatchlistExchange.parse(text).watchlist.single())
    }
    @Test fun defaultMergeKeepsConflictingLocalRowsAndAddsNewIdentities() {
        val incoming = row.copy(id = "foreign", label = "Changed", mine = false, alert = true, observerNotes = "Changed")
        val second = row.copy(id = "local", deviceKey = "BLE:00:11:22:33:44:55")
        val cfg = PersistedConfig(watchlist = listOf(row), settings = AppSettings(scanOnBoot = true))
        val merged = WatchlistExchange.merge(cfg, pack(incoming, second, second))
        assertEquals(row, merged.watchlist.first())
        assertEquals(2, merged.watchlist.size)
        assertNotEquals(row.id, merged.watchlist.last().id)
        assertEquals(1, merged.added)
        assertEquals(1, merged.conflicts)
        assertEquals(cfg.copy(watchlist = merged.watchlist).settings, cfg.settings)
    }
    @Test fun explicitReplacementKeepsLocalIdsAndUsesIncomingFlagsAndNotes() {
        val incoming = row.copy(id = "foreign", mine = false, alert = true, observerNotes = "Shared")
        val merged = WatchlistExchange.merge(PersistedConfig(watchlist = listOf(row)), pack(incoming), true)
        assertEquals(incoming.copy(id = row.id), merged.watchlist.single())
    }
    @Test fun normalizesAddressesAndDeduplicatesIdentityRatherThanForeignIds() {
        val compact = row.copy(id = "one", deviceKey = "BLE:aabbccddeeff")
        assertEquals(listOf(compact.copy(deviceKey = row.deviceKey)), pack(compact, row).watchlist)
    }
    @Test fun missingSignaturesAreSkippedAndInstalledSignaturesImport() {
        val watch = WatchTarget("id", fleetId = "known", label = "Known")
        val cfg = PersistedConfig(fleets = listOf(Fleet("known", "Known")))
        val merged = WatchlistExchange.merge(cfg, pack(watch, watch.copy(fleetId = "missing")))
        assertEquals(1, merged.added)
        assertEquals(1, merged.skipped)
    }
    @Test fun badPacksAreRejectedBeforeAnyMerge() {
        val valid = WatchlistExchange.encode(listOf(row))
        for (text in listOf("{}", "{", valid.replace("fieldwatch-watchlist", "fieldwatch-settings"),
                valid.replace("\"formatVersion\": 1", "\"formatVersion\": 2"),
                valid.replace("BLE:AA:BB:CC:DD:EE:FF", "BLE:not-a-mac"))) {
            try { WatchlistExchange.parse(text); fail("accepted invalid pack") } catch (_: IllegalArgumentException) { }
        }
        try { pack(row.copy(fleetId = "also-set")); fail("accepted ambiguous entry") } catch (_: IllegalArgumentException) { }
    }
}
