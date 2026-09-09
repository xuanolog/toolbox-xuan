package cn.edu.szcu.connect

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class ConnectionEngineTest {
    private val profile = Profile(name = "测试", carrier = Carrier.TELECOM, account = "student0001", password = "test-only")
    private open class FakeSession : PortalSession {
        var submissions = 0
        var checks = 0
        var initialOnline = false
        var finalOnline = true
        var connected = true
        var reply = PortalReply(true, "ok")
        override suspend fun online(): Boolean = if (checks++ == 0) initialOnline else finalOnline
        override suspend fun readContext() = PortalContext("192.0.2.9")
        override suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply { submissions++; return reply }
        override fun checkNetwork() { if (!connected) throw PortalException("网络已切换") }
    }
    @Test fun fullSuccess() = runTest {
        val fake = FakeSession(); val stages = mutableListOf<Stage>()
        ConnectionEngine().run(profile, fake) { stages += it.stage }
        assertEquals(1, fake.submissions); assertEquals(Stage.CONNECTED, stages.last())
    }
    @Test fun alreadyOnlineNeverSubmitsSelectedAccount() = runTest {
        val fake = FakeSession().apply { initialOnline = true }; var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(0, fake.submissions); assertEquals(Stage.ALREADY, last)
    }
    @Test fun campusDoesNotRequireExternalInternet() = runTest {
        val fake = FakeSession().apply { finalOnline = false }; var last = Stage.IDLE
        ConnectionEngine().run(profile.copy(carrier = Carrier.CAMPUS), fake) { last = it.stage }
        assertEquals(Stage.CAMPUS, last); assertEquals(1, fake.checks)
    }
    @Test fun existingPortalSessionDoesNotResubmitEvenWithoutInternet() = runTest {
        val fake = object : FakeSession() { override suspend fun readContext(): PortalContext { throw ExistingPortalSession() } }
        var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(Stage.ALREADY, last); assertEquals(0, fake.submissions)
    }
    @Test fun authenticatedButOfflineIsLimited() = runTest {
        val fake = FakeSession().apply { finalOnline = false }; var last = Stage.IDLE
        ConnectionEngine().run(profile, fake) { last = it.stage }
        assertEquals(Stage.LIMITED, last); assertEquals(1, fake.submissions)
    }
    @Test fun rejectionDoesNotRetry() = runTest {
        val fake = FakeSession().apply { reply = PortalReply(false, "拒绝") }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: PortalException) { }
        assertEquals(1, fake.submissions)
    }
    @Test fun cancelDuringReadNeverSubmits() = runTest {
        val started = CompletableDeferred<Unit>()
        val fake = object : FakeSession() { override suspend fun readContext(): PortalContext { started.complete(Unit); awaitCancellation() } }
        val job = launch { ConnectionEngine().run(profile, fake) {} }
        started.await(); job.cancelAndJoin(); assertEquals(0, fake.submissions)
    }
    @Test fun changedNetworkBeforeLoginNeverSubmits() = runTest {
        val fake = object : FakeSession() { override suspend fun readContext(): PortalContext { connected = false; return PortalContext("192.0.2.9") } }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: PortalException) { }
        assertEquals(0, fake.submissions)
    }
    @Test fun timeoutDoesNotRetryPassword() = runTest {
        val fake = object : FakeSession() { override suspend fun authenticate(profile: Profile, ctx: PortalContext): PortalReply { submissions++; throw SocketTimeoutException() } }
        try { ConnectionEngine().run(profile, fake) {}; fail("should fail") } catch (_: SocketTimeoutException) { }
        assertEquals(1, fake.submissions)
    }
}
