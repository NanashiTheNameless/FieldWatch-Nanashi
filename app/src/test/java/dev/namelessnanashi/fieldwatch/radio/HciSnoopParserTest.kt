package dev.namelessnanashi.fieldwatch.radio

import dev.namelessnanashi.fieldwatch.data.DeviceStore
import dev.namelessnanashi.fieldwatch.domain.CodDecoder
import dev.namelessnanashi.fieldwatch.domain.Observation
import dev.namelessnanashi.fieldwatch.domain.RadioKind
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class HciSnoopParserTest {
    private val at = 1_790_000_000_000L
    private val address = bytes(6, 5, 4, 3, 2, 1)
    private val name = bytes(4, 9, 'T'.code, 'a'.code, 'g'.code)
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
    private fun header(type: Int = 1002) = "btsnoop\u0000".toByteArray() +
        ByteBuffer.allocate(8).putInt(1).putInt(type).array()
    private fun event(code: Int, payload: ByteArray) = bytes(4, code, payload.size) + payload
    private fun record(packet: ByteArray, time: Long = at, flags: Int = 3, original: Int = packet.size): ByteArray =
        ByteBuffer.allocate(24).putInt(original).putInt(packet.size).putInt(flags).putInt(0)
            .putLong(HciSnoopParser.EPOCH_US + time * 1000).array() + packet
    private fun legacy(data: ByteArray = name, count: Int = 1): ByteArray {
        val report = bytes(0, 1) + address + bytes(data.size) + data + bytes(-62)
        return event(0x3e, bytes(2, count) + (1..count).fold(ByteArray(0)) { a, _ -> a + report })
    }
    private fun extended(type: Int = 1, addrType: Int = 0): ByteArray = event(0x3e,
        bytes(0x0d, 1, type, 0, addrType) + address +
            bytes(1, 0, 0xff, -8, -43, 0, 0, 0) + ByteArray(6) + bytes(name.size) + name)

    @Test fun partialHeadersAndRecordsAreBufferedWithoutDuplicateReports() {
        val results = mutableListOf<Observation>()
        val parser = HciSnoopParser(at, results::add)
        val capture = header() + record(legacy(count = 2))
        capture.forEach { parser.feed(byteArrayOf(it)) }
        assertEquals(2, results.size)
        val row = results.first()
        assertEquals("01:02:03:04:05:06", row.mac)
        assertEquals("Tag", row.name)
        assertEquals(-62, row.rssi)
        assertEquals(at, row.at)
        assertEquals(RadioKind.BLE, row.kind)
        assertEquals("Random", row.facts.addressType)
        assertEquals(true, row.facts.connectable)
        parser.feed(ByteArray(0))
        assertEquals(2, results.size)
    }

    @Test fun extendedReportsDecodeSignedPowerAndSkipFragmentsAndAnonymousAddresses() {
        val results = mutableListOf<Observation>()
        HciSnoopParser(at, results::add).feed(header() + record(extended()) +
            record(extended(type = 0x21)) + record(extended(type = 0x41)) +
            record(extended(addrType = 255)))
        assertEquals(1, results.size)
        assertEquals(-43, results.single().rssi)
        assertEquals(-8, results.single().facts.txPowerDbm)
        assertEquals("Tag", results.single().name)
    }

    @Test fun inquiryResultsUseExistingCodDecoderAndBluetoothList() {
        val results = mutableListOf<Observation>()
        val cod = bytes(4, 4, 0x20) // Audio/Video, wearable headset, Audio service
        val inquiry = address + bytes(1, 0) + cod + bytes(0, 0, -51)
        val extended = inquiry + name + ByteArray(240 - name.size)
        HciSnoopParser(at, results::add).feed(header() + record(event(0x22, bytes(1) + inquiry)) +
            record(event(0x2f, bytes(1) + extended)) +
            record(event(0x02, bytes(1) + address + bytes(1, 0, 0) + cod + bytes(0, 0))))
        assertEquals(3, results.size)
        assertEquals(0x200404, results.first().facts.deviceClass)
        assertEquals("Audio / Video", CodDecoder.decode(results.first().facts.deviceClass!!).major)
        assertEquals("Tag", results[1].name)
        assertEquals(-127, results[2].rssi)
        val store = DeviceStore()
        results.forEach { store.ingest(it, emptyList(), 60) }
        val row = store.find("BLE:01:02:03:04:05:06")!!
        assertEquals("Tag", row.name)
        assertEquals(0x200404, row.facts.deviceClass)
    }

    @Test fun oldPacketsOutgoingPacketsAclAndTruncatedRecordsNeverBecomeSightings() {
        val results = mutableListOf<Observation>()
        HciSnoopParser(at, results::add).feed(header() +
            record(legacy(), time = at - 1) + record(legacy(), flags = 2) +
            record(bytes(2, 0, 0, 0, 0, 1, 2, 3)) +
            record(legacy(), original = legacy().size + 2) + record(legacy()))
        assertEquals(1, results.size)
    }

    @Test fun advertisementsKeepManufacturerServiceAndCodFields() {
        val results = mutableListOf<Observation>()
        val data = name + bytes(5, 0xff, 0x4c, 0, 0x12, 0x34) +
            bytes(4, 0x16, 0x0f, 0x18, 0x64) + bytes(4, 0x0d, 4, 4, 0x20)
        HciSnoopParser(at, results::add).feed(header() + record(legacy(data)))
        val row = results.single()
        assertEquals(0x4c, row.manufacturerId)
        assertEquals("1234", row.manufacturerDataHex)
        assertEquals("180F", row.serviceUuids.single())
        assertEquals("64", row.facts.serviceData.single().dataHex)
        assertEquals(0x200404, row.facts.deviceClass)
        assertTrue(row.rawHex.startsWith("0409546167"))
    }

    @Test fun malformedEventLengthsAndCountsAreIgnoredSafely() {
        val results = mutableListOf<Observation>()
        val parser = HciSnoopParser(at, results::add)
        parser.feed(header())
        val random = Random(15)
        repeat(1000) {
            val payload = random.nextBytes(random.nextInt(0, 256))
            val code = listOf(0x3e, 0x22, 0x02, 0x2f)[it % 4]
            parser.feed(record(event(code, payload)))
        }
        parser.feed(record(legacy()))
        assertEquals("Tag", results.last().name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedDatalinkIsRejected() {
        HciSnoopParser(at) {}.feed(header(type = 2001))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedRecordIsRejectedBeforeAllocation() {
        val invalid = ByteBuffer.allocate(24).putInt(Int.MAX_VALUE).putInt(Int.MAX_VALUE).array()
        HciSnoopParser(at) {}.feed(header() + invalid)
    }
}
