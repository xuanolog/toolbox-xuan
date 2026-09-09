package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test

class PortalTemplateTest {
    private val config = """var pageSetting=2;var ipPageAry=new Array(3);
        ipPageAry[0]="default|0.0.0.0|255.255.255.255|||1|0|";
        ipPageAry[1]="old|0.0.0.0|255.255.255.255|||0|1|admin";
        ipPageAry[2]="active|192.0.2.0|192.0.2.255|||1|1|admin";"""
    private val bootstrap = "var jsVersion='3.3.3';var accountPrefix=1;var customPerceive=0;var eportalv6=0;var page={name:'WZXY'};"
    private val loginbox = "var password_cut=0;var ipv6_ipv4=0;"
    private val mobile = """var bodyContent='<form name="f1"><input name="DDDDD"><input name="upass" type="password"><input name="captcha" style="display: none"><select name="ISP_select"><option value="@telecom">中国电信</option><option value="@cmcc">中国移动</option><option value="@unicom">中国联通</option><option value="">校园内网</option></select></form>';"""
    @Test fun selectsEnabledIpTemplate() { assertEquals(2, PortalTemplate.index(config, "192.0.2.9")) }
    @Test fun rejectsUnmatchedIp() { assertThrows(PortalException::class.java) { PortalTemplate.index(config, "198.51.100.1") } }
    @Test fun rejectsDifferentLoginMethod() { assertThrows(PortalException::class.java) { PortalTemplate.index(config.replace("|||1|1|", "|||1|3|"), "192.0.2.9") } }
    @Test fun verifiesInspectedRules() { assertEquals("3.3.3", PortalTemplate.validate(bootstrap, loginbox, mobile)) }
    @Test fun rejectsChangedCarrierValues() { assertThrows(PortalException::class.java) { PortalTemplate.validate(bootstrap, loginbox, mobile.replace("@telecom", "@dx")) } }
    @Test fun rejectsVisibleCaptcha() { assertThrows(PortalException::class.java) { PortalTemplate.validate(bootstrap, loginbox, mobile.replace("display: none", "display: block")) } }
    @Test fun rejectsPasswordTransformation() { assertThrows(PortalException::class.java) { PortalTemplate.validate(bootstrap, loginbox.replace("password_cut=0", "password_cut=1"), mobile) } }
    @Test fun rejectsChangedPrefix() { assertThrows(PortalException::class.java) { PortalTemplate.validate(bootstrap.replace("accountPrefix=1", "accountPrefix=0"), loginbox, mobile) } }
}
