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
