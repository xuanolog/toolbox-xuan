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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class NetworkPortalSession(private val network: Network, private val ssid: String, private val wifi: WifiConnector) : PortalSession {
    companion object { private val executor = Executors.newFixedThreadPool(3) }
    private val initialIp = wifi.ip(network) ?: throw PortalException("Wi-Fi 尚未获得 IPv4 地址")
    private var sessionContext: PortalContext? = null
    override fun checkNetwork() {
        if (!wifi.matches(network, ssid)) throw PortalException("目标 Wi-Fi 已断开或发生切换，已停止认证")
        if (wifi.ip(network) != initialIp) throw PortalException("Wi-Fi 地址发生变化，已停止认证，请重试")
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
                if (PortalProtocol.trusted(url)) c.setRequestProperty("Referer", PortalProtocol.ROOT)
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
                    PortalEncoding.decode(bytes, c.contentType, PortalProtocol.trusted(url))
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

    override suspend fun verify(): Boolean = NetworkVerifier().verify(
        probe = ::online,
        authenticated = {
            try { inspect().authenticated }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { checkNetwork(); false }
        },
        validated = { wifi.validated(network) },
        reevaluate = { wifi.reevaluate(network, it) },
        checkNetwork = ::checkNetwork,
    )

    override suspend fun inspect(): CampusSession {
        val ctx = loadContext()
        val callback = "dr" + Random.nextInt(1000, 999999)
        val response = get(SessionProtocol.statusUrl(ctx, callback, Random.nextInt(500, 10500)))
        if (response.code != 200) throw PortalException("校园会话查询失败，已停止操作")
        checkNetwork()
        sessionContext = ctx
        return SessionProtocol.parseStatus(response.body, callback, ctx.ip)
    }

    override suspend fun logout() {
        currentCoroutineContext().ensureActive()
        checkNetwork()
        val ctx = sessionContext ?: throw PortalException("请先检查当前校园会话")
        val actions = get(PortalProtocol.ROOT + "a42.js")
        val bootstrap = get(PortalProtocol.ROOT + "a41.js")
        if (actions.code != 200 || bootstrap.code != 200) throw PortalException("无法核实注销协议，已停止操作")
        SessionProtocol.validateScripts(bootstrap.body, actions.body)
        currentCoroutineContext().ensureActive()
        val callback = "dr" + Random.nextInt(1000, 999999)
        val response = get(SessionProtocol.logoutUrl(ctx, callback, Random.nextInt(500, 10500)))
        if (response.code != 200 || !PortalProtocol.parseReply(response.body, callback).success)
            throw PortalException("校园注销未成功，已停止登录，请检查校园网页")
        sessionContext = null
        // Give the access controller a bounded interval to apply logout before checking it.
        kotlinx.coroutines.delay(1000)
    }

    override suspend fun readContext(): PortalContext = loadContext()

    private suspend fun loadContext(): PortalContext = withContext(Dispatchers.IO) {
        checkNetwork()
        val root = get(PortalProtocol.ROOT)
        if (root.code != 200) throw PortalException("校园认证入口不可用，请打开登录页检查")
        // The online-list query decides session state; a success-page shell can outlive logout.
        val ip = wifi.ip(network) ?: throw PortalException("Wi-Fi 尚未获得 IPv4 地址")
        // Version is a protocol version, not the cache-busting timestamp in fileVersion.
        val bootstrap = get(PortalProtocol.ROOT + "a41.js")
        if (bootstrap.code != 200) throw PortalException("无法读取认证脚本，请稍后重试")
        val preliminary = PortalProtocol.parseContext(root.body, ip, bootstrap.body)
        val version = PortalProtocol.literal(root.body, "fileVersion") ?: throw PortalException("认证页面缺少模板版本，请手动登录")
        if (!version.matches(Regex("[0-9]+"))) throw PortalException("认证模板版本异常")
        suspend fun script(path: String): String {
            val response = get("http://172.16.8.22:801/eportal/extern/WZXY/$path?version=$version")
            if (response.code != 200) throw PortalException("无法读取校园认证模板，请稍后重试")
            return response.body
        }
        val config = script("config.js")
        val index = PortalTemplate.index(config, ip)
        val loginbox = script("ip/$index/loginbox.js")
        val mobile = script("ip/$index/mobile.js")
        preliminary.copy(jsVersion = PortalTemplate.validate(bootstrap.body, loginbox, mobile))
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
