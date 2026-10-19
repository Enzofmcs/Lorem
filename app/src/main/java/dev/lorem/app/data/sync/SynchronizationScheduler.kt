package dev.lorem.app.data.sync

import androidx.work.*
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.sync.BackgroundSynchronization
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** KEEP preserves the persisted cadence across recreation, reboot and foreground recovery. */
class SynchronizationScheduler(
    private val workManager: WorkManager,
    private val repository: LoremRepository,
    private val synchronization: BackgroundSynchronization,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun observeProfiles() = scope.launch {
        repository.profileLink.distinctUntilChanged().onEach { recover() }.retryWhen { error, _ ->
            if (error is CancellationException) throw error
            delay(30_000)
            true
        }.collect {}
    }

    fun requestRecovery() = scope.launch {
        try { recover() }
        catch (error: Exception) { if (error is CancellationException) throw error }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val link = repository.profileLink.first()
            // Also cancel old owners left in WorkManager by a previous process.
            workManager.getWorkInfosByTag(PROFILE_TAG).get().filter {
                !it.state.isFinished && (link == null || ownerTag(link) !in it.tags)
            }.forEach { workManager.cancelWorkById(it.id).result.get() }
            if (link == null) return@withLock
            workManager.enqueueUniquePeriodicWork(
                profileName(link), ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SynchronizationWorker>(15, TimeUnit.MINUTES)
                    .setInputData(profileData(link)).addTag(PROFILE_TAG).addTag(ownerTag(link))
                    .setConstraints(network()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            ).result.get()
            workManager.enqueueUniquePeriodicWork(
                CATALOG_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SynchronizationWorker>(24, TimeUnit.HOURS)
                    .setInputData(catalogData()).setConstraints(network(batteryNotLow = true))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            ).result.get()
            if (synchronization.profileDue(link)) workManager.enqueueUniqueWork(
                "prepare:${ownerTag(link)}", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SynchronizationWorker>().setInputData(profileData(link))
                    .addTag(PROFILE_TAG).addTag(ownerTag(link)).setConstraints(network())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            ).result.get()
            if (synchronization.catalogDue()) workManager.enqueueUniqueWork(
                CATALOG_PREPARE, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SynchronizationWorker>().setInputData(catalogData())
                    .setConstraints(network()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            ).result.get()
        }
    }

    companion object {
        const val PROFILE_TAG = "lorem-profile-sync"
        const val CATALOG_PERIODIC = "lorem-catalog-periodic"
        const val CATALOG_PREPARE = "lorem-catalog-prepare"
        fun ownerTag(link: ProfileLink) = "lorem-owner:${link.scopeKey}"
        fun profileName(link: ProfileLink) = "periodic:${ownerTag(link)}"
        fun profileData(link: ProfileLink) = Data.Builder()
            .putString(SynchronizationWorker.KIND, SynchronizationWorker.PROFILE)
            .putString(SynchronizationWorker.HANDLE, link.handle)
            .putLong(SynchronizationWorker.VERSION, link.version)
            .apply { link.uid?.let {
                putString(SynchronizationWorker.UID, it)
                putLong(SynchronizationWorker.BINDING_VERSION, link.bindingVersion)
            } }.build()
        fun catalogData() = workDataOf(SynchronizationWorker.KIND to SynchronizationWorker.CATALOG)
        private fun network(batteryNotLow: Boolean = false) = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(batteryNotLow).build()
    }
}
