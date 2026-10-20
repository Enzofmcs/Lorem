package dev.lorem.app.data.cloud

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import dev.lorem.app.domain.*
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.handles.HandlesRepository
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** All writes are server transactions, scoped to the captured validated Google uid. */
class FirestoreTrainingRepository(
    private val db: FirebaseFirestore,
    private val currentUid: () -> String?,
    private val cf: CodeforcesRepository,
    private val accountHandles: HandlesRepository,
    override val deadline: CloudDeadline,
    private val elapsedMillis: () -> Long,
    private val schedule: (String, CloudIpsum, Long) -> Unit,
    private val recommender: RecommendIpsum = RecommendIpsum { kotlin.random.Random.nextInt(it) },
    private val legacy: dev.lorem.app.domain.rollout.LegacyMigration? = null,
    private val pseudonyms: dev.lorem.app.data.rollout.StudyPseudonyms? = null,
) : CloudTrainingRepository {
    private val verificationMutex = Mutex() // UI and worker share this API/persistence operation.
    private data class HistoryKey(val uid: String, val handle: String, val binding: Long, val version: Long)
    private val historyCache = java.util.concurrent.ConcurrentHashMap<HistoryKey, List<CloudIpsum>>()
    private fun user(uid: String) = db.collection("users").document(uid)
    private fun handles(uid: String) = user(uid).collection("handles")
    private fun handle(uid: String, key: String) = handles(uid).document(key)
    private fun attempts(uid: String, key: String) = handle(uid, key).collection("ipsums")
    private fun runtime(uid: String) = user(uid).collection("runtime").document("current")
    private fun checkUid(uid: String) = check(uid.isNotBlank() && currentUid() == uid) { "Sessão alterada. Entre novamente." }
    private fun selection(uid: String) = user(uid).collection("runtime").document("selection")
    private fun clock(uid: String) = user(uid).collection("runtime").document("clock")
    override suspend fun selectedHandle(uid: String): String? {
        checkUid(uid)
        val selected = withTimeoutOrNull(10_000) { selection(uid).get(Source.DEFAULT).await() }
            ?: selection(uid).get(Source.CACHE).await()
        checkUid(uid)
        return selected.getString("handleKey")
    }
    override suspend fun selectHandle(uid: String, key: String) {
        checkUid(uid)
        val profile = handle(uid, key).get(Source.SERVER).await().cloudHandle()
        accountHandles.select(uid, key, profile.bindingVersion)
        checkUid(uid)
    }

    override suspend fun addHandle(uid: String, handle: String): CloudHandle {
        checkUid(uid)
        val profile = accountHandles.add(uid, handle)
        checkUid(uid)
        return CloudHandle(profile.identity.key, profile.identity.canonical, profile.loremRating,
            profile.consolidatedRating, profile.progressVersion, profile.bindingVersion)
    }

    override suspend fun load(uid: String, selectedKey: String?): CloudSnapshot {
        checkUid(uid)
        val requestElapsed = elapsedMillis()
        val (pulse, online) = try {
            val confirmed = withTimeoutOrNull(10_000) {
                db.runTransaction { tx ->
                    checkUid(uid)
                    tx.set(clock(uid), mapOf("checkedAt" to FieldValue.serverTimestamp()))
                }.await()
                clock(uid).get(Source.SERVER).await()
            }
            if (confirmed == null) clock(uid).get(Source.CACHE).await() to false else confirmed to true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Only availability failures permit cached viewing. Rules/auth errors must stay visible.
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            if (code !in setOf(com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)) throw e
            clock(uid).get(Source.CACHE).await() to false
        }
        val source = if (online) Source.SERVER else Source.CACHE
        if (online) deadline.confirm(uid, pulse.getTimestamp("checkedAt")!!.toDate().time, requestElapsed)
        val lock = runtime(uid).get(source).await()
        val allHandles = handles(uid).get(source).await().documents.map { it.cloudHandle() }
        val activeKey = lock.getString("handleKey")
        val activeId = lock.getString("ipsumId")
        val active = if (activeKey != null && activeId != null)
            attempts(uid, activeKey).document(activeId).get(source).await().cloudIpsum() else null
        val key = activeKey ?: selectedKey
        val profile = allHandles.firstOrNull { it.key == key }
        val link = profile?.let { HistoryKey(uid, it.key, it.bindingVersion, it.version) }
        val previous = link?.let { historyCache[it] }
        val closed = if (key == null) emptyList() else previous ?: attempts(uid, key).get(source).await().documents
            .map { it.cloudIpsum() }.sortedByDescending { it.ipsum.startedAtEpochMillis }
        // Closed history is immutable except upsolve; don't reread every historical document on each poll.
        if (link != null && online) {
            historyCache.keys.removeAll { it.uid != uid || (it.handle == key && it.version != link.version) }
            historyCache[link] = closed.filter { it.state == CloudIpsumState.FINAL }
        }
        val visible = (closed.filter { it.id != active?.id } + listOfNotNull(active)).sortedByDescending { it.ipsum.startedAtEpochMillis }
        checkUid(uid)
        if (online && active != null) schedule(uid, active, requireNotNull(deadline.now(uid)))
        return CloudSnapshot(allHandles, active, visible, online)
    }

    override suspend fun start(uid: String, key: String, commandId: String): CloudIpsum = verificationMutex.withLock {
        checkUid(uid)
        require(commandId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val ref = attempts(uid, key).document(commandId)
        // Lost acknowledgement: recover by the original command id before selecting any new candidate.
        val existing = withTimeoutOrNull(10_000) { ref.get(Source.SERVER).await() }
            ?: error("Não foi possível confirmar o servidor. Verifique a conexão e tente novamente.")
        if (existing.exists()) {
            val recovered = existing.cloudIpsum()
            load(uid, key)
            return@withLock recovered.also { checkUid(uid) }
        }
        val before = load(uid, key)
        check(before.online) { "Conecte-se para iniciar um novo Ipsum." }
        check(before.active == null) { "Já existe um Ipsum ativo nesta conta." }
        val profile = before.handles.firstOrNull { it.key == key } ?: error("Selecione um handle válido.")
        check(legacy?.reconcile()?.ready != false) { "Conclua a verificação do Ipsum anterior no arquivo local." }
        val exclusionsRef = handle(uid, key).collection("exclusions")
        val localExclusions = legacy?.exclusions(profile.handle).orEmpty()
        for (chunk in localExclusions.toList().chunked(400)) {
            checkUid(uid)
            val batch = db.batch()
            chunk.forEach { id -> batch.set(exclusionsRef.document("${id.contestId}_${id.index}"),
                mapOf("contestId" to id.contestId, "index" to id.index, "source" to "legacy-device-v1")) }
            batch.commit().await()
        }
        val exclusions = exclusionsRef.get(Source.SERVER).await().documents.map {
            ProblemId(requireNotNull(it.getLong("contestId")), requireNotNull(it.getString("index")))
        }.toSet()
        val history = (cf.submissionHistory(profile.handle) as? SubmissionHistoryResult.Success)?.takeIf { it.verificationComplete }?.submissions
            ?: error("Atualize o histórico completo do Codeforces antes de iniciar.")
        val catalog = (cf.problems() as? ProblemCatalogResult.Success)?.problems
            ?: error("Não foi possível preparar o catálogo verificado. Tente novamente.")
        checkUid(uid)
        val prior = attempts(uid, key).get(Source.SERVER).await().documents.map { it.cloudIpsum() }
        val recommendation = recommender.recommend(profile.rating, catalog.filter { it.id !in exclusions }, buildProblemHistory(history), prior.map { it.ipsum }, profile.handle)
            ?: error("Nenhum problema inédito disponível nesta faixa.")
        val official = (cf.user(profile.handle) as? UserLookupResult.Success)?.user
            ?: error("Não foi possível atualizar o perfil Codeforces.")
        val observed = official.ratingObservedAtEpochMillis?.takeIf { it > 0 &&
            deadline.now(uid)?.let { now -> it <= now } == true && official.rating != null }
        val snap = snapshotOf(recommendation, profile.rating, profile.handle, official.rating.takeIf { observed != null }, observed)
        val marker = handle(uid, key).collection("usedProblems").document("${recommendation.problem.id.contestId}_${recommendation.problem.id.index}")
        db.runTransaction { tx ->
            checkUid(uid)
            val old = tx.get(ref)
            val current = tx.get(handle(uid, key)).cloudHandle()
            val lock = tx.get(runtime(uid))
            val selected = tx.get(selection(uid))
            val used = tx.get(marker)
            if (!old.exists()) {
                check(current == profile) { "O rating mudou. Atualize e tente novamente." }
                check(!lock.exists()) { "Já existe um Ipsum ativo nesta conta." }
                check(selected.getString("handleKey") == key && selected.getLong("bindingVersion") == profile.bindingVersion) {
                    "A seleção mudou. Selecione o handle e tente novamente."
                }
                check(!used.exists()) { "Este problema já foi usado. Tente novamente." }
                tx.set(ref, mapOf("handleKey" to key, "bindingVersion" to profile.bindingVersion, "version" to profile.version, "snapshot" to snap,
                    "state" to "ACTIVE", "startedAt" to FieldValue.serverTimestamp(), "limitMillis" to IPSUM_LIMIT_MILLIS,
                    "ruleVersion" to CLOUD_RULE_VERSION, "hintAt" to null, "cutoffAt" to null, "closeReason" to null,
                    "failure" to null, "result" to null, "processedAt" to null, "solvedOutsideAt" to null))
                tx.set(marker, mapOf("ipsumId" to commandId))
                tx.set(runtime(uid), mapOf("handleKey" to key, "ipsumId" to commandId, "checkedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
        checkUid(uid)
        val result = ref.get(Source.SERVER).await().cloudIpsum()
        load(uid, key) // Confirms server time and schedules deadline before returning to the screen.
        result
    }

    override suspend fun exportStudy(uid: String, key: String): IpsumStudyExport {
        checkUid(uid)
        val profile = handle(uid, key).get(Source.SERVER).await().cloudHandle()
        val selected = selection(uid).get(Source.SERVER).await()
        check(selected.getString("handleKey") == key)
        val all = attempts(uid, key).get(Source.SERVER).await().documents.map { it.cloudIpsum() }
        val ids = requireNotNull(pseudonyms)
        val result = IpsumStudyExport(dev.lorem.app.domain.sync.ProfileLink.forAccount(uid, profile.handle,
            profile.bindingVersion, requireNotNull(selected.getLong("selectionVersion"))),
            ids.participant(profile.handle), System.currentTimeMillis(), all.filter { it.state == CloudIpsumState.FINAL }.map { it.ipsum },
            all.count { it.state != CloudIpsumState.FINAL }, "ipsum_cloud", ids.sampleScope(uid, "$key:${profile.bindingVersion}"))
        checkUid(uid)
        return result
    }

    override suspend fun hint(uid: String, attempt: CloudIpsum) {
        checkUid(uid)
        check(deadline.now(uid)?.let { it < attempt.expiresAtMillis } == true) { "Confirme o prazo com a conexão ativa." }
        val ref = attempts(uid, attempt.handleKey).document(attempt.id)
        db.runTransaction { tx ->
            checkUid(uid)
            val latest = tx.get(ref).cloudIpsum()
            check(latest.state == CloudIpsumState.ACTIVE)
            if (latest.ipsum.hintRevealedAtEpochMillis == null) tx.update(ref, "hintAt", FieldValue.serverTimestamp())
        }.await()
        checkUid(uid)
    }

    override suspend fun close(uid: String, attempt: CloudIpsum, reason: IpsumFailureReason) {
        closeAtBoundary(uid, attempt, reason, timeout = deadline.now(uid)?.let { it >= attempt.expiresAtMillis } == true)
        reconcile(uid, attempt)
    }

    private suspend fun closeAtBoundary(uid: String, attempt: CloudIpsum, reason: IpsumFailureReason?, timeout: Boolean) {
        checkUid(uid)
        val ref = attempts(uid, attempt.handleKey).document(attempt.id)
        db.runTransaction { tx ->
            checkUid(uid)
            val document = tx.get(ref)
            val current = document.cloudIpsum()
            val startedAt = requireNotNull(document.getTimestamp("startedAt"))
            if (current.state == CloudIpsumState.ACTIVE) tx.update(ref, mapOf(
                "state" to "CLOSED_AWAITING_VERIFICATION",
                "cutoffAt" to if (timeout) Timestamp(startedAt.seconds + IPSUM_LIMIT_MILLIS / 1000,
                    startedAt.nanoseconds) else FieldValue.serverTimestamp(),
                "closeReason" to if (timeout) "TIME_LIMIT_EXCEEDED" else "GIVE_UP",
                "failure" to if (timeout) IpsumFailureReason.TIME_EXPIRED.name else requireNotNull(reason).name))
        }.await()
        checkUid(uid)
    }

    override suspend fun reconcile(uid: String, attempt: CloudIpsum): Boolean = verificationMutex.withLock {
        checkUid(uid)
        load(uid, attempt.handleKey)
        val ref = attempts(uid, attempt.handleKey).document(attempt.id)
        var current = ref.get(Source.SERVER).await().cloudIpsum()
        if (current.state == CloudIpsumState.FINAL) {
            if (current.ipsum.status == IpsumStatus.PENDING) updateUpsolve(uid, current)
            return@withLock true
        }
        val now = requireNotNull(deadline.now(uid))
        if (current.state == CloudIpsumState.ACTIVE && now >= current.expiresAtMillis) {
            closeAtBoundary(uid, current, null, timeout = true)
            current = ref.get(Source.SERVER).await().cloudIpsum()
        }
        val submissions = (cf.submissionHistorySince(current.ipsum.ownerHandle, current.ipsum.startedAtEpochMillis - 1)
            as? SubmissionHistoryResult.Success)?.takeIf { it.verificationComplete }?.submissions ?: return@withLock false
        checkUid(uid)
        val verification = verifyCloudWindow(current, submissions)
        if (verification == CloudVerification.Awaiting) return@withLock false
        if (verification is CloudVerification.NoAccepted && current.state == CloudIpsumState.ACTIVE) return@withLock false
        val history = attempts(uid, current.handleKey).get(Source.SERVER).await().documents.map { it.cloudIpsum() }
        val expected = current
        db.runTransaction { tx ->
            checkUid(uid)
            val latestDoc = tx.get(ref)
            val latest = latestDoc.cloudIpsum()
            val profileRef = handle(uid, latest.handleKey)
            val profile = tx.get(profileRef).cloudHandle()
            val lock = tx.get(runtime(uid))
            if (latest.state != CloudIpsumState.FINAL) {
                // Another device may have revealed a hint or closed an earlier boundary while CF was loading.
                check(latest == expected) { "O treino mudou durante a verificação. Tente novamente." }
                check(lock.getString("ipsumId") == latest.id && lock.getString("handleKey") == latest.handleKey)
                check(profile.version == latest.version && profile.bindingVersion == latest.bindingVersion &&
                    profile.rating == latest.ipsum.initialLoremRating)
                val failure = latestDoc.getString("failure")?.let(IpsumFailureReason::valueOf)
                val final = finalizeCloudIpsum(latest, verification, failure)
                val consolidated = calculateConsolidatedRating(history.filter { it.id != latest.id }.map { it.ipsum } + final,
                    profile.consolidatedRating, final.ownerHandle, final.finalLoremRating!!).consolidatedRating
                // Preserve Firestore's full timestamp precision for a manual/timeout boundary.
                val result = if (verification is CloudVerification.NoAccepted)
                    resultOf(final) + ("endedAt" to latestDoc.getTimestamp("cutoffAt")) else resultOf(final)
                tx.update(ref, mapOf("state" to "FINAL", "result" to result, "processedAt" to FieldValue.serverTimestamp()))
                tx.update(profileRef, mapOf("loremRating" to final.finalLoremRating, "progressVersion" to profile.version + 1,
                    "consolidatedRating" to consolidated, "lastIpsumId" to latest.id))
                tx.delete(runtime(uid))
            }
        }.await()
        checkUid(uid)
        true
    }

    private suspend fun updateUpsolve(uid: String, attempt: CloudIpsum) {
        val submissions = (cf.submissionHistorySince(attempt.ipsum.ownerHandle, attempt.boundaryMillis)
            as? SubmissionHistoryResult.Success)?.takeIf { it.verificationComplete }?.submissions ?: return
        val accepted = submissions.filter { it.problemId == attempt.ipsum.problem.id && it.verdict == "OK" &&
            it.createdAt.toEpochMilli() > attempt.boundaryMillis }.minByOrNull { it.createdAt } ?: return
        val ref = attempts(uid, attempt.handleKey).document(attempt.id)
        db.runTransaction { tx ->
            checkUid(uid)
            val latest = tx.get(ref).cloudIpsum()
            if (latest.ipsum.status == IpsumStatus.PENDING) tx.update(ref, "solvedOutsideAt", timestamp(accepted.createdAt.toEpochMilli()))
        }.await()
        checkUid(uid)
        historyCache.keys.removeAll { it.uid == uid && it.handle == attempt.handleKey }
    }
}
