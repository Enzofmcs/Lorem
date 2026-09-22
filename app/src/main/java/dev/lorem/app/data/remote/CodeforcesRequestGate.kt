package dev.lorem.app.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CodeforcesRequestGate {
    suspend fun <T> execute(request: suspend () -> T): T
}

class TwoSecondCodeforcesRequestGate(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : CodeforcesRequestGate {
    private val mutex = Mutex()
    private var lastStartedAt: Long? = null

    override suspend fun <T> execute(request: suspend () -> T): T = mutex.withLock {
        lastStartedAt?.let { previous ->
            val remaining = MINIMUM_INTERVAL_MILLIS - (nowMillis() - previous)
            if (remaining > 0) wait(remaining)
        }
        lastStartedAt = nowMillis()
        request()
    }

    companion object {
        const val MINIMUM_INTERVAL_MILLIS = 2_000L
    }
}
