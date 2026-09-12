package cn.edu.szcu.connect

import org.jsoup.Jsoup

/** Narrow, non-executing parser for the inspected WZXY template family. */
object PortalTemplate {
    fun number(source: String, name: String): Int? = Regex("(?<![A-Za-z0-9_$])${Regex.escape(name)}\\s*=\\s*([0-9]+)").find(source)?.groupValues?.get(1)?.toIntOrNull()
    private fun unsupported(): Nothing = throw PortalException("校园认证模板或规则已变化，请打开登录页手动认证")
    fun index(config: String, ip: String): Int {
        if (number(config, "pageSetting") != 2) unsupported()
        fun numeric(ip: String): Long {
            val parts = ip.split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) unsupported()
            return parts.fold(0L) { n, part -> (n shl 8) + part.toInt() }
        }
        val current = numeric(ip)
        val entries = Regex("ipPageAry\\[([0-9]+)]\\s*=\\s*['\"]([^'\"]*)['\"]").findAll(config)
            .map { it.groupValues[1].toInt() to it.groupValues[2] }.toList().sortedBy { it.first }
        if (entries.isEmpty()) unsupported()
        for ((index, data) in entries) {
            if (index == 0 || data.isEmpty()) continue
            val parts = data.split('|')
            if (parts.size != 8) unsupported()
            if (parts[5] != "1") continue
            val starts = parts[1].split(';'); val ends = parts[2].split(';')
            if (starts.size != ends.size) unsupported()
            if (starts.zip(ends).any { (a, b) -> current in numeric(a)..numeric(b) }) {
                if (parts[6] != "1") unsupported()
                return index
            }
        }
        unsupported()
    }
    fun validate(bootstrap: String, loginbox: String, mobile: String): String {
        if (PortalProtocol.literal(bootstrap, "name") != "WZXY" || number(bootstrap, "accountPrefix") != 1 ||
            number(bootstrap, "customPerceive") != 0 || number(bootstrap, "eportalv6") != 0 ||
            number(loginbox, "password_cut") != 0 || number(loginbox, "ipv6_ipv4") != 0) unsupported()
        val version = PortalProtocol.literal(bootstrap, "jsVersion") ?: unsupported()
        if (version != "3.3.3") unsupported()
        val html = Regex("(?s)\\bbodyContent\\s*=\\s*'((?:\\\\.|[^'\\\\])*)'").find(mobile)?.groupValues?.get(1) ?: unsupported()
        // This template only uses quoted HTML; decode string escapes, without evaluating expressions.
        val decoded = Regex("\\\\([\\\\'\"nrt])").replace(html) { match -> when (match.groupValues[1]) {
            "n" -> "\n"; "r" -> "\r"; "t" -> "\t"; else -> match.groupValues[1]
        } }
        val doc = Jsoup.parse(decoded)
        val form = doc.selectFirst("form[name=f1]") ?: unsupported()
        if (form.selectFirst("input[name=DDDDD]") == null || form.selectFirst("input[name=upass][type=password]") == null) unsupported()
        val options = doc.select("select[name=ISP_select] option").associate { it.text() to it.attr("value") }
        if (Carrier.entries.any { options[it.label] != it.suffix }) unsupported()
        if (doc.select("input[name=captcha]").any { !Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE).containsMatchIn(it.attr("style")) })
            throw PortalException("校园网要求验证码，请打开登录页手动认证")
        return version
    }
}
