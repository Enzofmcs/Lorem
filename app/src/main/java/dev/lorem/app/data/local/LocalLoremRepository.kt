package dev.lorem.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import android.database.sqlite.SQLiteConstraintException
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.model.IpsumSubmission
import dev.lorem.app.domain.repository.IpsumUpdate
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumResult
import dev.lorem.app.domain.calculateConsolidatedRating
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.ProfileLinkChanged

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalLoremRepository(
    private val dataStore: DataStore<Preferences>,
    private val problemHistoryDao: ProblemHistoryDao,
    private val problemCatalogDao: ProblemCatalogDao,
    private val ipsumDao: IpsumDao,
) : LoremRepository {
    override val studyDataKind: String = "ipsum_local"
    private val profileMutation = Mutex()
    override val profileLink: Flow<ProfileLink?> = dataStore.data.map { preferences ->
        preferences[HANDLE]?.let { ProfileLink.of(it, preferences[LINK_VERSION] ?: 0L) }
    }

    override suspend fun <T> withProfileLink(link: ProfileLink, write: suspend () -> T): T =
        profileMutation.withLock {
            val preferences = dataStore.data.first()
            val current = preferences[HANDLE]?.let { ProfileLink.of(it, preferences[LINK_VERSION] ?: 0L) }
            if (current != link) throw ProfileLinkChanged()
            write()
        }
    override val profile: Flow<LocalProfile?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val current = readProfile(preferences)
            if (current == null) null else reconcileTrainingProgress(current, preferences[LINK_VERSION] ?: 0L)
        }.flowOn(Dispatchers.IO)

    override val problemHistory: Flow<List<ProblemHistory>> = profile
        .flatMapLatest { profile ->
            profile?.let { problemHistoryDao.observeForOwner(normalizeHandle(it.handle)) }
                ?: flowOf(emptyList())
        }
        .map { history -> history.map { it.toDomain() } }

    override val problemCatalog: Flow<List<CodeforcesProblem>> = problemCatalogDao.observeAll()
        .map { problems -> problems.map { it.toDomain() } }

    override val catalogLastUpdatedEpochMillis: Flow<Long?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[CATALOG_LAST_UPDATED] }

    override val ipsums: Flow<List<Ipsum>> = profile.flatMapLatest { profile ->
        profile?.let { ipsumDao.observeForOwner(normalizeHandle(it.handle)) } ?: flowOf(emptyList())
    }.map { values -> values.map(IpsumEntity::toDomain) }
    override val activeIpsum: Flow<Ipsum?> = ipsumDao.observeActive().map { it?.toDomain() }

    override suspend fun saveProfileIfCurrent(expected: ProfileLink?, profile: LocalProfile): Boolean = profileMutation.withLock {
        if (profileLink.first() != expected) return@withLock false
        saveProfileUnlocked(profile)
        true
    }

    override suspend fun saveProfile(profile: LocalProfile) = profileMutation.withLock { saveProfileUnlocked(profile) }

    private suspend fun saveProfileUnlocked(profile: LocalProfile) {
        require(profile.handle.isNotBlank()) { "handle must not be blank" }
        require(profile.displayName.isNotBlank()) { "displayName must not be blank" }
        val active = ipsumDao.observeActive().first()
        check(active == null || active.ownerHandle == normalizeHandle(profile.handle)) {
            "End the active Ipsum before changing accounts"
        }
        dataStore.edit { preferences ->
            val sameOwner = preferences[HANDLE]?.let(::normalizeHandle) == normalizeHandle(profile.handle)
            if (!sameOwner) {
                preferences.remove(LAST_RATED_IPSUM)
                preferences[LINK_VERSION] = (preferences[LINK_VERSION] ?: 0L) + 1L
            }
            preferences.remove(LEGACY_DISPLAY_NAME)
            preferences[HANDLE] = profile.handle.trim()
            preferences[DISPLAY_NAME] = profile.displayName.trim()
            profile.officialRating?.let { preferences[OFFICIAL_RATING] = it }
                ?: preferences.remove(OFFICIAL_RATING)
            profile.officialRatingObservedAtEpochMillis?.let { preferences[OFFICIAL_RATING_OBSERVED_AT] = it }
                ?: preferences.remove(OFFICIAL_RATING_OBSERVED_AT)
            preferences[LOREM_RATING] = profile.loremRating
            val consolidated = if (sameOwner) listOfNotNull(preferences[CONSOLIDATED_RATING], profile.consolidatedRating).maxOrNull()
                else profile.consolidatedRating
            consolidated?.let { preferences[CONSOLIDATED_RATING] = it }
                ?: preferences.remove(CONSOLIDATED_RATING)
            preferences[LAST_SYNC] = profile.lastSyncEpochMillis
        }
        Unit
    }

    override suspend fun markHistorySynchronized(ownerHandle: String, atEpochMillis: Long) {
        dataStore.edit { preferences ->
            if (preferences[HANDLE]?.let(::normalizeHandle) == normalizeHandle(ownerHandle)) {
                preferences[LAST_SYNC] = maxOf(preferences[LAST_SYNC] ?: 0L, atEpochMillis)
            }
        }
    }

    override suspend fun updateProfileMetadata(ownerHandle: String, displayName: String, officialRating: Int?, observedAtEpochMillis: Long?) {
        dataStore.edit { preferences ->
            if (preferences[HANDLE]?.let(::normalizeHandle) == normalizeHandle(ownerHandle)) {
                preferences[HANDLE] = ownerHandle.trim()
                preferences[DISPLAY_NAME] = displayName
                officialRating?.let { preferences[OFFICIAL_RATING] = it } ?: preferences.remove(OFFICIAL_RATING)
                observedAtEpochMillis?.let { preferences[OFFICIAL_RATING_OBSERVED_AT] = it }
                    ?: preferences.remove(OFFICIAL_RATING_OBSERVED_AT)
            }
        }
    }

    override suspend fun clearProfile() = profileMutation.withLock {
        dataStore.edit { preferences ->
            preferences[LINK_VERSION] = (preferences[LINK_VERSION] ?: 0L) + 1L
            preferences.remove(LEGACY_DISPLAY_NAME)
            preferences.remove(HANDLE)
            preferences.remove(DISPLAY_NAME)
            preferences.remove(OFFICIAL_RATING)
            preferences.remove(OFFICIAL_RATING_OBSERVED_AT)
            preferences.remove(LOREM_RATING)
            preferences.remove(CONSOLIDATED_RATING)
            preferences.remove(LAST_SYNC)
            preferences.remove(LAST_RATED_IPSUM)
        }
        Unit
    }

    override suspend fun studyParticipantKey(): String {
        var result: String? = null
        dataStore.edit { preferences ->
            val handle = preferences[HANDLE]?.let(::normalizeHandle) ?: error("No profile")
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(handle.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val key = stringPreferencesKey("study_participant_$hash")
            result = preferences[key] ?: java.util.UUID.randomUUID().toString().also { preferences[key] = it }
        }
        return requireNotNull(result)
    }

    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
        val normalizedOwner = normalizeHandle(ownerHandle)
        require(normalizedOwner.isNotBlank()) { "ownerHandle must not be blank" }
        problemHistoryDao.upsertAll(history.map { it.toEntity(normalizedOwner) })
    }

    override suspend fun replaceProblemCatalog(
        problems: List<CodeforcesProblem>,
        updatedAtEpochMillis: Long,
    ) {
        require(updatedAtEpochMillis > 0) { "updatedAtEpochMillis must be positive" }
        val eligible = problems.filter { it.rating != null }.distinctBy { it.id }
        problemCatalogDao.replaceAll(eligible.map(CodeforcesProblem::toCatalogEntity))
        dataStore.edit { it[CATALOG_LAST_UPDATED] = updatedAtEpochMillis }
    }

    override suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum = profileMutation.withLock {
        createActiveIpsumUnlocked(ipsum)
    }

    override suspend fun createActiveIpsumForLink(ipsum: Ipsum, link: ProfileLink): Ipsum = withProfileLink(link) {
        createActiveIpsumUnlocked(ipsum)
    }

    private suspend fun createActiveIpsumUnlocked(ipsum: Ipsum): Ipsum {
        require(ipsum.id == 0L) { "new Ipsum must not have an id" }
        require(ipsum.startedAtEpochMillis > 0) { "startedAtEpochMillis must be positive" }
        val current = readProfile(dataStore.data.first())
        check(current == null || normalizeHandle(current.handle) == normalizeHandle(ipsum.ownerHandle)) { "Profile changed while selecting an Ipsum" }
        return try {
            ipsum.copy(
                id = ipsumDao.insert(ipsum.copy(ownerHandle = normalizeHandle(ipsum.ownerHandle)).toEntity()),
                ownerHandle = normalizeHandle(ipsum.ownerHandle),
            )
        } catch (error: SQLiteConstraintException) {
            throw ActiveIpsumAlreadyExistsException(error)
        }
    }

    override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) {
        require(revealedAtEpochMillis > 0) { "revealedAtEpochMillis must be positive" }
        ipsumDao.revealHintOnce(ipsumId, revealedAtEpochMillis)
    }

    override suspend fun recordIpsumSubmissions(
        ipsumId: Long,
        submissions: List<IpsumSubmission>,
    ): IpsumUpdate {
        val update = ipsumDao.record(ipsumId, submissions.map(IpsumSubmission::toEntity))
        refreshTrainingProgress()
        return update
    }

    override suspend fun endIpsumWithoutAc(
        ipsumId: Long,
        reason: IpsumFailureReason,
        endedAtEpochMillis: Long,
    ): Boolean {
        require(endedAtEpochMillis > 0) { "endedAtEpochMillis must be positive" }
        val ended = ipsumDao.endWithoutAc(ipsumId, reason.name, endedAtEpochMillis)
        refreshTrainingProgress()
        return ended
    }

    override suspend fun getIpsumResult(ipsumId: Long): IpsumResult? {
        val ipsum = ipsumDao.find(ipsumId)?.toDomain() ?: return null
        val owner = readProfile(dataStore.data.first())?.handle ?: return null
        if (normalizeHandle(owner) != normalizeHandle(ipsum.ownerHandle)) return null
        if (ipsum.status == dev.lorem.app.domain.model.IpsumStatus.ACTIVE) return null
        refreshTrainingProgress()
        val ordered = ipsumDao.submissions(ipsumId)
        val errors = ordered.takeWhile { it.verdict != "OK" }.map { it.verdict }
        return IpsumResult(ipsum, errors)
    }

    override suspend fun markIpsumsSolvedOutside(firstAcceptedAtByIpsumId: Map<Long, Long>): Int =
        ipsumDao.markSolvedOutside(firstAcceptedAtByIpsumId)

    private suspend fun refreshTrainingProgress() {
        val preferences = dataStore.data.first()
        readProfile(preferences)?.let { reconcileTrainingProgress(it, preferences[LINK_VERSION] ?: 0L) }
    }

    /** Recover Room -> DataStore interruption without replaying an old result after a rating cycle. */
    private suspend fun reconcileTrainingProgress(current: LocalProfile, linkVersion: Long): LocalProfile? {
        val owner = normalizeHandle(current.handle)
        val attempts = ipsumDao.observeForOwner(owner).first().map(IpsumEntity::toDomain)
        val latest = attempts.filter { it.finalLoremRating != null && it.endedAtEpochMillis != null }.maxByOrNull { it.id }
        val consolidated = calculateConsolidatedRating(attempts, current.consolidatedRating, owner).consolidatedRating
        val updated = dataStore.edit { preferences ->
            if (preferences[HANDLE]?.let(::normalizeHandle) == owner && (preferences[LINK_VERSION] ?: 0L) == linkVersion) {
                if (latest != null && (preferences[LAST_RATED_IPSUM] ?: 0L) < latest.id &&
                    (preferences[LOREM_RATING] == latest.initialLoremRating || preferences[LOREM_RATING] == latest.finalLoremRating)) {
                    preferences[LOREM_RATING] = requireNotNull(latest.finalLoremRating)
                    preferences[LAST_RATED_IPSUM] = latest.id
                }
                if (consolidated != null && consolidated > (preferences[CONSOLIDATED_RATING] ?: 0)) {
                    preferences[CONSOLIDATED_RATING] = consolidated
                }
            }
        }
        return readProfile(updated)
    }

    private fun readProfile(preferences: Preferences): LocalProfile? {
        val handle = preferences[HANDLE]?.takeIf(String::isNotBlank) ?: return null
        val displayName = preferences[DISPLAY_NAME]?.takeIf(String::isNotBlank) ?: return null
        val loremRating = preferences[LOREM_RATING] ?: return null
        val lastSync = preferences[LAST_SYNC] ?: return null
        return LocalProfile(
            handle = handle,
            displayName = displayName,
            officialRating = preferences[OFFICIAL_RATING],
            loremRating = loremRating,
            consolidatedRating = preferences[CONSOLIDATED_RATING],
            lastSyncEpochMillis = lastSync,
            officialRatingObservedAtEpochMillis = preferences[OFFICIAL_RATING_OBSERVED_AT],
        )
    }

    private companion object {
        fun normalizeHandle(handle: String): String = handle.trim().lowercase(Locale.ROOT)
        val LEGACY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val HANDLE = stringPreferencesKey("profile_handle")
        val DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val OFFICIAL_RATING = intPreferencesKey("profile_official_rating")
        val OFFICIAL_RATING_OBSERVED_AT = longPreferencesKey("profile_official_rating_observed_at")
        val LOREM_RATING = intPreferencesKey("profile_lorem_rating")
        val CONSOLIDATED_RATING = intPreferencesKey("profile_consolidated_rating")
        val LAST_RATED_IPSUM = longPreferencesKey("profile_last_rated_ipsum")
        val LAST_SYNC = longPreferencesKey("profile_last_sync")
        val LINK_VERSION = longPreferencesKey("profile_link_version")
        val CATALOG_LAST_UPDATED = longPreferencesKey("catalog_last_updated")
    }
}
