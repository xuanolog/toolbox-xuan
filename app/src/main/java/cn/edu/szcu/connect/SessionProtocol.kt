package cn.edu.szcu.connect

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder

object SessionProtocol {
    fun isSuccessPage(html: String): Boolean = Regex("<!--\\s*Dr\\.COMWebLoginID_3\\.htm\\s*-->").containsMatchIn(html)
    fun resolveStatus(status: CampusSession, successPage: Boolean): CampusSession =
        if (successPage && !status.authenticated) CampusSession(true) else status

    private fun url(action: String, ctx: PortalContext, callback: String, nonce: Int, logout: Boolean): String {
        require(callback.matches(Regex("dr[0-9]+")))
        val params = linkedMapOf("callback" to callback, "user_account" to "drcom", "user_password" to "123",
            "wlan_user_mac" to ctx.mac, "wlan_user_ip" to ctx.ip, "jsVersion" to ctx.jsVersion, "v" to nonce.toString())
        if (logout) params.putAll(mapOf("login_method" to "1", "ac_logout" to "1", "register_mode" to "1",
            "wlan_user_ipv6" to ctx.ipv6, "wlan_vlan_id" to ctx.vlan, "wlan_ac_ip" to ctx.acIp, "wlan_ac_name" to ctx.acName))
        else params["curr_user_ip"] = ctx.ip
        return "http://172.16.8.22:801/eportal/?c=Portal&a=$action&" + params.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")
        }
    }
    fun statusUrl(ctx: PortalContext, callback: String, nonce: Int) = url("online_list", ctx, callback, nonce, false)
    fun logoutUrl(ctx: PortalContext, callback: String, nonce: Int) = url("logout", ctx, callback, nonce, true)

    private fun json(body: String, callback: String): JsonObject = try {
        require(callback.matches(Regex("dr[0-9]+")))
        val match = Regex("^\\s*${Regex.escape(callback)}\\s*\\(([\\s\\S]*)\\)\\s*;?\\s*$").matchEntire(body) ?: error("callback")
        JsonParser.parseString(match.groupValues[1]).asJsonObject
    } catch (_: Exception) { throw PortalException("校园会话接口格式异常，已停止操作") }

    fun parseStatus(body: String, callback: String, ip: String): CampusSession {
        try {
            val obj = json(body, callback)
            val result = obj.get("result")?.asString
            // This exact offline response was verified against the live school's endpoint.
            if (result == "0" && obj.get("msg")?.asString == "在线数据为空") return CampusSession(false)
            if (result != "1") throw PortalException("无法确认校园在线状态，请稍后重试")
            val list = obj.getAsJsonArray("list") ?: throw PortalException("校园会话缺少在线列表")
            val matches = list.map { it.asJsonObject }.filter { it.get("online_ip")?.asString == ip }
            if (matches.isEmpty()) return CampusSession(false)
            return CampusSession(true, matches.singleOrNull()?.get("user_account")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() })
        } catch (e: PortalException) { throw e }
        catch (_: Exception) { throw PortalException("校园在线列表格式异常，已停止操作") }
    }

    fun validateScripts(bootstrap: String, actions: String) {
        for (name in listOf("acLogout", "registerMode", "checkOnlineMethod")) {
            if (!Regex("\\bvar\\s+$name\\s*=\\s*1\\s*;").containsMatchIn(bootstrap))
                throw PortalException("校园会话协议配置已变化，请手动登录")
        }
        if (!Regex("\\bvar\\s+unBindmac\\s*=\\s*0\\s*;").containsMatchIn(bootstrap) ||
            !actions.contains("?c=Portal&a=logout") || !actions.contains("logout_portal: function"))
            throw PortalException("校园注销流程已变化，请手动登录")
    }
}
