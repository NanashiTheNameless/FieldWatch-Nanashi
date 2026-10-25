package dev.namelessnanashi.fieldwatch.elevated

import dev.namelessnanashi.fieldwatch.elevated.xposed.XposedTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevatedCommandsTest {
    private val pkg = "dev.namelessnanashi.fieldwatch"
    private val items = ElevatedItems.all(
        pkg,
        listOf("android.permission.BLUETOOTH_SCAN", "android.permission.ACCESS_FINE_LOCATION"),
    )

    private fun item(id: String) = items.single { it.id == id }

    @Test
    fun everyItemIsSeparateWithItsOwnId() {
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertTrue(items.all { it.apply.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun persistentChangesCanBeUndone() {
        listOf(
            ElevatedItems.SECURE_SETTINGS,
            ElevatedItems.SCAN_THROTTLE,
            ElevatedItems.SCAN_ALWAYS,
            ElevatedItems.DOZE,
            ElevatedItems.BACKGROUND,
            ElevatedItems.BACKGROUND_LOCATION,
            ElevatedItems.KEEP_PERMISSIONS,
            ElevatedItems.HCI_SNOOP,
        ).forEach { id -> assertTrue(id, !item(id).undo.isNullOrBlank()) }
        assertNull(item(ElevatedItems.WIFI_ON).undo)
    }

    @Test
    fun commandsTargetThisPackage() {
        assertEquals("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS", item(ElevatedItems.SECURE_SETTINGS).apply)
        assertEquals("pm revoke $pkg android.permission.WRITE_SECURE_SETTINGS", item(ElevatedItems.SECURE_SETTINGS).undo)
        assertEquals("cmd deviceidle whitelist +$pkg", item(ElevatedItems.DOZE).apply)
        assertEquals(
            "pm grant $pkg android.permission.BLUETOOTH_SCAN; pm grant $pkg android.permission.ACCESS_FINE_LOCATION",
            item(ElevatedItems.GRANT_PERMISSIONS).apply,
        )
    }

    @Test
    fun bootPermissionWorksThroughShizukuAndCanBeRevoked() {
        val permission = item(ElevatedItems.BACKGROUND_LOCATION)
        assertFalse(permission.rootOnly)
        assertEquals("pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION", permission.apply)
        assertEquals("pm revoke $pkg android.permission.ACCESS_BACKGROUND_LOCATION", permission.undo)
    }

    @Test
    fun onlyTheSnoopLogNeedsRoot() {
        assertEquals(listOf(ElevatedItems.HCI_SNOOP), items.filter { it.rootOnly }.map { it.id })
        assertTrue(item(ElevatedItems.HCI_SNOOP).undo!!.contains("rm -f ${ElevatedItems.SNOOP_LOG_PATH}"))
    }

    @Test
    fun shellResultRoundTripsOverTheBinderWireFormat() {
        val original = ShellResult(1, "line one\nline two")
        assertEquals(original, ShellResult.decode(original.encode()))
        assertEquals(ShellResult(0, ""), ShellResult.decode("0"))
        assertFalse(ShellResult.decode("garbage").ok)
    }

    @Test
    fun xposedMatchesOnlyThisAppsPackages() {
        assertTrue(XposedTargets.isOwnApp("dev.namelessnanashi.fieldwatch"))
        assertTrue(XposedTargets.isOwnApp("dev.namelessnanashi.fieldwatch.debug"))
        assertFalse(XposedTargets.isOwnApp("app.fieldwatch"))
        assertFalse(XposedTargets.isOwnApp("dev.namelessnanashi.fieldwatch.evil"))
        assertFalse(XposedTargets.isOwnApp(null))
    }
}
