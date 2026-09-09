package cn.edu.szcu.connect

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class ConnectionEngineTest {
    private val profile = Profile(name = "测试", carrier = Carrier.TELECOM, account = "student0001", password = "test-only")
    private open class FakeSession : PortalSession {
        val events = mutableListOf<String>()
        var submissions = 0
        var state = CampusSession(false)
        var finalOnline = true
        var connected = true
        var reply = PortalReply(true, "ok")
        override suspend fun online() = true
        override suspend fun verify(): Boolean { events += "verify"; return finalOnline }
        override suspend fun inspect(): CampusSession { events += "inspect"; return state }
        override suspend fun logout() { events += "logout"; state = CampusSession(false) }
        override suspend fun readContext(): PortalContext { events += "context"; return PortalContext("192.0.2.9") }
        override suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply { events += "login"; submissions++; return reply }
        override fun checkNetwork() { if (!connected) throw PortalException("网络已切换") }
    }
    @Test fun reachableProbeNeverBypassesAuthentication() = runTest {
        val fake = FakeSession(); var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(listOf("inspect", "context", "login", "verify"), fake.events)
        assertEquals(Stage.CONNECTED, last)
    }
    @Test fun sameAccountSkipsLogoutAndLogin() = runTest {
        val fake = FakeSession().apply { state = CampusSession(true, ",1,student0001@telecom") }; var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(listOf("inspect", "verify"), fake.events); assertEquals(Stage.ALREADY, last)
    }
    @Test fun differentOrUnknownAccountLogsOutThenReadsFreshContext() = runTest {
        for (account in listOf(null, "other@telecom", "student0001@unicom")) {
            val fake = FakeSession().apply { state = CampusSession(true, account) }
            ConnectionEngine().run(profile, fake) {}
            assertEquals(listOf("inspect", "logout", "inspect", "context", "login", "verify"), fake.events)
        }
    }
    @Test fun unsuccessfulOrUnconfirmedLogoutNeverSubmits() = runTest {
        for (throwError in listOf(true, false)) {
            val fake = object : FakeSession() { override suspend fun logout() { if (throwError) throw PortalException("注销失败") } }.apply { state = CampusSession(true) }
            try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: PortalException) {}
            assertEquals(0, fake.submissions)
        }
    }
    @Test fun campusSkipsExternalVerification() = runTest {
        val fake = FakeSession(); var last = Stage.IDLE
        ConnectionEngine().run(profile.copy(carrier = Carrier.CAMPUS), fake) { last = it.stage }
        assertEquals(Stage.CAMPUS, last); assertFalse(fake.events.contains("verify"))
    }
    @Test fun unverifiedNetworkIsLimited() = runTest {
        val fake = FakeSession().apply { finalOnline = false }; var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(Stage.LIMITED, last); assertEquals(1, fake.submissions)
    }
    @Test fun rejectionNeverRetries() = runTest {
        val fake = FakeSession().apply { reply = PortalReply(false, "拒绝") }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: PortalException) {}
        assertEquals(1, fake.submissions)
    }
    @Test fun cancelDuringReadOrLogoutNeverSubmits() = runTest {
        for (duringLogout in listOf(true, false)) {
            val started = CompletableDeferred<Unit>()
            val fake = object : FakeSession() {
                override suspend fun logout() { started.complete(Unit); awaitCancellation() }
                override suspend fun readContext(): PortalContext { started.complete(Unit); awaitCancellation() }
            }.apply { state = CampusSession(duringLogout) }
            val job = launch { ConnectionEngine().run(profile, fake) {} }
            started.await(); job.cancelAndJoin(); assertEquals(0, fake.submissions)
        }
    }
    @Test fun changedNetworkBeforeLoginNeverSubmits() = runTest {
        val fake = object : FakeSession() { override suspend fun readContext(): PortalContext { connected = false; return PortalContext("192.0.2.9") } }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: PortalException) {}
        assertEquals(0, fake.submissions)
    }
    @Test fun timeoutNeverRetriesPassword() = runTest {
        val fake = object : FakeSession() { override suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply { submissions++; throw SocketTimeoutException() } }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: SocketTimeoutException) {}
        assertEquals(1, fake.submissions)
    }
}
