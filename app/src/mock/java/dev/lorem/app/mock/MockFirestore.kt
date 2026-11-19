package dev.lorem.app.mock

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dev.lorem.app.data.cloud.MOCK_HANDLE
import dev.lorem.app.data.cloud.MOCK_RULE_VERSION
import dev.lorem.app.data.cloud.MockBatchReader
import dev.lorem.app.data.cloud.SavedMockHistory
import dev.lorem.app.data.cloud.mockBatchesForUser
import dev.lorem.app.data.cloud.resultOf
import dev.lorem.app.data.cloud.snapshotOf
import dev.lorem.app.data.cloud.timestamp
import dev.lorem.app.domain.IpsumRecommendation
import dev.lorem.app.domain.calculateLoremRating
import dev.lorem.app.domain.cloud.IPSUM_LIMIT_MILLIS
import dev.lorem.app.domain.cloud.cloudHandleKey
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.model.RecommendationFactors
import dev.lorem.app.domain.model.VerifiedProblemOrigin
import kotlinx.coroutines.tasks.await

internal const val MOCK_PROJECT = "demo-lorem-ipsums"
internal const val MOCK_PORT = 8082
private const val INITIAL_RATING = 800

internal data class MockImportResult(val inserted: Boolean, val history: SavedMockHistory)

internal class MockFirestore(
    private val db: FirebaseFirestore,
    private val batches: CollectionReference = db.collection("users"),
) {
    private val key = cloudHandleKey(MOCK_HANDLE)
    private val reader = MockBatchReader(batches)

    companion object {
        fun forUser(db: FirebaseFirestore, uid: String) = MockFirestore(
            db, mockBatchesForUser(db, uid),
        )
    }

    suspend fun import(workbook: MockWorkbook): MockImportResult {
        val account = batches.document(workbook.id)
        val handle = account.collection("handles").document(key)
        val attempts = buildAttempts(workbook.ipsums)
        val createdAt = timestamp(attempts.first().startedAtEpochMillis)
        val inserted = try {
            db.runTransaction { tx ->
                if (tx.get(account).exists()) return@runTransaction false
                tx.set(account, mapOf("schemaVersion" to 1, "createdAt" to createdAt,
                    "mockDatasetId" to workbook.id, "mockIpsumCount" to attempts.size))
                tx.set(handle, mapOf("schemaVersion" to 1, "handle" to MOCK_HANDLE, "handleKey" to key,
                    "bindingVersion" to 1, "createdAt" to createdAt, "progressVersion" to attempts.size,
                    "loremRating" to attempts.last().finalLoremRating, "consolidatedRating" to null,
                    "lastIpsumId" to attempts.last().id.toString()))
                attempts.forEach { ipsum ->
                    val id = ipsum.id.toString()
                    val problem = ipsum.problem.id
                    tx.set(handle.collection("ipsums").document(id), ipsum.toMockDocument(key))
                    tx.set(handle.collection("usedProblems").document("${problem.contestId}_${problem.index}"),
                        mapOf("ipsumId" to id))
                }
                true
            }.await()
        } catch (exception: FirebaseFirestoreException) {
            // With create-only rules, a concurrent successful commit can make a stale transaction fail.
            if (exception.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                exception.code != FirebaseFirestoreException.Code.ABORTED) throw exception
            val existing = load(workbook.id) ?: throw exception
            return MockImportResult(false, existing)
        }
        val history = requireNotNull(load(workbook.id)) { "O lote ainda não pôde ser confirmado no Firestore." }
        return MockImportResult(inserted, history)
    }

    suspend fun load(id: String): SavedMockHistory? = reader.load(id)
}

internal fun mockFirestoreClient(context: Context, appName: String = MOCK_PROJECT): FirebaseFirestore {
    val existing = FirebaseApp.getApps(context).firstOrNull { it.name == appName }
    if (existing != null) return FirebaseFirestore.getInstance(existing)
    val options = FirebaseOptions.Builder()
        .setProjectId(MOCK_PROJECT)
        .setApplicationId("1:123456789000:android:0000000000000000000000")
        .build()
    val app = FirebaseApp.initializeApp(context, options, appName)
    return FirebaseFirestore.getInstance(app).apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        useEmulator("127.0.0.1", MOCK_PORT)
    }
}

private fun buildAttempts(rows: List<MockIpsum>): List<Ipsum> {
    var rating = INITIAL_RATING
    return rows.mapIndexed { index, row ->
        val accepted = row.outcome == MockOutcome.AC
        val score = calculateLoremRating(rating, row.rating, accepted, row.duration, row.hintAt != null)
        val problem = CodeforcesProblem(ProblemId(row.contestId, row.index), row.name, row.rating,
            row.tags.toSet(), VerifiedProblemOrigin(row.contestId, row.startedAt, row.startedAt, "mock:excel", MOCK_RULE_VERSION))
        val failure = when {
            accepted -> null
            row.duration == IPSUM_LIMIT_MILLIS -> IpsumFailureReason.TIME_EXPIRED
            else -> IpsumFailureReason.LOGIC_NOT_FOUND
        }
        Ipsum(
            id = index + 1L,
            ownerHandle = MOCK_HANDLE,
            problem = problem,
            initialLoremRating = rating,
            category = IpsumCategory.CURRENT_LEVEL,
            desiredRatingMin = row.rating,
            desiredRatingMax = row.rating,
            selectedRating = row.rating,
            fallbackDistance = 0,
            startedAtEpochMillis = row.startedAt,
            hintRevealedAtEpochMillis = row.hintAt,
            endedAtEpochMillis = row.startedAt + row.duration,
            errorCount = row.errors,
            failureReason = failure,
            ratingDelta = score.delta,
            finalLoremRating = score.ratingAfter,
            expectedTimeMillis = score.expectedTimeMillis,
            solvedOutsideAtEpochMillis = row.upsolveAt,
            status = when (row.outcome) {
                MockOutcome.AC -> IpsumStatus.COMPLETED
                MockOutcome.PENDENTE -> IpsumStatus.PENDING
                MockOutcome.UPSOLVE -> IpsumStatus.SOLVED_OUTSIDE
            },
        ).also { rating = score.ratingAfter }
    }
}

private fun Ipsum.toMockDocument(handleKey: String): Map<String, Any?> {
    val ended = requireNotNull(endedAtEpochMillis)
    val closeReason = when (failureReason) {
        null -> null
        IpsumFailureReason.TIME_EXPIRED -> "TIME_LIMIT_EXCEEDED"
        else -> "GIVE_UP"
    }
    val recommendation = IpsumRecommendation(problem = problem, category = category, normalizedLoremRating = initialLoremRating,
        desiredRatingMin = desiredRatingMin, desiredRatingMax = desiredRatingMax, fallbackDistance = fallbackDistance,
        factors = RecommendationFactors(emptyList(), 0))
    return mapOf(
        "handleKey" to handleKey, "version" to id - 1, "bindingVersion" to 1,
        "state" to "FINAL", "startedAt" to timestamp(startedAtEpochMillis),
        "limitMillis" to IPSUM_LIMIT_MILLIS, "ruleVersion" to MOCK_RULE_VERSION,
        "snapshot" to snapshotOf(recommendation, initialLoremRating, ownerHandle, null, null),
        "hintAt" to hintRevealedAtEpochMillis?.let(::timestamp),
        "cutoffAt" to closeReason?.let { timestamp(ended) },
        "closeReason" to closeReason, "failure" to failureReason?.name,
        "result" to resultOf(this), "processedAt" to timestamp(ended),
        "solvedOutsideAt" to solvedOutsideAtEpochMillis?.let(::timestamp),
    )
}
