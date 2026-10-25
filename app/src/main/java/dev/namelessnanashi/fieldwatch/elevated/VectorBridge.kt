package dev.namelessnanashi.fieldwatch.elevated

import android.content.SharedPreferences
import dev.namelessnanashi.fieldwatch.elevated.xposed.XposedTargets
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App side of the Vector module. The framework hands this app a service binder while the
 * module is enabled; the per-hook switches live in its remote preferences, which the module
 * reads inside the Bluetooth process. Every hook defaults to off.
 */
object VectorBridge {
    data class Info(val framework: String, val version: String, val scope: List<String>) {
        val bluetoothInScope: Boolean get() = scope.any { it in XposedTargets.BLUETOOTH_PACKAGES }
    }

    private val service = MutableStateFlow<XposedService?>(null)
    val connected: StateFlow<XposedService?> = service.asStateFlow()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                service.value = bound
            }

            override fun onServiceDied(dead: XposedService) {
                if (service.value === dead) service.value = null
            }
        })
    }

    fun info(): Info? = service.value?.let { s ->
        runCatching { Info(s.frameworkName, s.frameworkVersion, s.scope) }.getOrNull()
    }

    fun hookEnabled(key: String): Boolean = prefs()?.getBoolean(key, false) ?: false

    fun setHook(key: String, on: Boolean): Boolean = prefs()?.edit()?.putBoolean(key, on)?.commit() ?: false

    /** Vector shows its own prompt; nothing changes unless the user approves it there. */
    fun requestBluetoothScope(onResult: (String) -> Unit) {
        val s = service.value ?: return onResult("Vector is not connected")
        val missing = XposedTargets.BLUETOOTH_PACKAGES.filter { pkg ->
            runCatching { s.scope }.getOrDefault(emptyList()).none { it == pkg }
        }
        runCatching {
            s.requestScope(missing, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    onResult("Scope approved: ${approved.joinToString()}. Restart Bluetooth to load the hooks.")
                }

                override fun onScopeRequestFailed(message: String) {
                    onResult("Scope request failed: $message")
                }
            })
        }.onFailure { onResult("Scope request failed: ${it.message}") }
    }

    private fun prefs(): SharedPreferences? =
        runCatching { service.value?.getRemotePreferences(XposedTargets.PREFS_GROUP) }.getOrNull()
}
