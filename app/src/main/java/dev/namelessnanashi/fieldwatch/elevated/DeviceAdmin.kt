package dev.namelessnanashi.fieldwatch.elevated

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Declares no policies. Being an active device admin is enough for Android to put the app in
 * the EXEMPTED standby bucket, which keeps background scanning from being deferred.
 */
class FieldwatchAdminReceiver : DeviceAdminReceiver()

object DeviceAdmin {
    fun component(context: Context) = ComponentName(context, FieldwatchAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    fun activationIntent(context: Context, explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)

    fun deactivate(context: Context) {
        dpm(context).removeActiveAdmin(component(context))
    }
}
