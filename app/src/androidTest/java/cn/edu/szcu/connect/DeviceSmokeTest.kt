package cn.edu.szcu.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    /** Read-only comparison of cookie continuity across native request headers. */
    @Test fun cookieContinuityDiagnostic() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("cookieProbe") == "true")
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await("SZCU-313-5G", 30000) ?: error("Wi-Fi unavailable")
            for (customHeaders in listOf(false, true)) {
                val cookies = PortalCookies()
                repeat(2) { step ->
                    val url = "http://172.16.8.22:801/eportal/?c=Portal&a=page_type_data&callback=dr1000"
                    val c = network.openConnection(java.net.URL(url), java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                    try {
                        c.connectTimeout = 5000; c.readTimeout = 5000; c.instanceFollowRedirects = false; c.useCaches = false
                        if (customHeaders) {
                            c.setRequestProperty("Cache-Control", "no-store")
                            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Mobile) SZCUConnect/0.1")
                            c.setRequestProperty("Referer", PortalProtocol.ROOT)
                        }
                        val before = cookies.headers(url)
                        before.forEach { (k, v) -> c.setRequestProperty(k, v.joinToString("; ")) }
                        val code = c.responseCode
                        cookies.receive(url, c.headerFields)
                        c.inputStream.use { it.readBytes() }
                        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                            putString("stream", "COOKIE_CHECK customHeaders=$customHeaders step=$step http=$code changed=${before != cookies.headers(url)}\n")
                        })
                    } finally { c.disconnect() }
                }
            }
        } finally { wifi.close() }
    }

    @Test fun logoutResponseDiagnostic() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("logoutDiagnostic") == "true")
        val withBootstrap = InstrumentationRegistry.getArguments().getString("cookieBootstrap") == "true"
        val cookies = java.net.CookieManager()
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await("SZCU-313-5G", 30000) ?: error("Wi-Fi unavailable")
            fun get(url: String): String {
                val c = network.openConnection(java.net.URL(url), java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                try {
                    c.connectTimeout = 5000; c.readTimeout = 5000; c.instanceFollowRedirects = false; c.useCaches = false
                    if (withBootstrap) cookies.get(java.net.URI(url), emptyMap()).forEach { (k, v) -> c.setRequestProperty(k, v.joinToString("; ")) }
                    c.responseCode
                    if (withBootstrap) cookies.put(java.net.URI(url), c.headerFields)
                    return PortalEncoding.decode(c.inputStream.use { it.readBytes() }, c.contentType, true)
                } finally { c.disconnect() }
            }
            val root = get(PortalProtocol.ROOT)
            val ctx = PortalProtocol.parseContext(root, wifi.ip(network)!!, get(PortalProtocol.ROOT + "a41.js"))
            if (withBootstrap) get("http://172.16.8.22:801/eportal/?c=Portal&a=page_type_data&callback=dr1000")
            if (InstrumentationRegistry.getArguments().getString("statusBefore") == "true")
                get(SessionProtocol.statusUrl(ctx, "dr1002", 788))
            val body = get(SessionProtocol.logoutUrl(ctx, "dr1001", 789))
            val obj = com.google.gson.JsonParser.parseString(body.substringAfter('(').substringBeforeLast(')')).asJsonObject
            val msg = obj.get("msg")?.asString.orEmpty()
            val safeMessage = msg.takeIf { it.length < 100 && it.matches(Regex("[\\p{IsHan}，。；：！、（）\\s]+")) } ?: "<redacted>"
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "LOGOUT_RESPONSE success=${obj.get("result")?.asString in listOf("1", "ok")} zeroMac=${ctx.mac == "000000000000"} cookieBootstrap=$withBootstrap cookieCount=${cookies.cookieStore.cookies.size} message=$safeMessage\n")
            })
        } finally { wifi.close() }
    }

    /** Opt-in controller check. Logs out once, never reads saved credentials or submits login. */
    @Test fun logoutControllerCheck() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("logoutProbe") == "true")
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await("SZCU-313-5G", 30000) ?: error("Wi-Fi unavailable")
            val session = NetworkPortalSession(network, "SZCU-313-5G", wifi)
            session.inspect() // Fetch only terminal context; the school's online list may be empty while online.
            session.logout()
            assertFalse("Controller must show offline after logout", session.inspect().authenticated)
            assertEquals(wifi.ip(network), session.readContext().ip)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "LOGOUT_CHECK confirmedOffline=true returnedToLogin=true\n")
            })
        } finally { wifi.close() }
    }

    @Test fun sessionSourcesReadOnly() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sourcesProbe") == "true")
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await("SZCU-313-5G", 30000) ?: error("Wi-Fi unavailable")
            fun get(url: String): String {
                val c = network.openConnection(java.net.URL(url), java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                try {
                    c.connectTimeout = 5000; c.readTimeout = 5000; c.useCaches = false; c.instanceFollowRedirects = false
                    return PortalEncoding.decode(c.inputStream.use { it.readBytes() }, c.contentType, true)
                } finally { c.disconnect() }
            }
            val root = get(PortalProtocol.ROOT)
            val ctx = PortalProtocol.parseContext(root, wifi.ip(network)!!)
            val summaries = mutableListOf("successMarker=${SessionProtocol.isSuccessPage(root)} logoutTitle=${org.jsoup.Jsoup.parse(root).title().contains("注销")} loginTitle=${org.jsoup.Jsoup.parse(root).title().contains("登录")}")
            for ((name, url) in listOf("radius" to SessionProtocol.statusUrl(ctx, "dr1001", 777), "kernel" to "http://172.16.8.22/drcom/chkstatus?callback=dr1001")) {
                val body = get(url)
                val obj = com.google.gson.JsonParser.parseString(body.substringAfter('(').substringBeforeLast(')')).asJsonObject
                val list = obj.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
                summaries += "$name result=${obj.get("result")?.asString?.takeIf { it in listOf("0", "1", "ok") }} listCount=${list?.size()} matched=${list?.count { it.asJsonObject.get("online_ip")?.asString == ctx.ip }} uidPresent=${obj.has("uid")} offlineMessage=${obj.get("msg")?.asString == "在线数据为空"}"
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", summaries.joinToString("\n") + "\n") })
        } finally { wifi.close() }
    }

    /** No login/logout. Emits booleans only and asks Android to reevaluate connectivity. */
    @Test fun campusSessionReadOnly() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit session probe not requested", args.getString("sessionProbe") == "true")
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await("SZCU-313-5G", 30000) ?: error("Target Wi-Fi unavailable or permission missing")
            val session = NetworkPortalSession(network, "SZCU-313-5G", wifi)
            val state = session.inspect()
            val reachable = session.online()
            val verified = session.verify()
            val suggestionsCleared = !wifi.hasLegacySuggestions()
            val defaultCaps = wifi.connectivity.getNetworkCapabilities(wifi.connectivity.activeNetwork)
            val result = android.os.Bundle().apply {
                putString("stream", "SESSION_PROBE authenticated=${state.authenticated} identityAvailable=${state.account != null} wifiProbe=$reachable verified=$verified suggestionsCleared=$suggestionsCleared wifiValidated=${wifi.validated(network)} defaultCellular=${defaultCaps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true}\n")
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, result)
        } finally { wifi.close() }
    }

    @Test fun keystoreAndMultipleProfilesPersistWithoutPlaintext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "smoke-test.vault"
        val alias = "szcu.smoke.test"
        val store = ProfileStore(context, fileName, alias)
        val profile = Profile(name = "仅测试", carrier = Carrier.UNICOM, account = "student0001", password = "test-only-a&b=+")
        try {
            val book = ProfileBook(selectedId = profile.id, profiles = listOf(profile, profile.copy(id = "second", carrier = Carrier.CAMPUS)))
            store.write(book)
            assertEquals(book, ProfileStore(context, fileName, alias).read())
            val disk = File(context.noBackupFilesDir, fileName).readText(Charsets.ISO_8859_1)
            assertFalse(disk.contains(profile.account)); assertFalse(disk.contains(profile.password))
            store.write(book.copy(profiles = listOf(profile)))
            assertEquals(1, store.read().profiles.size)
        } finally {
            File(context.noBackupFilesDir, fileName).delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    /** Opt-in read-only real-network check: never calls authenticate and never touches profiles. */
    @Test fun campusPortalReadOnly() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit campus probe not requested", args.getString("campusProbe") == "true")
        val ssid = args.getString("campusSsid") ?: "SZCU-313-5G"
        val wifi = WifiConnector(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            wifi.refresh()
            val network = wifi.await(ssid, 30000) ?: error("Target Wi-Fi unavailable or permission missing")
            val ctx = NetworkPortalSession(network, ssid, wifi).readContext()
            assertEquals(wifi.ip(network), ctx.ip)
            assertTrue(ctx.jsVersion.isNotEmpty())
        } finally { wifi.close() }
    }
}
