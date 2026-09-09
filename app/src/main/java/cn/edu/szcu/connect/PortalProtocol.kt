package cn.edu.szcu.connect

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

open class PortalException(message: String) : Exception(message)

data class PortalContext(val ip: String, val ipv6: String = "", val mac: String = "000000000000",
    val acIp: String = "", val acName: String = "", val jsVersion: String = "3.3.3", val vlan: String = "1")

data class PortalReply(val success: Boolean, val message: String)

object PortalProtocol {
    const val HOST = "172.16.8.22"
    const val ROOT = "http://172.16.8.22/"
    const val ENDPOINT = "http://172.16.8.22:801/eportal/?c=Portal&a=login"

    fun trusted(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "http" && u.host == HOST && u.port in listOf(-1, 80, 801) && u.userInfo == null
    }.getOrDefault(false)

    /** Reads literal assignments only. Never evaluates page scripts. */
    fun literal(source: String, name: String): String? =
        Regex("(?<![A-Za-z0-9_$])" + Regex.escape(name) + "\\s*[:=]\\s*['\"]([^'\"\\r\\n]*)['\"]")
            .find(source)?.groupValues?.get(1)?.trim()

    fun parseContext(html: String, wifiIp: String, scripts: String = ""): PortalContext {
        val successPage = Regex("<!--\\s*Dr\\.COMWebLoginID_3\\.htm\\s*-->").containsMatchIn(html)
        if ((!successPage && literal(html, "v4serip") != HOST) || !html.contains("a41.js"))
            throw PortalException("认证页面已变化，请打开校园网登录页手动认证")
        val ip = literal(html, "v46ip") ?: literal(html, "ss5") ?: (if (successPage) literal(html, "v4ip") else null)
            ?: throw PortalException("认证页未提供手机 IP，请重新连接校园 Wi-Fi")
        if (ip != wifiIp || !ip.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")))
            throw PortalException("认证页 IP 与当前 Wi-Fi 不一致，请重新连接后重试")
        val doc = Jsoup.parse(html)
        if (doc.select("input[name=captcha]").any { !it.attr("style").contains("display:none") && !it.attr("style").contains("display: none") })
            throw PortalException("认证页需要验证码，请手动登录")
        // The page supplies the MAC (zero on the inspected portal); never use the phone's factory MAC.
        val version = literal(scripts, "jsVersion") ?: "3.3.3"
        val ipv6 = literal(html, "myv6ip").orEmpty()
        if (ipv6.isNotEmpty()) throw PortalException("检测到不同的 IPv6 认证模式，请手动登录")
        val mac = (literal(html, "ss4") ?: literal(html, "olmac") ?: "000000000000").replace(":", "").replace("-", "")
        if (!mac.matches(Regex("[0-9A-Fa-f]{12}"))) throw PortalException("认证页终端信息异常，请手动登录")
        val vlan = literal(html, "vlanid") ?: Regex("\\bvlanid\\s*=\\s*(\\d+)").find(html)?.groupValues?.get(1) ?: "1"
        if (!vlan.matches(Regex("[0-9]{1,4}"))) throw PortalException("认证页 VLAN 参数异常")
        return PortalContext(ip = ip, mac = mac, jsVersion = version, vlan = vlan)
    }

    fun account(profile: Profile): String = ",1," + profile.account + profile.carrier.suffix

    fun loginUrl(profile: Profile, ctx: PortalContext, callback: String, nonce: Int): String {
        profile.validate()
        require(callback.matches(Regex("dr[0-9]+")))
        val params = linkedMapOf(
            "callback" to callback, "login_method" to "1", "user_account" to account(profile),
            "user_password" to profile.password, "wlan_user_ip" to ctx.ip,
            "wlan_user_ipv6" to ctx.ipv6, "wlan_user_mac" to ctx.mac,
            "wlan_ac_ip" to ctx.acIp, "wlan_ac_name" to ctx.acName,
            "jsVersion" to ctx.jsVersion, "v" to nonce.toString()
        )
        return ENDPOINT + "&" + params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun parseReply(body: String, callback: String): PortalReply {
        val match = Regex("^\\s*${Regex.escape(callback)}\\s*\\(([\\s\\S]*)\\)\\s*;?\\s*$").matchEntire(body)
            ?: throw PortalException("认证接口返回格式异常，请手动登录")
        try {
            val obj = JsonParser.parseString(match.groupValues[1]).asJsonObject
            val result = obj.get("result")?.asString ?: throw IllegalArgumentException()
            val ok = result == "1" || result == "ok"
            // Do not surface server-echoed account/password strings in the app or logs.
            val raw = listOf("msg", "ret_code", "message").mapNotNull { obj.get(it)?.takeIf { v -> v.isJsonPrimitive }?.asString }.joinToString(" ")
            val message = when {
                ok -> "校园网认证成功"
                raw.contains("验证码") || raw.contains("captcha", true) -> "需要验证码，请打开校园网登录页"
                raw.contains("在线") || raw.contains("already", true) -> "账号可能已在线，请先确认现有会话"
                raw.contains("欠费") || raw.contains("余额") -> "账号余额不足或服务已停用，请检查校园网账户"
                else -> "认证未通过，请核对运营商、学工号、密码及账号状态"
            }
            return PortalReply(ok, message)
        } catch (_: Exception) { throw PortalException("认证接口返回格式异常，请手动登录") }
    }
}
