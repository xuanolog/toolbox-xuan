package cn.edu.szcu.connect

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address

class WifiConnector(context: Context) {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val current = MutableStateFlow("未连接 Wi-Fi")
    val ssid = current.asStateFlow()
    private val seen = java.util.concurrent.ConcurrentHashMap<Network, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    @SuppressLint("MissingPermission")
    fun refresh() {
        // Returning from the system Wi-Fi panel must not clear an in-flight network's identity.
        if (callback != null) { publish(); return }
        val flags = if (Build.VERSION.SDK_INT >= 31) ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO else 0
        callback = if (Build.VERSION.SDK_INT >= 31) createCallback(flags) else createLegacyCallback()
        try {
            connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback!!)
            publish()
        } catch (_: SecurityException) { callback = null; current.value = "需要 Wi-Fi 权限" }
    }
    @androidx.annotation.RequiresApi(31)
    private fun createCallback(flags: Int) = object : ConnectivityManager.NetworkCallback(flags) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { update(network, caps) }
        override fun onLost(network: Network) { seen.remove(network); publish() }
    }
    private fun createLegacyCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { update(network, caps) }
        override fun onLost(network: Network) { seen.remove(network); publish() }
    }
    private fun update(network: Network, caps: NetworkCapabilities) {
        val name = clean((caps.transportInfo as? WifiInfo)?.ssid)
        if (name != null) seen[network] = name else seen.remove(network)
        publish()
    }
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun legacySsid(): String? = runCatching { clean(wifi.connectionInfo.ssid) }.getOrNull()
    private fun clean(s: String?) = s?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    @Suppress("DEPRECATION")
    private fun wifiNetworks() = connectivity.allNetworks.filter {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    private fun publish() {
        current.value = wifiNetworks().firstNotNullOfOrNull { seen[it] } ?: legacySsid()
            ?: if (wifiNetworks().isNotEmpty()) "Wi-Fi 已连接（需权限识别名称）" else "未连接 Wi-Fi"
    }
    fun find(ssid: String): Network? {
        val networks = wifiNetworks()
        return networks.firstOrNull { seen[it] == ssid }
            ?: networks.singleOrNull()?.takeIf { legacySsid() == ssid }
    }
    fun ip(network: Network): String? = connectivity.getLinkProperties(network)?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    fun matches(network: Network, ssid: String): Boolean {
        val networks = wifiNetworks()
        return network in networks && (seen[network] == ssid ||
            (networks.size == 1 && legacySsid() == ssid))
    }
    fun validated(network: Network): Boolean {
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }
    fun reevaluate(network: Network, reachable: Boolean) {
        connectivity.reportNetworkConnectivity(network, reachable)
    }

    @SuppressLint("MissingPermission")
    fun removeLegacySuggestions(): Boolean {
        // Only remove this application's suggestions. The OS may release their connection afterwards.
        return runCatching {
            if (wifi.networkSuggestions.isEmpty()) return@runCatching true
            val result = if (Build.VERSION.SDK_INT >= 33)
                wifi.removeNetworkSuggestions(emptyList(), WifiManager.ACTION_REMOVE_SUGGESTION_LINGER)
            else wifi.removeNetworkSuggestions(emptyList())
            result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        }.getOrDefault(false)
    }
    @SuppressLint("MissingPermission")
    fun hasLegacySuggestions(): Boolean = wifi.networkSuggestions.isNotEmpty()
    suspend fun await(ssid: String, timeoutMs: Long): Network? = withTimeoutOrNull(timeoutMs) {
        var found = find(ssid)
        while (found == null || ip(found) == null) { delay(300); found = find(ssid) }
        found
    }
    fun close() { callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }; callback = null }
}
