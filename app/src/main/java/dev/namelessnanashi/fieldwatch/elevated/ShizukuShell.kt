package dev.namelessnanashi.fieldwatch.elevated

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.namelessnanashi.fieldwatch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Commands through Shizuku's user service, running as the adb shell user. */
class ShizukuShell(private val context: Context) : ElevatedShell {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    override val label = "Shizuku"

    private val lock = Mutex()

    @Volatile
    private var service: IShellService? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("elevated")
            .debuggable(BuildConfig.DEBUG)
            .version(SERVICE_VERSION)
    }

    fun state(): State {
        if (!installed()) return State.NOT_INSTALLED
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return State.NOT_RUNNING
        val granted = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        return if (granted == PackageManager.PERMISSION_GRANTED) State.READY else State.NO_PERMISSION
    }

    /** Shows Shizuku's permission prompt. True when granted (or already granted). */
    suspend fun requestPermission(): Boolean {
        when (state()) {
            State.READY -> return true
            State.NO_PERMISSION -> Unit
            else -> return false
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != PERMISSION_REQUEST) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.requestPermission(PERMISSION_REQUEST)
        }
    }

    override suspend fun run(command: String): ShellResult {
        if (state() != State.READY) return ShellResult.failure("Shizuku is not running or not allowed")
        val shell = runCatching { bound() }.getOrElse {
            return ShellResult.failure("Shizuku service did not start: ${it.message}")
        }
        return withContext(Dispatchers.IO) {
            runCatching { ShellResult.decode(shell.exec(command)) }
                .getOrElse { ShellResult.failure(it.message ?: "Shizuku call failed") }
        }
    }

    private suspend fun bound(): IShellService = lock.withLock {
        service?.takeIf { it.asBinder().pingBinder() } ?: withTimeout(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val shell = IShellService.Stub.asInterface(binder)
                        service = shell
                        if (cont.isActive) cont.resume(shell)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                    }
                }
                Shizuku.bindUserService(args, connection)
            }
        }
    }

    private fun installed(): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 4_201
        private const val SERVICE_VERSION = 1
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}
