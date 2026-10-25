package dev.namelessnanashi.fieldwatch.domain

object OuiLookup {
    fun vendor(mac: String): String? = RadioDb.vendorForMac(mac)
}
