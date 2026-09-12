package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test

class PortalCookiesTest {
    private val school = "http://172.16.8.22:801/eportal/"
    @Test fun preservesHttpOnlyPhpSessionAcrossSchoolRequests() {
        val cookies = PortalCookies()
        cookies.receive(school, mapOf("Set-Cookie" to listOf("PHPSESSID=fake-test-session; Path=/; HttpOnly")))
        assertTrue(cookies.headers(school + "?c=Portal&a=logout").values.flatten().joinToString().contains("fake-test-session"))
        assertTrue(cookies.headers(PortalProtocol.RETURN).values.flatten().joinToString().contains("fake-test-session"))
    }
    @Test fun neverSendsSchoolSessionToExternalProbes() {
        val cookies = PortalCookies()
        cookies.receive(school, mapOf("Set-Cookie" to listOf("PHPSESSID=fake-test-session; Path=/")))
        assertTrue(cookies.headers("https://www.baidu.com/").isEmpty())
        assertTrue(cookies.headers("http://172.16.8.22:9000/").isEmpty())
    }
    @Test fun rejectsExternalCookieAndKeepsOperationsIsolated() {
        val cookies = PortalCookies()
        cookies.receive("https://www.baidu.com/", mapOf("Set-Cookie" to listOf("PHPSESSID=external; Domain=172.16.8.22; Path=/")))
        assertFalse(cookies.headers(school).values.flatten().joinToString().contains("external"))
        cookies.receive(school, mapOf("Set-Cookie" to listOf("PHPSESSID=fake-test-session; Path=/")))
        assertFalse(PortalCookies().headers(school).values.flatten().joinToString().contains("fake-test-session"))
    }
}
