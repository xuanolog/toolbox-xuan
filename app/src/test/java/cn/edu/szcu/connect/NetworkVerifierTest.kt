package cn.edu.szcu.connect

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkVerifierTest {
    @Test fun overallVerificationTimeoutReturnsLimitedWithoutMoreProbes() = runTest {
        var probes = 0
        assertFalse(NetworkVerifier().verify({ probes++; delay(31000); true }, { true }, { true }, {}, {}))
        assertEquals(1, probes)
    }
    @Test fun requiresWifiProbeSessionAndSystemValidation() = runTest {
        for (probe in listOf(false, true)) for (session in listOf(false, true)) for (system in listOf(false, true)) {
            val result = NetworkVerifier().verify({ probe }, { session }, { system }, {}, {})
            assertEquals(probe && session && system, result)
        }
    }
    @Test fun delayedSystemValidationIsRetriedWithoutLogin() = runTest {
        var checks = 0; var reevaluations = 0
        assertTrue(NetworkVerifier().verify({ true }, { true }, { ++checks >= 2 }, { reevaluations++ }, {}))
        assertEquals(2, checks); assertEquals(1, reevaluations)
    }
    @Test fun cancellationStopsFurtherProbes() = runTest {
        val started = CompletableDeferred<Unit>(); var probes = 0
        val job = launch { NetworkVerifier().verify({ probes++; started.complete(Unit); awaitCancellation() }, { true }, { true }, {}, {}) }
        started.await(); job.cancelAndJoin(); assertEquals(1, probes)
    }
    @Test fun lostNetworkFailsBeforeProbe() = runTest {
        var probes = 0
        try { NetworkVerifier().verify({ probes++; true }, { true }, { true }, {}, { throw PortalException("断开") }); fail() }
        catch (_: PortalException) {}
        assertEquals(0, probes)
    }
}
