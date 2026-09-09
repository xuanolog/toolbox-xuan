package cn.edu.szcu.connect

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class PortalEncodingTest {
    private val chinese = "中国电信 中国移动 中国联通 校园内网"
    @Test fun portalScriptWithoutCharsetInheritsGb2312() {
        assertEquals(chinese, PortalEncoding.decode(chinese.toByteArray(Charset.forName("GB2312")), "application/javascript", true))
    }
    @Test fun explicitUtf8OverridesPortalDefault() {
        assertEquals(chinese, PortalEncoding.decode(chinese.toByteArray(), "text/javascript; charset=utf-8", true))
    }
    @Test fun metaCharsetIsRespected() {
        val html = "<meta charset=\"utf-8\">$chinese"
        assertEquals(html, PortalEncoding.decode(html.toByteArray(), "text/html", true))
    }
}
