package dev.namelessnanashi.fieldwatch.elevated

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import dev.namelessnanashi.fieldwatch.radio.RadioPermissions

/** What the phone currently allows this app. Read without any elevated access. */
data class ElevatedStatus(
    val shizuku: ShizukuShell.State,
    /** Item id → whether that change is in effect now; null when the phone does not say. */
    val items: Map<String, Boolean?>,
    val standbyBucket: Int,
    val adminActive: Boolean,
    val vector: VectorBridge.Info?,
) {
    val standbyBucketName: String
        get() = when (standbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "Restricted"
            EXEMPTED_BUCKET -> "Exempted"
            else -> "Bucket $standbyBucket"
        }

    companion object {
        /** UsageStatsManager.STANDBY_BUCKET_EXEMPTED is hidden; active device admins land here. */
        const val EXEMPTED_BUCKET = 5

        fun read(context: Context, shizuku: ShizukuShell): ElevatedStatus {
            val app = context.applicationContext
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val usage = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val bluetooth = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val location = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            fun check(block: () -> Boolean): Boolean? = runCatching(block).getOrNull()
            val items = mapOf(
                ElevatedItems.SECURE_SETTINGS to hasWriteSecureSettings(app),
                ElevatedItems.SCAN_THROTTLE to wifi?.let { w -> check { !w.isScanThrottleEnabled } },
                ElevatedItems.SCAN_ALWAYS to check {
                    Settings.Global.getInt(app.contentResolver, ElevatedItems.SCAN_ALWAYS_KEY) == 1
                },
                ElevatedItems.DOZE to power.isIgnoringBatteryOptimizations(app.packageName),
                ElevatedItems.BACKGROUND to !activity.isBackgroundRestricted,
                ElevatedItems.BACKGROUND_LOCATION to (app.checkSelfPermission(
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED),
                ElevatedItems.KEEP_PERMISSIONS to check { app.packageManager.isAutoRevokeWhitelisted },
                ElevatedItems.GRANT_PERMISSIONS to RadioPermissions.missing(app).isEmpty(),
                ElevatedItems.WIFI_ON to wifi?.let { w -> check { w.isWifiEnabled } },
                ElevatedItems.BLUETOOTH_ON to bluetooth?.let { b -> check { b.adapter?.isEnabled == true } },
                ElevatedItems.LOCATION_ON to location?.let { l -> check { l.isLocationEnabled } },
                // Apps cannot read bluetooth_prop; the ViewModel fills this in through root.
                ElevatedItems.HCI_SNOOP to null,
            )
            return ElevatedStatus(
                shizuku = shizuku.state(),
                items = items,
                standbyBucket = usage.appStandbyBucket,
                adminActive = runCatching { DeviceAdmin.isActive(app) }.getOrDefault(false),
                vector = VectorBridge.info(),
            )
        }

        fun hasWriteSecureSettings(context: Context): Boolean =
            context.checkSelfPermission(ElevatedItems.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * Turns Wi-Fi scan throttling back off without root or Shizuku. Runs only when the user
         * has turned on "Keep Wi-Fi scan throttling off" and secure-settings access was granted.
         * True when the setting was changed.
         */
        fun keepScanThrottleOff(context: Context): Boolean {
            if (!hasWriteSecureSettings(context)) return false
            val resolver = context.contentResolver
            val current = runCatching { Settings.Global.getInt(resolver, ElevatedItems.SCAN_THROTTLE_KEY, 1) }
                .getOrDefault(1)
            if (current == 0) return false
            return runCatching { Settings.Global.putInt(resolver, ElevatedItems.SCAN_THROTTLE_KEY, 0) }
                .getOrDefault(false)
        }
    }
}
