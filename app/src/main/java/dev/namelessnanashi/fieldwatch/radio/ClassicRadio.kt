package dev.namelessnanashi.fieldwatch.radio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.namelessnanashi.fieldwatch.domain.Observation
import dev.namelessnanashi.fieldwatch.domain.RadioFacts
import dev.namelessnanashi.fieldwatch.domain.RadioKind
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bluetooth Classic inquiry. Off unless the user turns it on.
 *
 * Classic devices answer an inquiry instead of advertising, so they never appear in a BLE scan.
 * An inquiry is about 12 seconds of radio work, which costs battery and shares the chip with the
 * BLE scan, so it runs on a long interval and only while the screen is on.
 */
class ClassicRadio(
    private val context: Context,
    private val onObservation: (Observation) -> Unit,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val registered = AtomicBoolean(false)
    private val ownInquiry = AtomicBoolean(false)
    private val lastStartAt = AtomicLong(0L)
    private val seen = AtomicLong(0L)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> runCatching { found(intent) }
                    .onFailure { Log.e(TAG, "inquiry result failed", it) }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> ownInquiry.set(false)
            }
        }
    }

    @Synchronized
    fun start() {
        if (!permitted() || registered.get()) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered.set(true)
        }.onFailure { Log.w(TAG, "Could not register inquiry receiver", it) }
    }

    @Synchronized
    fun stop() {
        cancelInquiry()
        if (registered.getAndSet(false)) {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Starts one inquiry when [minIntervalMs] has passed since the last. True when one started. */
    // permitted() checks both runtime permissions; runCatching also handles revocation races.
    @SuppressLint("MissingPermission")
    @Synchronized
    fun requestInquiry(minIntervalMs: Long): Boolean {
        if (!registered.get() || !permitted()) return false
        val radio = adapter ?: return false
        if (!runCatching { radio.isEnabled }.getOrDefault(false)) return false
        val now = SystemClock.elapsedRealtime()
        if (lastStartAt.get() != 0L && now - lastStartAt.get() < minIntervalMs) return false
        if (runCatching { radio.isDiscovering }.getOrDefault(false)) return false
        val started = runCatching { radio.startDiscovery() }.getOrDefault(false)
        if (started) {
            lastStartAt.set(now)
            ownInquiry.set(true)
        }
        return started
    }

    @SuppressLint("MissingPermission") // Optional query; a revoked permission returns false.
    fun isDiscovering(): Boolean =
        registered.get() && runCatching { adapter?.isDiscovering == true }.getOrDefault(false)

    fun hits(): Long = seen.get()

    @SuppressLint("MissingPermission") // Checks permitted() and catches revocation races.
    private fun cancelInquiry() {
        if (!ownInquiry.getAndSet(false) || !permitted()) return
        runCatching { adapter?.takeIf { it.isDiscovering }?.cancelDiscovery() }
    }

    private fun found(intent: Intent) {
        val device = deviceOf(intent) ?: return
        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
        val mac = runCatching { device.address }.getOrNull().orEmpty()
        if (mac.isEmpty()) return
        val name = runCatching { intent.getStringExtra(BluetoothDevice.EXTRA_NAME) }.getOrNull()
            ?.trim().orEmpty()
        val cod = runCatching { classOf(intent)?.let { BleAdParser.btClass24(it) } }.getOrNull()
        seen.incrementAndGet()
        onObservation(
            Observation(
                kind = RadioKind.BLE,
                mac = mac,
                name = name,
                rssi = if (rssi == Short.MIN_VALUE.toInt()) UNKNOWN_RSSI else rssi,
                channel = 0,
                frequencyMhz = 2402,
                hiddenSsid = false,
                serviceUuids = emptyList(),
                manufacturerId = null,
                manufacturerDataHex = "",
                rawHex = "",
                extras = CLASSIC_TAG,
                at = System.currentTimeMillis(),
                facts = RadioFacts(deviceClass = cod),
            ),
        )
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }.getOrNull()

    private fun classOf(intent: Intent): BluetoothClass? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
        }
    }.getOrNull()

    private fun permitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /** Marks a sighting that came from an inquiry rather than a BLE advertisement. */
        const val CLASSIC_TAG = "classic"

        /** An inquiry is ~12 s of radio; these keep it from crowding the BLE scan. */
        const val PERFORMANCE_INTERVAL_MS = 60_000L
        const val BALANCED_INTERVAL_MS = 120_000L
        const val SAVER_INTERVAL_MS = 300_000L

        /** Some phones report no RSSI for an inquiry result. */
        const val UNKNOWN_RSSI = -127

        private const val TAG = "FieldwatchClassic"
    }
}
