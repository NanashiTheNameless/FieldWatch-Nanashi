package dev.namelessnanashi.fieldwatch.domain

/** Expensive display names and rolling RSSI averages are evaluated once per device, not per comparison. */
object LiveDeviceOrder {
    private data class Row(val device: Sighting, val signal: Double, val name: String)

    fun sorted(
        devices: List<Sighting>, settings: AppSettings, now: Long,
        fleetNames: Map<String, String>, watchNames: Map<String, String>, alerts: Map<String, Long>,
    ): List<Sighting> {
        val mode = settings.listSort
        val needSignal = mode == ListSort.STRENGTH || mode == ListSort.NEWEST || mode == ListSort.SIGNATURES
        val window = settings.averageWindowSec.coerceIn(10, 180) * 1000L
        val rows = devices.map { device ->
            Row(
                device,
                if (needSignal) device.sortRssi(settings.strengthSort, window, now) else 0.0,
                if (mode == ListSort.NAME) device.listLineText(
                    settings.listTitleLine, device.fleetIds.map { fleetNames[it] ?: it }, watchNames[device.key],
                ).lowercase() else "",
            )
        }
        val order = when (mode) {
            ListSort.STRENGTH -> compareByDescending<Row> { it.signal }
            ListSort.NEWEST -> compareByDescending<Row> { it.device.lastSeen }.thenByDescending { it.signal }
            ListSort.NEWEST_ALERT -> compareByDescending<Row> { alerts[it.device.key] ?: 0L }
                .thenByDescending { it.device.lastSeen }
            ListSort.FIRST_SEEN -> compareByDescending<Row> { it.device.firstSeen }.thenByDescending { it.device.lastSeen }
            ListSort.ARRIVAL -> compareBy<Row> { it.device.firstSeen }.thenBy { it.device.mac }
            ListSort.NAME -> compareBy<Row> { it.name }.thenBy { it.device.mac }
            ListSort.SIGNATURES -> compareByDescending<Row> { it.device.fleetIds.isNotEmpty() }.thenByDescending { it.signal }
        }
        return rows.sortedWith(order).map { it.device }
    }
}
