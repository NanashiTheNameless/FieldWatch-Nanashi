package dev.namelessnanashi.fieldwatch.domain

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WatchlistPack(
    val format: String,
    val formatVersion: Int = 1,
    val watchlist: List<WatchTarget> = emptyList(),
) {
    companion object { const val FORMAT = "fieldwatch-watchlist" }
}

object WatchlistExchange {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    data class Merge(val watchlist: List<WatchTarget>, val added: Int, val conflicts: Int, val skipped: Int,
        val replace: Boolean) {
        fun summary() = "$added new entries. $conflicts existing entries ${if (replace) "replaced" else "kept"}. " +
            "$skipped signature watches skipped because their signatures are not installed."
    }
    private fun identity(row: WatchTarget): String = row.deviceKey?.let { "radio:$it" } ?: "signature:${row.fleetId}"

    private fun normalize(row: WatchTarget): WatchTarget {
        val device = row.deviceKey?.trim()?.takeIf { it.isNotEmpty() }
        val fleet = row.fleetId?.trim()?.takeIf { it.isNotEmpty() }
        require((device != null) != (fleet != null)) { "Each entry must select one radio or signature." }
        val key = device?.let {
            val kind = it.substringBefore(':')
            require(RadioKind.entries.any { radio -> radio.name == kind }) { "Unknown radio kind." }
            val mac = MacUtil.normalize(it.substringAfter(':', ""))
            require(mac.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))) { "Invalid radio address." }
            "$kind:$mac"
        }
        require(fleet == null || fleet.length <= 128) { "Invalid signature identifier." }
        return row.copy(deviceKey = key, fleetId = fleet, label = row.label.trim().take(if (key != null) RadioBookmarks.MAX_NAME else 128),
            observerNotes = RadioBookmarks.clipNotes(row.observerNotes))
    }
    fun encode(watchlist: List<WatchTarget>): String = json.encodeToString(WatchlistPack.serializer(),
        WatchlistPack(format = WatchlistPack.FORMAT, watchlist = watchlist.map(::normalize).distinctBy(::identity)))

    fun parse(text: String): WatchlistPack {
        val pack = try { json.decodeFromString(WatchlistPack.serializer(), text.trim().trimStart('\uFEFF')) }
        catch (e: IllegalArgumentException) { throw IllegalArgumentException("Choose a Fieldwatch watchlist export.", e) }
        require(pack.format == WatchlistPack.FORMAT && pack.formatVersion == 1) { "Unsupported watchlist file or version." }
        require(pack.watchlist.size <= 10_000) { "This watchlist is too large." }
        return pack.copy(watchlist = pack.watchlist.map(::normalize).distinctBy(::identity))
    }

    /** Default conflicts keep the complete local row, including Mine, notes and alert preferences. */
    fun merge(local: PersistedConfig, pack: WatchlistPack, replace: Boolean = false): Merge {
        val rows = local.watchlist.toMutableList()
        val indices = rows.withIndex().associate { identity(normalize(it.value)) to it.index }.toMutableMap()
        val signatures = local.fleets.map { it.id }.toSet()
        var added = 0; var conflicts = 0; var skipped = 0
        for (row in pack.watchlist.map(::normalize).distinctBy(::identity)) {
            val index = indices[identity(row)]
            if (index != null) {
                conflicts++
                if (replace) rows[index] = row.copy(id = rows[index].id)
            } else if (row.fleetId != null && row.fleetId !in signatures) {
                skipped++
            } else {
                indices[identity(row)] = rows.size
                rows.add(row.copy(id = UUID.randomUUID().toString()))
                added++
            }
        }
        return Merge(rows, added, conflicts, skipped, replace)
    }
}
