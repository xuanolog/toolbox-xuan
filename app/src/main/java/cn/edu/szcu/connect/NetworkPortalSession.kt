package cn.edu.szcu.connect

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class NetworkPortalSession(private val network: Network, private val ssid: String, private val wifi: WifiConnector) : PortalSession {
    companion object { private val executor = Executors.newFixedThreadPool(3) }
    override fun checkNetwork() {
        if (!wifi.matches(network, ssid)) throw PortalException("目标 Wi-Fi 已断开或发生切换，已停止认证")
    }
    private data class Response(val code: Int, val body: String)

    /** No default-network sockets, proxy, cookies, redirects, request logging, or cache. */
    private suspend fun get(url: String, timeout: Int = 8000): Response = suspendCancellableCoroutine { continuation ->
        val active = AtomicReference<HttpURLConnection?>()
        val future = executor.submit {
            try {
                if (!continuation.isActive) return@submit
                checkNetwork()
                val c = network.openConnection(URL(url), Proxy.NO_PROXY) as HttpURLConnection
                active.set(c)
                if (!continuation.isActive) { c.disconnect(); return@submit }
                c.connectTimeout = timeout
                c.readTimeout = timeout
                c.instanceFollowRedirects = false
                c.useCaches = false
                c.setRequestProperty("Cache-Control", "no-store")
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Mobile) SZCUConnect/0.1")
                c.setRequestProperty("Referer", PortalProtocol.ROOT)
                val code = c.responseCode
                val body = if (code == 200) {
                    val bytes = c.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val chunk = ByteArray(4096)
                        while (true) {
                            if (!continuation.isActive) throw CancellationException()
                            val n = input.read(chunk)
                            if (n < 0) break
                            if (out.size() + n > 1_048_576) throw PortalException("认证响应过大，已停止读取")
                            out.write(chunk, 0, n)
                        }
                        out.toByteArray()
                    }
                    val declared = Regex("charset=([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(c.contentType.orEmpty())?.groupValues?.get(1)
                    val meta = Regex("charset=[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(String(bytes.take(2048).toByteArray(), Charsets.ISO_8859_1))?.groupValues?.get(1)
                    val charset = runCatching { Charset.forName(declared ?: meta ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
                    String(bytes, charset)
                } else ""
                if (continuation.isActive) continuation.resume(Response(code, body))
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            } finally { active.getAndSet(null)?.disconnect() }
        }
        continuation.invokeOnCancellation { active.getAndSet(null)?.disconnect(); future.cancel(true) }
    }

    override suspend fun online(): Boolean {
        // TLS + exact 204 prevents a captive HTTP redirect or cellular fallback being treated as success.
        for (url in listOf("https://connect.rom.miui.com/generate_204", "https://connectivitycheck.gstatic.com/generate_204")) {
            currentCoroutineContext().ensureActive()
            try { if (get(url, 4000).code == 204) { checkNetwork(); return true } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Try the second independent HTTPS endpoint. */ }
        }
        return false
    }

    override suspend fun readContext(): PortalContext = withContext(Dispatchers.IO) {
        checkNetwork()
        val root = get(PortalProtocol.ROOT)
        if (root.code != 200) throw PortalException("校园认证入口不可用，请打开登录页检查")
        val ip = wifi.ip(network) ?: throw PortalException("Wi-Fi 尚未获得 IPv4 地址")
        // Version is a protocol version, not the cache-busting timestamp in fileVersion.
        val bootstrap = get(PortalProtocol.ROOT + "a41.js")
        if (bootstrap.code != 200) throw PortalException("无法读取认证脚本，请稍后重试")
        PortalProtocol.parseContext(root.body, ip, bootstrap.body)
    }

    override suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply {
        currentCoroutineContext().ensureActive()
        checkNetwork()
        if (wifi.ip(network) != ctx.ip) throw PortalException("Wi-Fi 地址发生变化，请重新连接")
        val callback = "dr" + Random.nextInt(1000, 999999)
        val url = PortalProtocol.loginUrl(profile, ctx, callback, Random.nextInt(500, 10500))
        check(PortalProtocol.trusted(url))
        val response = get(url)
        if (response.code != 200) throw PortalException("认证接口不可用或发生重定向；未向其他地址发送密码")
        return PortalProtocol.parseReply(response.body, callback)
    }
}
