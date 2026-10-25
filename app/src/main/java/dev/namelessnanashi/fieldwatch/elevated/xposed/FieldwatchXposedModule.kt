package dev.namelessnanashi.fieldwatch.elevated.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Vector (modern Xposed) module. Enable it for the Bluetooth app.
 *
 * In the Bluetooth stack it lifts three BLE scan limits, for this app's scans only:
 * - "scanning too frequently" (more than 5 scan starts in 30 s fails the next start),
 * - the 30-minute limit that downgrades a long scan to opportunistic,
 * - unfiltered scans being suspended while the screen is off.
 *
 * Each hook is installed but stays inert until its switch is turned on in the app
 * (Settings → Elevated access), which the module reads from Vector's remote preferences.
 */
class FieldwatchXposedModule : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        if (pkg in XposedTargets.BLUETOOTH_PACKAGES) hookBluetooth(param.classLoader)
    }

    private fun hookBluetooth(loader: ClassLoader) {
        val prefs = runCatching { getRemotePreferences(XposedTargets.PREFS_GROUP) }.getOrNull()
        fun on(key: String) = prefs?.getBoolean(key, false) == true
        var hooked = 0
        for (prefix in XposedTargets.SCAN_PACKAGES) {
            val stats = loader.findClass(prefix + "AppScanStats") ?: continue
            hooked += hookFalseWhen(stats, "isScanningTooFrequently") { self, _ ->
                on(XposedTargets.HOOK_TOO_FREQUENT) && isOwnStats(self)
            }
            hooked += hookFalseWhen(stats, "isScanningTooLong") { self, _ ->
                on(XposedTargets.HOOK_TOO_LONG) && isOwnStats(self)
            }
            loader.findClass(prefix + "ScanManager\$ClientHandler")?.let { handler ->
                hooked += hookFalseWhen(handler, "requiresScreenOn") { _, args ->
                    on(XposedTargets.HOOK_SCREEN_OFF) &&
                        isOwnStats(args.firstOrNull()?.let { readField(it, XposedTargets.STATS_FIELDS) })
                }
            }
            // The Bluetooth app is compiled ahead of time; small private checks can be inlined
            // into their callers, where a hook never runs. Deoptimize the callers.
            for (caller in XposedTargets.CALLER_CLASSES) {
                loader.findClass(prefix + caller)?.let(::deoptimizeAll)
            }
        }
        log(Log.INFO, TAG, "Bluetooth scan hooks installed: $hooked")
    }

    /** Hooks every boolean method named [name] on [cls] to return false when [predicate] holds. */
    private fun hookFalseWhen(
        cls: Class<*>,
        name: String,
        predicate: (self: Any?, args: List<Any?>) -> Boolean,
    ): Int {
        val methods: List<Method> = cls.declaredMethods.filter {
            it.name == name && it.returnType == Boolean::class.javaPrimitiveType
        }
        for (method in methods) {
            runCatching {
                hook(method).intercept { chain ->
                    if (predicate(chain.thisObject, chain.args)) false else chain.proceed()
                }
            }.onFailure { log(Log.WARN, TAG, "Could not hook ${cls.name}.$name", it) }
        }
        return methods.size
    }

    private fun deoptimizeAll(cls: Class<*>) {
        for (method in cls.declaredMethods) runCatching { deoptimize(method) }
    }

    private fun isOwnStats(stats: Any?): Boolean =
        stats != null && XposedTargets.isOwnApp(readField(stats, XposedTargets.APP_NAME_FIELDS) as? String)

    private fun ClassLoader.findClass(name: String): Class<*>? =
        runCatching { Class.forName(name, false, this) }.getOrNull()

    private fun readField(target: Any, names: List<String>): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            for (name in names) {
                val field: Field = runCatching { cls.getDeclaredField(name) }.getOrNull() ?: continue
                return runCatching { field.isAccessible = true; field.get(target) }.getOrNull()
            }
            cls = cls.superclass
        }
        return null
    }

    private companion object {
        const val TAG = "FieldWatch-Nanashi"
    }
}
