package dev.lorem.app

import dev.lorem.app.domain.sync.SynchronizationCoordinator
import dev.lorem.app.domain.sync.BackgroundSynchronization
import dev.lorem.app.data.sync.SynchronizationScheduler
import dev.lorem.app.data.sync.SynchronizationWorkerFactory
import dev.lorem.app.data.remote.CodeforcesRetryAfter
import androidx.work.WorkManager
import dev.lorem.app.data.local.PreferencesSynchronizationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import dev.lorem.app.data.local.LocalLoremRepository
import dev.lorem.app.data.local.LoremDatabase
import dev.lorem.app.data.local.profileDataStore
import dev.lorem.app.data.remote.CodeforcesApiRepository
import dev.lorem.app.data.remote.TwoSecondCodeforcesRequestGate
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = LoremDatabase.create(context)
    val loremRepository: LoremRepository = LocalLoremRepository(
        dataStore = context.profileDataStore,
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
    )
    private val synchronizationStore = PreferencesSynchronizationStore(context.profileDataStore)
    private val retryAfter = CodeforcesRetryAfter(synchronizationStore)
    val codeforcesRepository: CodeforcesRepository = CodeforcesApiRepository(
        gate = TwoSecondCodeforcesRequestGate(beforeRequest = retryAfter::awaitPermission),
        onRetryAfter = retryAfter::record,
    )
    val synchronizationCoordinator = SynchronizationCoordinator(
        loremRepository, codeforcesRepository, applicationScope,
        synchronizationStore,
    )
    val backgroundSynchronization = BackgroundSynchronization(loremRepository, synchronizationCoordinator, apiDeferred = retryAfter::isDeferred)
    val workerFactory = SynchronizationWorkerFactory(backgroundSynchronization, retryAfter::isDeferred)
    val synchronizationScheduler by lazy {
        SynchronizationScheduler(
            WorkManager.getInstance(context), loremRepository, backgroundSynchronization, applicationScope,
        )
    }

    fun startSynchronization() {
        applicationScope.launch {
            try {
                synchronizationCoordinator.recoverInterrupted()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException && error !is dev.lorem.app.domain.sync.ProfileLinkChanged) throw error
                // Workers recover the durable cursor if storage or the profile changed during startup.
                android.util.Log.w("LoremSync", "Recuperação inicial adiada para o trabalho agendado.", error)
            }
            synchronizationScheduler.observeProfiles()
        }
    }

    fun recoverSynchronization() { synchronizationScheduler.requestRecovery() }
}
