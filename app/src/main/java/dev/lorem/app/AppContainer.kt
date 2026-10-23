package dev.lorem.app

import android.content.Context
import androidx.work.WorkManager
import dev.lorem.app.data.auth.FirebaseAuthentication
import dev.lorem.app.data.handles.AccountHandlesRepository
import dev.lorem.app.data.local.RoomHandlesCache
import dev.lorem.app.domain.handles.HandlesRepository
import dev.lorem.app.data.cloud.CloudIpsumScheduler
import dev.lorem.app.data.cloud.FirestoreTrainingRepository
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.cloud.CloudDeadline
import dev.lorem.app.data.local.LocalLoremRepository
import dev.lorem.app.data.local.LoremDatabase
import dev.lorem.app.data.local.PreferencesAppearanceRepository
import dev.lorem.app.data.local.PreferencesSynchronizationStore
import dev.lorem.app.data.local.profileDataStore
import dev.lorem.app.data.remote.CodeforcesApiRepository
import dev.lorem.app.data.remote.CodeforcesRetryAfter
import dev.lorem.app.data.remote.TwoSecondCodeforcesRequestGate
import dev.lorem.app.data.sync.SynchronizationScheduler
import dev.lorem.app.data.sync.SynchronizationWorkerFactory
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.sync.BackgroundSynchronization
import dev.lorem.app.domain.sync.SynchronizationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authenticationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val authentication by lazy { FirebaseAuthentication.create(appContext, authenticationScope) }
    private val cloudScheduler by lazy { CloudIpsumScheduler(appContext) }
    fun validatedCloudUid(): String? = (authentication.repository.state.value as? AuthenticationState.SignedIn)
        ?.account?.uid?.takeIf { it == authentication.auth?.currentUser?.uid }
    val cloudTraining by lazy {
        authentication.firestore?.let { firestore -> FirestoreTrainingRepository(firestore, ::validatedCloudUid,
            codeforcesRepository, requireNotNull(handlesRepository),
            CloudDeadline(android.os.SystemClock::elapsedRealtime) {
                android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
            }, android.os.SystemClock::elapsedRealtime, cloudScheduler::schedule,
            duelCatalog = dev.lorem.app.data.cloud.VirtualDuelCatalog(
                appContext.assets.open("virtual-duel-v1.json").bufferedReader().use { it.readText() }),
            duelEnabled = BuildConfig.VIRTUAL_DUEL_ENABLED,
            scheduleMaintenance = cloudScheduler::maintenance,
            legacy = legacyMigration, pseudonyms = studyPseudonyms,
            scoringSelector = dev.lorem.app.data.cloud.TimeCurveCatalog(
                appContext.assets.open("time-curves-v1.json").bufferedReader().use { it.readText() }
            )::select) }
    }
    private val database = LoremDatabase.create(context)
    val loremRepository: LoremRepository = LocalLoremRepository(
        dataStore = context.profileDataStore,
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
        archiveStarted = { database.legacyRolloutDao().state() != null },
    )
    val appearanceRepository = PreferencesAppearanceRepository(context.profileDataStore)
    private val synchronizationStore = PreferencesSynchronizationStore(context.profileDataStore)
    private val retryAfter = CodeforcesRetryAfter(synchronizationStore)
    val codeforcesRepository: CodeforcesRepository = CodeforcesApiRepository(
        gate = TwoSecondCodeforcesRequestGate(beforeRequest = retryAfter::awaitPermission),
        onRetryAfter = retryAfter::record,
    )
    private val studyPseudonyms by lazy { dev.lorem.app.data.rollout.StudyPseudonyms(appContext.profileDataStore) }
    val legacyMigration by lazy { dev.lorem.app.data.rollout.LocalLegacyMigration(database, loremRepository,
        codeforcesRepository, pseudonyms = studyPseudonyms) }
    val handlesRepository: HandlesRepository? by lazy {
        authentication.handlesStore?.let { AccountHandlesRepository(it, codeforcesRepository,
            RoomHandlesCache(database.accountHandlesDao())) }
    }
    val synchronizationCoordinator = SynchronizationCoordinator(
        loremRepository, codeforcesRepository, applicationScope,
        synchronizationStore,
    )
    val backgroundSynchronization = BackgroundSynchronization(loremRepository, synchronizationCoordinator, apiDeferred = retryAfter::isDeferred)
    val workerFactory = SynchronizationWorkerFactory(backgroundSynchronization, retryAfter::isDeferred,
        isAllowed = { !BuildConfig.FIREBASE_AUTH_ENABLED })
    val synchronizationScheduler by lazy {
        SynchronizationScheduler(
            WorkManager.getInstance(context), loremRepository, backgroundSynchronization, applicationScope,
        )
    }

    fun startSynchronization() {
        if (BuildConfig.FIREBASE_AUTH_ENABLED) {
            // Legacy data has no Firebase uid. Do not run its workers under a Google session.
            WorkManager.getInstance(appContext).apply {
                cancelAllWorkByTag(SynchronizationScheduler.PROFILE_TAG)
                cancelUniqueWork(SynchronizationScheduler.CATALOG_PERIODIC)
                cancelUniqueWork(SynchronizationScheduler.CATALOG_PREPARE)
            }
            dev.lorem.app.data.rollout.scheduleLegacyMigration(appContext)
            applicationScope.launch {
                var previous: String? = null
                authentication.repository.state.collectLatest { state ->
                    val uid = (state as? AuthenticationState.SignedIn)?.account?.uid
                    if (uid != previous) {
                        previous?.let { cloudScheduler.cancel(it); cloudTraining?.deadline?.clear(it) }
                        previous = uid
                    }
                    if (uid != null) try {
                        cloudTraining?.load(uid, cloudTraining?.selectedHandle(uid))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }
            }
            return
        }
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

    fun recoverSynchronization() {
        if (!BuildConfig.FIREBASE_AUTH_ENABLED) synchronizationScheduler.requestRecovery()
    }
}
