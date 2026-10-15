package dev.lorem.app.domain.sync

import dev.lorem.app.domain.VerifyIpsumResult
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One visible session per ViewModel; callers bind its lifetime to RESUMED. */
class IpsumSubmissionMonitor(
    private val repository: LoremRepository,
    private val coordinator: SynchronizationCoordinator,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val visibleSession = Mutex()

    suspend fun monitor(ipsumId: Long) = visibleSession.withLock {
        val link = repository.profileLink.first() ?: return@withLock
        if (repository.activeIpsum.first()?.id != ipsumId) return@withLock
        coroutineScope {
            val polling = launch {
                var interval = INTERVAL_MILLIS
                try {
                    while (isActive) {
                        val result = coordinator.verifyActiveIpsum(link, ipsumId)
                        if (result == VerifyIpsumResult.NoActiveIpsum || result is VerifyIpsumResult.Success && result.completed) break
                        interval = if (result is VerifyIpsumResult.Success) INTERVAL_MILLIS
                            else (interval * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                        wait(interval)
                    }
                } catch (_: ProfileLinkChanged) { /* The next visible session belongs to the new link. */ }
            }
            val ended = launch {
                combine(repository.profileLink, repository.activeIpsum) { current, active ->
                    current != link || active?.id != ipsumId
                }.first { it }
                polling.cancel()
            }
            try { polling.join() } finally { ended.cancelAndJoin() }
        }
    }

    companion object {
        const val INTERVAL_MILLIS = 15_000L
        const val MAX_BACKOFF_MILLIS = 120_000L
    }
}
