package cn.edu.szcu.connect

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI

/** Operation-scoped PHP session. Never installs a process-global CookieHandler or persists tokens. */
class PortalCookies {
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    fun headers(url: String): Map<String, List<String>> =
        if (PortalProtocol.trusted(url)) manager.get(URI(url), emptyMap()) else emptyMap()
    fun receive(url: String, headers: Map<String, List<String>>) {
        if (PortalProtocol.trusted(url)) manager.put(URI(url), headers)
    }
}
