package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test

class SessionProtocolTest {
    @Test fun logoutSerializationMatchesCapturedBrowserRequest() {
        val query = java.net.URI(SessionProtocol.logoutUrl(PortalContext("192.0.2.9"), "dr1001", 789)).rawQuery
        assertEquals("c=Portal&a=logout&callback=dr1001&login_method=1&user_account=drcom&user_password=123&ac_logout=1&register_mode=1&wlan_user_ip=192.0.2.9&wlan_user_ipv6=&wlan_vlan_id=1&wlan_user_mac=000000000000&wlan_ac_ip=&wlan_ac_name=&jsVersion=3.3.3&v=789", query)
    }

    private fun controllerPage(kind: Int) = """
        <!--Dr.COMWebLoginID_$kind.htm-->
        <html><head><title>注销页</title></head><body>
        <script>v4ip='192.0.2.9';uid='student0001';authsuccess='Dr.COMWebLoginID_3.htm';</script>
        <script src="a41.js?version=123"></script><script>
        page.run($kind);
        </script></body></html>
    """.trimIndent()

    @Test fun actualControllerLoggedInPageParsesWithoutLoginOnlyFields() {
        val html = controllerPage(1)
        assertTrue(SessionProtocol.isSuccessPage(html))
        assertFalse(SessionProtocol.isLogoutPage(html))
        assertEquals("192.0.2.9", PortalProtocol.parseContext(html, "192.0.2.9").ip)
        assertEquals(CampusSession(true), SessionProtocol.resolveStatus(CampusSession(false), SessionProtocol.isSuccessPage(html)))
    }
    @Test fun controllerLogoutPageIsNotAuthenticatedButHasFreshContext() {
        val html = controllerPage(2)
        assertFalse(SessionProtocol.isSuccessPage(html))
        assertTrue(SessionProtocol.isLogoutPage(html))
        assertEquals("192.0.2.9", PortalProtocol.parseContext(html, "192.0.2.9").ip)
    }
    @Test fun genericSuccessSettingAndWrongRunCannotImpersonateLoggedInPage() {
        assertFalse(SessionProtocol.isSuccessPage(controllerPage(1).replace("page.run(1);", "page.run(2);")))
        assertFalse(SessionProtocol.isSuccessPage(controllerPage(1).replace("<!--Dr.COMWebLoginID_1.htm-->", "")))
        try { PortalProtocol.parseContext(controllerPage(1), "192.0.2.8"); fail() } catch (_: PortalException) {}
    }

    @Test fun successPageOverridesIncorrectOfflineRadiusReply() {
        val merged = SessionProtocol.resolveStatus(CampusSession(false), true)
        assertTrue(merged.authenticated); assertNull(merged.account)
    }
    @Test fun logoutRequiresBothPageAndRadiusOffline() {
        assertFalse(SessionProtocol.resolveStatus(CampusSession(false), false).authenticated)
        assertTrue(SessionProtocol.resolveStatus(CampusSession(true, "student0001@telecom"), false).authenticated)
    }
    @Test fun pageDoesNotEraseReliableRadiusIdentity() {
        val state = CampusSession(true, "student0001@telecom")
        assertEquals(state, SessionProtocol.resolveStatus(state, true))
        assertTrue(SessionProtocol.isSuccessPage("<!-- Dr.COMWebLoginID_3.htm -->"))
        assertFalse(SessionProtocol.isSuccessPage("<title>上网登录页</title>"))
    }
    private val ip = "192.0.2.9"
    @Test fun matchesOnlyCurrentIpInOnlineList() {
        val body = """dr1001({"result":1,"list":[{"online_ip":"192.0.2.8","user_account":"other@unicom"},{"online_ip":"192.0.2.9","user_account":"student0001@telecom"}]});"""
        assertEquals(CampusSession(true, "student0001@telecom"), SessionProtocol.parseStatus(body, "dr1001", ip))
    }
    @Test fun offlineIsExplicit() {
        assertEquals(CampusSession(false), SessionProtocol.parseStatus("""dr1({"result":"0","msg":"在线数据为空"})""", "dr1", ip))
        assertEquals(CampusSession(false), SessionProtocol.parseStatus("""dr1({"result":1,"list":[]})""", "dr1", ip))
    }
    @Test fun unknownIdentityRemainsAuthenticated() {
        assertEquals(CampusSession(true), SessionProtocol.parseStatus("""dr1({"result":1,"list":[{"online_ip":"192.0.2.9"}]})""", "dr1", ip))
    }
    @Test fun malformedAndServerErrorsAreNotOffline() {
        for (body in listOf("dr2({})", "dr1({})", "dr1({\"result\":1})", "dr1({\"result\":0,\"msg\":\"failure\"})", "dr1({\"result\":1,\"list\":[1]})")) {
            try { SessionProtocol.parseStatus(body, "dr1", ip); fail(body) } catch (_: PortalException) {}
        }
    }
    @Test fun logoutUsesPublicPlaceholdersAndEncodesDynamicParameters() {
        val url = SessionProtocol.logoutUrl(PortalContext(ip, acName = "测试&+=", vlan = "12"), "dr1001", 900)
        assertTrue(url.contains("a=logout&")); assertTrue(url.contains("user_account=drcom&user_password=123"))
        assertTrue(url.contains("wlan_ac_name=%E6%B5%8B%E8%AF%95%26%2B%3D")); assertTrue(url.contains("wlan_vlan_id=12"))
        assertTrue(PortalProtocol.trusted(url))
    }
    @Test fun successPageUsesFreshV4Ip() {
        val html = """<!-- Dr.COMWebLoginID_3.htm --><script src="a41.js"></script><script>v4ip='192.0.2.9';</script>"""
        assertEquals(ip, PortalProtocol.parseContext(html, ip).ip)
        try { PortalProtocol.parseContext(html, "192.0.2.8"); fail() } catch (_: PortalException) {}
    }
    @Test fun changedLogoutSettingsFailClosed() {
        val bootstrap = "var acLogout=1;var registerMode=1;var checkOnlineMethod=1;var unBindmac=0;"
        val actions = "logout_portal: function(){}; '?c=Portal&a=logout'"
        SessionProtocol.validateScripts(bootstrap, actions)
        try { SessionProtocol.validateScripts(bootstrap.replace("unBindmac=0", "unBindmac=1"), actions); fail() } catch (_: PortalException) {}
    }
}
