package dev.lorem.app.data.remote

import dev.lorem.app.domain.sync.SynchronizationStore
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Global and durable: a process restart or a second dataset cannot bypass Retry-After. */
class CodeforcesRetryAfter(
    private val store: SynchronizationStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()

    suspend fun record(header: String?) {
        val value = header?.trim() ?: return
        val deadline = value.toLongOrNull()?.takeIf { it >= 0 }?.let {
            nowMillis() + it.coerceAtMost((Long.MAX_VALUE - nowMillis()) / 1000) * 1000
        } ?: runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: return
        mutex.withLock {
            val state = store.read(KEY)
            store.write(KEY, state.copy(retryNotBeforeEpochMillis = maxOf(state.retryNotBeforeEpochMillis, deadline)))
        }
    }

    suspend fun isDeferred(): Boolean = store.read(KEY).retryNotBeforeEpochMillis > nowMillis()

    /** Called inside the same request gate, before measuring the two-second interval. */
    suspend fun awaitPermission() {
        while (true) {
            val remaining = store.read(KEY).retryNotBeforeEpochMillis - nowMillis()
            if (remaining <= 0) return
            wait(remaining)
        }
    }

    private companion object { const val KEY = "api-retry" }
}
