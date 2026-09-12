package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class PortalProtocolTest {
    private fun profile(carrier: Carrier = Carrier.TELECOM, password: String = "test-only") =
        Profile(name = "测试配置", carrier = carrier, account = "student0001", password = password)
    private val html = """<html><script>v4serip='172.16.8.22';v46ip='192.0.2.9   ';ss4='000000000000';myv6ip=' ';document.write('a41.js');</script></html>"""
    @Test fun allFourCarrierMappings() {
        val suffixes = listOf("@telecom", "@cmcc", "@unicom", "")
        Carrier.entries.zip(suffixes).forEach { (carrier, suffix) -> assertEquals(",1,student0001$suffix", PortalProtocol.account(profile(carrier))) }
    }
    @Test fun passwordRoundTripWithReservedCharacters() {
        val p = profile(password = "a&b=+%?# 中文'\"\\")
        val url = PortalProtocol.loginUrl(p, PortalContext("192.0.2.9"), "dr1234", 999)
        val query = URI(url).rawQuery.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals(p.password, query["user_password"])
        assertEquals(",1,student0001@telecom", query["user_account"])
        assertEquals("192.0.2.9", query["wlan_user_ip"])
        assertEquals("1", query["login_method"])
        assertNull(URI(url).fragment)
    }
    @Test fun extractsCurrentIpAndScriptVersion() {
        val ctx = PortalProtocol.parseContext(html, "192.0.2.9", "var jsVersion='3.3.3';")
        assertEquals("192.0.2.9", ctx.ip); assertEquals("3.3.3", ctx.jsVersion)
    }
    @Test fun rejectsStaleIp() { assertThrows(PortalException::class.java) { PortalProtocol.parseContext(html, "192.0.2.10") } }
    @Test fun rejectsUnrelatedPage() { assertThrows(PortalException::class.java) { PortalProtocol.parseContext("<html>Baidu</html>", "192.0.2.9") } }
    @Test fun visibleCaptchaRequiresManualLogin() { assertThrows(PortalException::class.java) { PortalProtocol.parseContext(html + "<input name='captcha'>", "192.0.2.9") } }
    @Test fun hiddenCaptchaDoesNotBlock() { PortalProtocol.parseContext(html + "<input name='captcha' style='display: none'>", "192.0.2.9") }
    @Test fun parsesNumericSuccess() { assertTrue(PortalProtocol.parseReply("dr123({\"result\":1});", "dr123").success) }
    @Test fun parsesStringSuccess() { assertTrue(PortalProtocol.parseReply(" dr123({\"result\":\"ok\"}) ", "dr123").success) }
    @Test fun rejectsWrongCallback() { assertThrows(PortalException::class.java) { PortalProtocol.parseReply("evil({\"result\":1})", "dr123") } }
    @Test fun refusesExecutableSuffix() { assertThrows(PortalException::class.java) { PortalProtocol.parseReply("dr123({\"result\":1});alert(1)", "dr123") } }
    @Test fun refusesMalformedResponse() { assertThrows(PortalException::class.java) { PortalProtocol.parseReply("dr123({broken})", "dr123") } }
    @Test fun failureDoesNotEchoCredentials() {
        val reply = PortalProtocol.parseReply("dr123({\"result\":0,\"msg\":\"student0001 password=test-only\"})", "dr123")
        assertFalse(reply.success); assertFalse(reply.message.contains("test-only")); assertFalse(reply.message.contains("student0001"))
    }
    @Test fun trustedHostRequiresExactAuthority() {
        assertTrue(PortalProtocol.trusted(PortalProtocol.ENDPOINT))
        listOf("http://172.16.8.22.evil.test/", "http://172.16.8.22@evil.test/", "http://172.16.8.22:9000/", "https://evil.test/", "file:///tmp/x").forEach { assertFalse(PortalProtocol.trusted(it)) }
    }
    @Test fun refusesSuffixedAccountAndInvalidSsid() {
        assertThrows(IllegalArgumentException::class.java) { profile().copy(account = "student0001@telecom").validate() }
        assertThrows(IllegalArgumentException::class.java) { profile().copy(ssid = "校".repeat(11)).validate() }
    }
    @Test fun profileToStringIsRedacted() { assertFalse(profile().toString().contains("student0001")); assertFalse(profile().toString().contains("test-only")) }
}
