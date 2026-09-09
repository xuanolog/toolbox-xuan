package cn.edu.szcu.connect

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** All callbacks are scoped to the same Wi-Fi Network; never inspect the default network. */
class NetworkVerifier {
    suspend fun verify(
        probe: suspend () -> Boolean,
        authenticated: suspend () -> Boolean,
        validated: () -> Boolean,
        reevaluate: (Boolean) -> Unit,
        checkNetwork: () -> Unit,
    ): Boolean = withTimeoutOrNull(30_000) {
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            checkNetwork()
            val reachable = probe()
            currentCoroutineContext().ensureActive()
            checkNetwork()
            if (attempt == 0) reevaluate(reachable)
            val sessionConfirmed = if (reachable) authenticated() else false
            currentCoroutineContext().ensureActive()
            checkNetwork()
            if (reachable && sessionConfirmed && validated()) return@withTimeoutOrNull true
            if (attempt < 2) delay(2000)
        }
        false
    } ?: false
}
