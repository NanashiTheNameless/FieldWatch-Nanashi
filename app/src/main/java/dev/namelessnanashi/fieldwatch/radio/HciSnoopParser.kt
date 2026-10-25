package dev.namelessnanashi.fieldwatch.radio

import dev.namelessnanashi.fieldwatch.domain.Observation
import dev.namelessnanashi.fieldwatch.domain.RadioFacts
import dev.namelessnanashi.fieldwatch.domain.RadioKind
import dev.namelessnanashi.fieldwatch.domain.toHexUpper
import java.nio.ByteBuffer

/** Incremental Android btsnoop/H4 reader. Only received discovery events leave this parser. */
class HciSnoopParser(private val since: Long, private val emit: (Observation) -> Unit) {
    private var pending = ByteArray(0)
    private var header = false

    fun feed(bytes: ByteArray) {
        require(bytes.size <= 32768)
        pending += bytes
        var pos = 0
        if (!header) {
            if (pending.size < 16) return
            require(pending.copyOfRange(0, 8).contentEquals("btsnoop\u0000".toByteArray())) { "Not a btsnoop log" }
            require(int(8) == 1 && int(12) == 1002) { "Unsupported btsnoop format (requires H4)" }
            header = true
            pos = 16
        }
        while (pending.size - pos >= 24) {
            val original = int(pos)
            val length = int(pos + 4)
            require(length in 1..65535 && original >= length) { "Invalid btsnoop record" }
            if (pending.size - pos < 24 + length) break
            val flags = int(pos + 8)
            val at = (ByteBuffer.wrap(pending, pos + 16, 8).long - EPOCH_US) / 1000
            if (flags and 3 == 3 && at >= since && original == length) {
                event(pending.copyOfRange(pos + 24, pos + 24 + length), at)
            }
            pos += 24 + length
        }
        pending = pending.copyOfRange(pos, pending.size)
    }

    private fun int(offset: Int): Int = ByteBuffer.wrap(pending, offset, 4).int

    private fun event(b: ByteArray, at: Long) {
        fun u(i: Int) = b[i].toInt() and 255
        if (b.size < 4 || u(0) != 4 || u(2) != b.size - 3) return
        when (u(1)) {
            0x3e -> { // LE Meta Event
                if (b.size < 5) return
                var p = 5
                when (u(3)) {
                    0x02 -> repeat(u(4)) {
                        if (p + 10 > b.size) return
                        val size = u(p + 8)
                        if (p + 10 + size > b.size) return
                        report(b, p + 2, b.copyOfRange(p + 9, p + 9 + size),
                            b[p + 9 + size].toInt(), at, addressType = u(p + 1),
                            connectable = u(p) in listOf(0, 1))
                        p += 10 + size
                    }
                    0x0d -> repeat(u(4)) { // LE Extended Advertising Report
                        if (p + 24 > b.size) return
                        val size = u(p + 23)
                        if (p + 24 + size > b.size) return
                        val type = u(p) or (u(p + 1) shl 8)
                        // Fragmented/truncated payloads cannot be decoded as complete AD structures.
                        if ((type shr 5) and 3 == 0) {
                            report(b, p + 3, b.copyOfRange(p + 24, p + 24 + size),
                                b[p + 13].toInt(), at, addressType = u(p + 2),
                                connectable = type and 1 != 0,
                                tx = b[p + 12].toInt().takeUnless { it == 127 })
                        }
                        p += 24 + size
                    }
                }
            }
            0x02, 0x22, 0x2f -> { // Inquiry Result / with RSSI / Extended Inquiry Result
                var p = 4
                repeat(u(3)) {
                    val size = if (u(1) == 0x2f) 254 else 14
                    if (p + size > b.size) return
                    val c = p + if (u(1) == 0x02) 9 else 8
                    val cod = u(c) or (u(c + 1) shl 8) or (u(c + 2) shl 16)
                    val data = if (size == 254) b.copyOfRange(p + 14, p + size) else ByteArray(0)
                    val rssi = if (u(1) == 0x02) ClassicRadio.UNKNOWN_RSSI else b[p + 13].toInt()
                    report(b, p, data, rssi, at, classic = true, cod = cod)
                    p += size
                }
            }
        }
    }

    private fun report(
        packet: ByteArray, address: Int, data: ByteArray, rssi: Int, at: Long,
        classic: Boolean = false, cod: Int? = null, addressType: Int? = null,
        connectable: Boolean? = null, tx: Int? = null,
    ) {
        if (addressType == 255) return // anonymous, no stable device address
        val mac = (5 downTo 0).joinToString(":") { "%02X".format(packet[address + it].toInt() and 255) }
        if (mac == "00:00:00:00:00:00") return
        val ad = BleAdParser.parse(data)
        val mfg = ad.mfg.firstOrNull()
        emit(Observation(
            kind = RadioKind.BLE, mac = mac, name = ad.localName.orEmpty(), rssi = rssi,
            channel = 0, frequencyMhz = 2402, hiddenSsid = false,
            serviceUuids = (ad.uuids + ad.serviceData.map { it.uuid }).distinct(),
            manufacturerId = mfg?.companyId, manufacturerDataHex = mfg?.dataHex.orEmpty(),
            rawHex = data.toHexUpper(), extras = if (classic) "classic hci-snoop" else "hci-snoop", at = at,
            facts = RadioFacts(
                deviceClass = cod ?: ad.deviceClass, txPowerDbm = tx ?: ad.txPower,
                advFlags = ad.flags, appearance = ad.appearance,
                advertisingIntervalMs = ad.advertisingIntervalMs, connectable = connectable,
                addressType = when (addressType) { 0, 2 -> "Public"; 1, 3 -> "Random"; else -> null },
                mfgRecords = ad.mfg, serviceData = ad.serviceData,
            ),
        ))
    }

    companion object {
        const val EPOCH_US = 0x00dcddb30f2f8000L
    }
}
