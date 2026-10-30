package dev.namelessnanashi.fieldwatch.domain

import kotlinx.serialization.json.Json

/** A bounded identity/signal record for a notification whose live radio may be gone. */
object AlertRadioSnapshot {
    private const val MAX_CHARS = 16_384
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(device: Sighting): String? {
        // Notification intents stay small: no advertisement bytes, paths or decoded payloads.
        val radio = SitRadio(
            key = device.key,
            kind = device.kind,
            mac = device.mac,
            name = device.name.take(512),
            fleetIds = device.fleetIds.take(16).map { it.take(128) }.toSet(),
            firstSeen = device.firstSeen,
            lastSeen = device.lastSeen,
            hitCount = device.hitCount,
            rssi = device.rssi,
            rssiMin = device.rssiMin,
            rssiMax = device.rssiMax,
            channel = device.channel,
            frequencyMhz = device.frequencyMhz,
            randomized = device.randomized,
            hiddenSsid = device.hiddenSsid,
            gone = true,
        )
        return json.encodeToString(SitRadio.serializer(), radio).takeIf { it.length <= MAX_CHARS }
    }

    fun decode(text: String?, key: String): Sighting? {
        if (text.isNullOrBlank() || text.length > MAX_CHARS) return null
        return runCatching {
            val radio = json.decodeFromString(SitRadio.serializer(), text)
            if (radio.key != key || key != "${radio.kind}:${MacUtil.normalize(radio.mac)}") return null
            radio.toSighting().copy(gone = true)
        }.getOrNull()
    }
}
