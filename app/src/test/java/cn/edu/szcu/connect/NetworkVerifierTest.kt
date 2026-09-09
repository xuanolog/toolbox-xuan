package cn.edu.szcu.connect

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkVerifierTest {
    @Test fun overallVerificationTimeoutReturnsLimitedWithoutMoreProbes() = runTest {
        var probes = 0
        assertFalse(NetworkVerifier().verify({ probes++; delay(31000); true }, { true }, {}, {}))
        assertEquals(1, probes)
    }
    @Test fun requiresWifiProbeAndSession() = runTest {
        for (probe in listOf(false, true)) for (session in listOf(false, true)) {
            val result = NetworkVerifier().verify({ probe }, { session }, {}, {})
            assertEquals(probe && session, result)
        }
    }
    @Test fun delayedSessionIsRetriedWithoutLogin() = runTest {
        var checks = 0; var reevaluations = 0
        assertTrue(NetworkVerifier().verify({ true }, { ++checks >= 2 }, { reevaluations++ }, {}))
        assertEquals(2, checks); assertEquals(1, reevaluations)
    }
    @Test fun cancellationStopsFurtherProbes() = runTest {
        val started = CompletableDeferred<Unit>(); var probes = 0
        val job = launch { NetworkVerifier().verify({ probes++; started.complete(Unit); awaitCancellation() }, { true }, {}, {}) }
        started.await(); job.cancelAndJoin(); assertEquals(1, probes)
    }
    @Test fun lostNetworkFailsBeforeProbe() = runTest {
        var probes = 0
        try { NetworkVerifier().verify({ probes++; true }, { true }, {}, { throw PortalException("断开") }); fail() }
        catch (_: PortalException) {}
        assertEquals(0, probes)
    }
}
