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
    private var pageAuthenticated = false
    private var loginAccepted = false
    private val cookies = PortalCookies()
    private var portalInitialized = false
    override fun checkNetwork() {
        if (!wifi.matches(network, ssid)) throw PortalException("目标 Wi-Fi 已断开或发生切换，已停止认证")
        if (wifi.ip(network) != initialIp) throw PortalException("Wi-Fi 地址发生变化，已停止认证，请重试")
    }
    private data class Response(val code: Int, val body: String)

    /** Wi-Fi sockets only; scoped school cookies, no proxy, redirects, request logging or cache. */
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
                val cookieHeaders = cookies.headers(url)
                cookieHeaders.forEach { (name, values) -> c.setRequestProperty(name, values.joinToString("; ")) }
                val code = c.responseCode
                cookies.receive(url, c.headerFields)
                if (PortalProtocol.trusted(url)) {
                    val kind = when {
                        url.contains("a=page_type_data") -> "INIT"
                        url.contains("a=online_list") -> "STATUS"
                        url.contains("a=logout") -> "LOGOUT"
                        url.contains("a=login") -> "LOGIN"
                        else -> "PAGE"
                    }
                    android.util.Log.i("SZCU_PROTOCOL", "$kind http=$code cookie=${cookieHeaders.values.flatten().any { it.contains("PHPSESSID=") }} changed=${cookieHeaders != cookies.headers(url)}")
                }
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
        // Probe the actual external site the user expects, not OS endpoints often allowed before login.
        currentCoroutineContext().ensureActive()
        return try {
            val response = get("https://www.baidu.com/", 5000)
            checkNetwork()
            response.code == 200 && response.body.contains("baidu.com", ignoreCase = true)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { checkNetwork(); false }
    }

    override suspend fun verify(): Boolean = NetworkVerifier().verify(
        probe = ::online,
        authenticated = {
            try { loginAccepted || inspect().authenticated }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { checkNetwork(); false }
        },
        reevaluate = { wifi.reevaluate(network, it) },
        checkNetwork = ::checkNetwork,
    )

    override suspend fun inspect(): CampusSession {
        val ctx = loadContext()
        val pageSuccess = pageAuthenticated
        val callback = "dr" + Random.nextInt(1000, 999999)
        sessionContext = ctx
        val status = try {
            val response = get(SessionProtocol.statusUrl(ctx, callback, Random.nextInt(500, 10500)))
            if (response.code != 200) throw PortalException("校园会话查询失败，已停止操作")
            SessionProtocol.parseStatus(response.body, callback, ctx.ip)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (pageSuccess) CampusSession(true) else throw e }
        checkNetwork()
        return SessionProtocol.resolveStatus(status, pageSuccess)
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
        loginAccepted = false
        // Require both a login page and an offline list after the controller has applied logout.
        repeat(3) {
            kotlinx.coroutines.delay(1000)
            if (!inspect().authenticated) return
        }
        throw PortalException("注销请求已发送，但校园网页仍显示在线；已停止提交新账号，请稍后重试")
    }

    override suspend fun readContext(): PortalContext {
        // Equivalent to the school's Return button after logout; fetch fresh form parameters.
        val ctx = loadContext(PortalProtocol.RETURN)
        if (pageAuthenticated) throw PortalException("返回后校园网页仍显示在线，已停止提交新账号，请重新连接")
        return ctx
    }

    private suspend fun loadContext(entry: String = PortalProtocol.ROOT): PortalContext = withContext(Dispatchers.IO) {
        checkNetwork()
        val root = get(entry)
        if (root.code != 200) throw PortalException("校园认证入口不可用，请打开登录页检查")
        pageAuthenticated = SessionProtocol.isSuccessPage(root.body)
        val ip = wifi.ip(network) ?: throw PortalException("Wi-Fi 尚未获得 IPv4 地址")
        // Version is a protocol version, not the cache-busting timestamp in fileVersion.
        val bootstrap = get(PortalProtocol.ROOT + "a41.js")
        if (bootstrap.code != 200) throw PortalException("无法读取认证脚本，请稍后重试")
        val preliminary = PortalProtocol.parseContext(root.body, ip, bootstrap.body)
        if (!portalInitialized) {
            val callback = "dr" + Random.nextInt(1000, 999999)
            val init = get("http://172.16.8.22:801/eportal/?c=Portal&a=page_type_data&callback=$callback&v=${Random.nextInt(500, 10500)}")
            if (init.code != 200 || !PortalProtocol.parseReply(init.body, callback).success)
                throw PortalException("无法初始化校园网页会话，请稍后重试")
            portalInitialized = true
        }
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
        return PortalProtocol.parseReply(response.body, callback).also { loginAccepted = it.success }
    }
}
