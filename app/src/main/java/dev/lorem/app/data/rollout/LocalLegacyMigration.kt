package dev.lorem.app.data.rollout

import androidx.room.withTransaction
import dev.lorem.app.data.local.*
import dev.lorem.app.domain.calculateConsolidatedRating
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import dev.lorem.app.domain.rollout.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Idempotent, local-only transition. An unresolved old attempt never grants a new cloud start. */
class LocalLegacyMigration(
    private val db: LoremDatabase,
    private val legacy: LoremRepository,
    private val cf: CodeforcesRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val pseudonyms: StudyPseudonyms? = null,
) : LegacyMigration {
    private val mutex = Mutex()
    private val dao get() = db.legacyRolloutDao()

    private suspend fun prepare(): LegacyRolloutEntity {
        dao.state()?.let { return it }
        val profile = legacy.profile.first()
        return db.withTransaction {
            dao.state() ?: run {
                val active = db.ipsumDao().observeActive().first()
                val state = LegacyRolloutEntity(phase = if (active == null) "FROZEN" else "VERIFYING",
                    profileJson = profile?.json(), activeIpsumId = active?.id, cutoffAt = null, failure = null)
                dao.insert(state)
                requireNotNull(dao.state())
            }
        }
    }
    override suspend fun reconcile(): LegacyArchive = mutex.withLock { reconcileUnlocked() }
    override suspend fun close(reason: IpsumFailureReason): LegacyArchive = mutex.withLock {
        val state = prepare()
        if (state.phase != "FROZEN" && state.cutoffAt == null) {
            val active = db.ipsumDao().find(requireNotNull(state.activeIpsumId))!!.toDomain()
            val expiry = active.startedAtEpochMillis + IPSUM_LIMIT_MILLIS
            val cutoff = now().coerceIn(active.startedAtEpochMillis, expiry)
            dao.cutoffOnce(cutoff, if (cutoff == expiry) IpsumFailureReason.TIME_EXPIRED.name else reason.name)
        }
        reconcileUnlocked()
    }
    private suspend fun reconcileUnlocked(): LegacyArchive {
        var state = prepare()
        if (state.phase == "FROZEN") return snapshot(state)
        val active = db.ipsumDao().find(requireNotNull(state.activeIpsumId))!!.toDomain()
        val expiry = active.startedAtEpochMillis + IPSUM_LIMIT_MILLIS
        if (state.cutoffAt == null && now() >= expiry) {
            dao.cutoffOnce(expiry, IpsumFailureReason.TIME_EXPIRED.name)
            state = requireNotNull(dao.state())
        }
        val response = cf.submissionHistorySince(active.ownerHandle, active.startedAtEpochMillis - 1)
        if (response !is SubmissionHistoryResult.Success || !response.verificationComplete) return snapshot(state)
        val window = CloudIpsum("legacy", cloudHandleKey(active.ownerHandle), 0,
            if (state.cutoffAt == null) CloudIpsumState.ACTIVE else CloudIpsumState.CLOSED_AWAITING_VERIFICATION,
            active, cutoffMillis = state.cutoffAt)
        val verification = verifyCloudWindow(window, response.submissions)
        if (verification == CloudVerification.Awaiting) return snapshot(state)
        if (verification is CloudVerification.NoAccepted && state.cutoffAt == null) return snapshot(state)
        db.withTransaction {
            val current = requireNotNull(dao.state())
            if (current.phase == "FROZEN") return@withTransaction
            if (current.cutoffAt != state.cutoffAt || current.failure != state.failure) return@withTransaction
            val submissions = response.submissions.filter { it.problemId == active.problem.id &&
                it.createdAt.toEpochMilli() >= window.firstEligibleSubmissionMillis && it.createdAt.toEpochMilli() <= window.boundaryMillis &&
                !it.verdict.isNullOrBlank() && it.verdict != "TESTING" }
                .map { IpsumSubmission(it.id, active.id, requireNotNull(it.verdict), it.createdAt.toEpochMilli()) }
            db.ipsumDao().insertSubmissions(submissions.map { it.toEntity() })
            val accepted = verification as? CloudVerification.Accepted
            val endedAt = accepted?.atMillis ?: requireNotNull(current.cutoffAt)
            val rating = dev.lorem.app.domain.calculateLoremRating(active.initialLoremRating, active.selectedRating,
                accepted != null, endedAt - active.startedAtEpochMillis,
                active.hintRevealedAtEpochMillis?.let { it in active.startedAtEpochMillis..endedAt } == true)
            if (accepted != null) db.ipsumDao().completeOnce(active.id, endedAt, accepted.errors,
                rating.delta, rating.ratingAfter, rating.expectedTimeMillis)
            else db.ipsumDao().endWithoutAcOnce(active.id, requireNotNull(current.failure), endedAt,
                (verification as CloudVerification.NoAccepted).errors, rating.delta, rating.ratingAfter, rating.expectedTimeMillis)
            val attempts = dao.attempts().map { it.toDomain() }
            val ended = attempts.first { it.id == active.id }
            val original = current.profileJson?.profile()
            val updated = original?.let {
                if (!it.handle.equals(ended.ownerHandle, true)) it else it.copy(loremRating = ended.finalLoremRating!!,
                    consolidatedRating = calculateConsolidatedRating(attempts, it.consolidatedRating, it.handle).consolidatedRating)
            }
            dao.update(current.copy(phase = "FROZEN", profileJson = updated?.json()))
        }
        return snapshot(requireNotNull(dao.state()))
    }
    private suspend fun snapshot(state: LegacyRolloutEntity): LegacyArchive {
        val attempts = dao.attempts().map { it.toDomain() }
        return LegacyArchive(state.phase == "FROZEN", state.profileJson?.profile(), attempts,
            attempts.firstOrNull { it.status == IpsumStatus.ACTIVE }, state.cutoffAt)
    }
    override suspend fun export(handle: String): dev.lorem.app.domain.IpsumStudyExport {
        val state = reconcile()
        check(state.ready) { "Aguarde a verificação do treino anterior." }
        val attempts = state.attempts.filter { it.ownerHandle.equals(handle, true) }
        return dev.lorem.app.domain.IpsumStudyExport(dev.lorem.app.domain.sync.ProfileLink.of(handle, 0),
            requireNotNull(pseudonyms).participant(handle), now(), attempts, 0, "ipsum_local")
    }
    override suspend fun exclusions(handle: String): Set<ProblemId> {
        val normalized = dev.lorem.app.domain.sync.ProfileLink.of(handle, 0).handle
        return dao.attempts().filter { it.ownerHandle.equals(normalized, true) }.map { ProblemId(it.contestId, it.problemIndex) }.toSet() +
            db.problemHistoryDao().observeForOwner(normalized).first().filter { it.attempted || it.hasAcceptedSubmission }
                .map { ProblemId(it.contestId, it.problemIndex) }
    }
}
private fun LocalProfile.json() = JSONObject().put("handle", handle).put("name", displayName)
    .put("rating", loremRating).put("consolidated", consolidatedRating ?: JSONObject.NULL)
    .put("official", officialRating ?: JSONObject.NULL).put("observed", officialRatingObservedAtEpochMillis ?: JSONObject.NULL)
    .put("sync", lastSyncEpochMillis).toString()
private fun String.profile(): LocalProfile = JSONObject(this).let { j ->
    LocalProfile(j.getString("handle"), j.getString("name"), if (j.isNull("official")) null else j.getInt("official"),
        j.getInt("rating"), if (j.isNull("consolidated")) null else j.getInt("consolidated"), j.getLong("sync"),
        if (j.isNull("observed")) null else j.getLong("observed"))
}
