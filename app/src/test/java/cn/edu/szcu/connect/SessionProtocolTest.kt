package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test

class SessionProtocolTest {
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
