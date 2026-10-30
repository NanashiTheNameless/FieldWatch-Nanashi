package dev.namelessnanashi.fieldwatch.radio

import dev.namelessnanashi.fieldwatch.domain.TakBirth

/** Callback-owned network inventory. Caller serializes access with the socket lock. */
internal class TakNetworkState<K> {
    data class Link(val wifi: Boolean, val ethernet: Boolean, val cellular: Boolean, val vpn: Boolean = false)
    data class Facts(val lan: Boolean, val online: Boolean, val birth: TakBirth)
    private val links = LinkedHashMap<K, Link>()
    fun update(key: K, link: Link): Boolean = links.put(key, link) != link
    fun lost(key: K): Boolean = links.remove(key) != null
    fun clear() = links.clear()
    fun facts(): Facts {
        val wifi = links.values.any { it.wifi }
        val ethernet = links.values.any { it.ethernet }
        val cellular = links.values.any { it.cellular }
        return Facts(takLanUp(wifi, ethernet), wifi || ethernet || cellular || links.values.any { it.vpn },
            takBirth(wifi, ethernet, cellular))
    }
}
