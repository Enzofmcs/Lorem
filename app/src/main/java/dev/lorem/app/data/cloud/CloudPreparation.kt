package dev.lorem.app.data.cloud

import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*

/** Public CF data only. Never substitutes an incomplete/stale scan for a verified history.
 * Owned by the repository's mutex, shared by UI and WorkManager. Cache loss costs requests only.
 */
class CloudPreparation(private val cf: CodeforcesRepository,
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val pageSize: Int = 1000, private val pageBudget: Int = 8,
) {
    data class Metrics(val pages: Int, val catalogLoads: Int, val profileLoads: Int,
        val elapsedMillis: Long, val incremental: Boolean, val completed: Boolean)
    data class Prepared(val history: List<CodeforcesSubmission>, val catalog: List<CodeforcesProblem>, val user: CodeforcesUser)
    private data class Complete(val rows: List<CodeforcesSubmission>, val at: Long)
    private data class Scan(val base: Complete?, val rows: MutableMap<Long, CodeforcesSubmission> = linkedMapOf(),
        var from: Int = 1, var overlap: Set<Long> = emptySet())
    private val complete = mutableMapOf<String, Complete>()
    private val scans = mutableMapOf<String, Scan>()
    private val windows = mutableMapOf<String, Scan>()
    private var catalog: Pair<Long, List<CodeforcesProblem>>? = null
    var lastMetrics: Metrics? = null; private set

    suspend fun prepare(context: String, handle: String, validate: () -> Unit,
        progress: (CloudPreparationProgress) -> Unit): Prepared {
        val began = now()
        var pages = 0; var catalogs = 0; var profiles = 0; var done = false
        val incremental = complete[context]?.let { now() - it.at in 0..CACHE_MILLIS } == true
        try {
            progress(CloudPreparationProgress(CloudPreparationStage.HISTORY, "Verificando cobertura e novas submissões…"))
            val history = history(context, handle, validate) { kotlin.check(pages < pageBudget) { "Limite de páginas atingido. Tente novamente para continuar." }; pages++; progress(CloudPreparationProgress(
                CloudPreparationStage.HISTORY, "Página $pages de até $pageBudget nesta etapa")) }
            validate()
            progress(CloudPreparationProgress(CloudPreparationStage.CATALOG, "Conferindo catálogo com origem verificável…"))
            val problems = catalog?.takeIf { now() - it.first in 0..CACHE_MILLIS }?.second ?: run {
                catalogs++
                val result = (cf.problems() as? ProblemCatalogResult.Success)?.problems
                    ?: error("Falha no catálogo. Tente novamente nesta etapa.")
                validate(); catalog = now() to result; result
            }
            progress(CloudPreparationProgress(CloudPreparationStage.PROFILE, "Atualizando rating oficial de referência…"))
            profiles++
            val user = (cf.user(handle) as? UserLookupResult.Success)?.user
                ?: error("Falha no perfil Codeforces. Tente novamente nesta etapa.")
            validate(); done = true
            return Prepared(history, problems, user)
        } finally { lastMetrics = Metrics(pages, catalogs, profiles, now() - began, incremental, done) }
    }

    private suspend fun history(context: String, handle: String, validate: () -> Unit, page: () -> Unit): List<CodeforcesSubmission> {
        // No cross-account or cross-binding state, even for an identical public handle.
        complete.keys.retainAll(setOf(context)); scans.keys.retainAll(setOf(context))
        val base = complete[context]?.takeIf { now() - it.at in 0..CACHE_MILLIS }
        val scan = scans.getOrPut(context) { Scan(base) }
        if (scan.base != base) { scans.remove(context); return history(context, handle, validate, page) }
        val knownIds = base?.rows?.mapTo(hashSetOf()) { it.id }.orEmpty()
        val boundary = base?.rows?.filter { it.verdict.isNullOrBlank() || it.verdict == "TESTING" }
            ?.minOfOrNull { it.createdAt.toEpochMilli() }
            ?: base?.rows?.maxOfOrNull { it.createdAt.toEpochMilli() }
        repeat(pageBudget) {
            validate(); page()
            val result = cf.submissionPage(handle, scan.from, pageSize) as? SubmissionHistoryResult.Success
                ?: error("Falha no histórico. Tente novamente para retomar a preparação.")
            validate()
            check(result.verificationComplete && result.receivedCount == result.submissions.size) {
                "Página incompleta. Nenhum treino foi autorizado; tente novamente."
            }
            val rows = result.submissions
            if (scan.from > 1 && scan.overlap.isNotEmpty() && rows.none { it.id in scan.overlap }) {
                scans.remove(context)
                error("O histórico mudou durante a paginação. Tente novamente para conferir a cobertura.")
            }
            rows.forEach { scan.rows[it.id] = it }
            val metBoundary = boundary != null && rows.any { it.createdAt.toEpochMilli() < boundary &&
                it.id in knownIds }
            if (result.receivedCount < pageSize || metBoundary) {
                val merged = (base?.rows.orEmpty().associateBy { it.id } + scan.rows).values.toList()
                scans.remove(context)
                if (scan.from > 1 && base == null) {
                    // Pagination can span retries while new submissions arrive at the front.
                    // Verify the top again before this full history becomes recommendation input.
                    complete[context] = Complete(merged, now())
                    return history(context, handle, validate, page)
                }
                complete[context] = Complete(merged, now())
                return merged
            }
            // Overlap half a page so shifted pagination cannot silently skip submissions.
            scan.overlap = rows.takeLast((pageSize / 2).coerceAtLeast(1)).map { it.id }.toSet()
            scan.from += (pageSize / 2).coerceAtLeast(1)
        }
        error("Limite de $pageBudget páginas atingido. Tente novamente para continuar a preparação salva.")
    }

    /** One bounded window for a whole pending batch; incomplete windows never classify an upsolve. */
    suspend fun window(handle: String, since: Long, context: String = handle, validate: () -> Unit): List<CodeforcesSubmission>? {
        val key = "$context:$since"
        windows.keys.retainAll(setOf(key))
        val scan = windows.getOrPut(key) { Scan(null) }
        repeat(4) {
            validate()
            val result = cf.submissionPage(handle, scan.from, pageSize) as? SubmissionHistoryResult.Success ?: return null
            validate()
            if (!result.verificationComplete || result.receivedCount != result.submissions.size) return null
            if (scan.from > 1 && scan.overlap.isNotEmpty() && result.submissions.none { it.id in scan.overlap }) {
                windows.remove(key); return null
            }
            result.submissions.forEach { scan.rows[it.id] = it }
            if (result.receivedCount < pageSize || result.submissions.any { it.createdAt.toEpochMilli() <= since }) {
                windows.remove(key)
                return scan.rows.values.toList()
            }
            scan.overlap = result.submissions.takeLast((pageSize / 2).coerceAtLeast(1)).map { it.id }.toSet()
            scan.from += (pageSize / 2).coerceAtLeast(1)
        }
        return null // Cursor survives the next scheduled/manual attempt; no partial classification.
    }
    companion object { const val CACHE_MILLIS = 86_400_000L }
}
