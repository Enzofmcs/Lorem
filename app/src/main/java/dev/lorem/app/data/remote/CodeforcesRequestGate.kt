package dev.lorem.app.data.remote

/**
 * Integration point for future Codeforces calls.
 *
 * A real implementation must serialize requests and leave at least two seconds between them,
 * respecting the public API limit. Story 00 intentionally performs no network requests.
 */
interface CodeforcesRequestGate {
    suspend fun <T> execute(request: suspend () -> T): T
}
