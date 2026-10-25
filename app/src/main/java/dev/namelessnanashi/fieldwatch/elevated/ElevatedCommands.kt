package dev.namelessnanashi.fieldwatch.elevated

/**
 * One change that root or Shizuku can make. Each runs only after the user confirms it on
 * Settings → Elevated access, where its exact [apply] (or [undo]) command is shown.
 */
data class ElevatedItem(
    val id: String,
    val title: String,
    val about: String,
    val apply: String,
    /** Null for one-shot actions with nothing to revert. */
    val undo: String?,
    /** Needs root; the adb shell user that Shizuku runs as cannot do it. */
    val rootOnly: Boolean = false,
)

object ElevatedItems {
    /** Settings.Global key behind Developer options → Wi-Fi scan throttling. */
    const val SCAN_THROTTLE_KEY = "wifi_scan_throttle_enabled"
    const val SCAN_ALWAYS_KEY = "wifi_scan_always_enabled"
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    const val SNOOP_MODE_PROP = "persist.bluetooth.btsnooplogmode"
    const val SNOOP_LOG_PATH = "/data/misc/bluetooth/logs/btsnoop_hci.log"

    const val SECURE_SETTINGS = "secure_settings"
    const val SCAN_THROTTLE = "scan_throttle"
    const val SCAN_ALWAYS = "scan_always"
    const val DOZE = "doze"
    const val BACKGROUND = "background"
    const val KEEP_PERMISSIONS = "keep_permissions"
    const val GRANT_PERMISSIONS = "grant_permissions"
    const val WIFI_ON = "wifi_on"
    const val BLUETOOTH_ON = "bluetooth_on"
    const val LOCATION_ON = "location_on"
    const val HCI_SNOOP = "hci_snoop"
    const val BACKGROUND_LOCATION = "background_location"
    const val RESTART_BLUETOOTH = "restart_bluetooth"

    /** One-shot action shown on the Vector card rather than in the main list. */
    val VECTOR_CARD_ITEMS = setOf(RESTART_BLUETOOTH)

    fun all(pkg: String, runtimePermissions: List<String>): List<ElevatedItem> = listOf(
        ElevatedItem(
            SECURE_SETTINGS,
            "Secure settings access",
            "Lets the app turn Wi-Fi scan throttling back off by itself later (only if you also turn on " +
                "\"Keep Wi-Fi scan throttling off\").",
            "pm grant $pkg $WRITE_SECURE_SETTINGS",
            "pm revoke $pkg $WRITE_SECURE_SETTINGS",
        ),
        ElevatedItem(
            SCAN_THROTTLE,
            "Wi-Fi scan throttling off",
            "Android limits apps to 4 Wi-Fi scans every 2 minutes. Same as the Developer options switch.",
            "settings put global $SCAN_THROTTLE_KEY 0",
            "settings put global $SCAN_THROTTLE_KEY 1",
        ),
        ElevatedItem(
            SCAN_ALWAYS,
            "Wi-Fi scanning while Wi-Fi is off",
            "Keeps Wi-Fi scans working when Wi-Fi is switched off. Same as Location → Wi-Fi scanning.",
            "cmd wifi set-scan-always-available enabled",
            "cmd wifi set-scan-always-available disabled",
        ),
        ElevatedItem(
            DOZE,
            "Battery optimization exemption",
            "Keeps Doze from deferring the app's work while the screen is off.",
            "cmd deviceidle whitelist +$pkg",
            "cmd deviceidle whitelist -$pkg",
        ),
        ElevatedItem(
            BACKGROUND,
            "Background running allowed",
            "Clears the \"background restricted\" state some phones set on apps that run a lot.",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default; cmd appops set $pkg RUN_IN_BACKGROUND default",
        ),
        ElevatedItem(
            KEEP_PERMISSIONS,
            "Keep permissions when unused",
            "Stops Android removing the app's permissions after months without use.",
            "cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore",
            "cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED default",
        ),
        ElevatedItem(
            GRANT_PERMISSIONS,
            "Grant radio permissions",
            "Grants the permissions the app already asks for: " +
                runtimePermissions.joinToString(", ") { it.substringAfterLast('.') } + ".",
            runtimePermissions.joinToString("; ") { "pm grant $pkg $it" },
            null,
        ),
        ElevatedItem(
            BACKGROUND_LOCATION,
            "Location permission for boot scanning",
            "Allows the location foreground service to start after boot without device owner. " +
                "Enable Start scanning after boot separately in Scan settings. Grant radio permissions first.",
            "pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION",
            "pm revoke $pkg android.permission.ACCESS_BACKGROUND_LOCATION",
        ),
        ElevatedItem(WIFI_ON, "Turn Wi-Fi on", "One-time.", "svc wifi enable", null),
        ElevatedItem(BLUETOOTH_ON, "Turn Bluetooth on", "One-time.", "svc bluetooth enable", null),
        ElevatedItem(LOCATION_ON, "Turn Location on", "One-time.", "cmd location set-location-enabled true", null),
        ElevatedItem(
            RESTART_BLUETOOTH,
            "Restart Bluetooth",
            "Turns Bluetooth off and on so the Bluetooth app reloads the Vector hooks. Connected devices drop briefly.",
            "svc bluetooth disable; sleep 2; svc bluetooth enable",
            null,
        ),
        ElevatedItem(
            HCI_SNOOP,
            "Bluetooth HCI snoop log",
            "Has Bluetooth record every packet to $SNOOP_LOG_PATH so the app can read raw advertisements " +
                "and Classic inquiry results Android normally drops. The log also records this phone's own " +
                "Bluetooth traffic, including pairing keys. Restarts Bluetooth. Turning it off deletes the log.",
            "setprop $SNOOP_MODE_PROP full; svc bluetooth disable; sleep 2; svc bluetooth enable",
            "setprop $SNOOP_MODE_PROP disabled; svc bluetooth disable; sleep 2; svc bluetooth enable; " +
                "rm -f $SNOOP_LOG_PATH ${SNOOP_LOG_PATH}.last",
            rootOnly = true,
        ),
    )
}
