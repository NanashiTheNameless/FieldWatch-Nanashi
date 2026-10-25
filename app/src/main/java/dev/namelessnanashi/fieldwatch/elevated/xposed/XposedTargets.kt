package dev.namelessnanashi.fieldwatch.elevated.xposed

/** Names the module hooks and the identity checks it uses. Plain Kotlin so it can be unit tested. */
object XposedTargets {
    /** This app, release and debug. Only these apps' scans are exempted. */
    val OWN_PACKAGES = setOf("dev.namelessnanashi.fieldwatch", "dev.namelessnanashi.fieldwatch.debug")

    /** AOSP and Google builds of the Bluetooth app. */
    val BLUETOOTH_PACKAGES = setOf("com.android.bluetooth", "com.google.android.bluetooth")

    /** le_scan from Android 15 on; gatt before. */
    val SCAN_PACKAGES = listOf("com.android.bluetooth.le_scan.", "com.android.bluetooth.gatt.")

    /** AppScanStats name field: mAppName in current AOSP, appName in older releases. */
    val APP_NAME_FIELDS = listOf("mAppName", "appName")

    /** ScanClient to AppScanStats: mStats in current AOSP, stats in older releases. */
    val STATS_FIELDS = listOf("mStats", "stats")

    /**
     * Classes that call the hooked checks, relative to a scan package: ScanController (and
     * GattService before Android 15) for registerScanner, and ScanManager's handler and native
     * wrapper for the screen and timeout checks.
     */
    val CALLER_CLASSES = listOf("ScanController", "GattService", "ScanManager\$ClientHandler", "ScanManager\$ScanNative")

    /** Remote preferences group shared by the app (VectorBridge) and the module. */
    const val PREFS_GROUP = "hooks"

    /** One switch per hook, all off until the user turns them on in Settings. */
    const val HOOK_TOO_FREQUENT = "too_frequent"
    const val HOOK_TOO_LONG = "too_long"
    const val HOOK_SCREEN_OFF = "screen_off"

    /**
     * AppScanStats names an app by PackageManager.getNameForUid: the package name, or
     * "sharedUserId:uid" for a shared uid. Only an exact package match counts.
     */
    fun isOwnApp(appName: String?): Boolean = appName != null && appName in OWN_PACKAGES
}
