package dev.lorem.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.lorem.app.domain.sync.BackgroundSynchronization
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.SyncWorkResult
import kotlinx.coroutines.CancellationException

class SynchronizationWorker(
    context: Context,
    parameters: WorkerParameters,
    private val synchronization: BackgroundSynchronization,
    private val deferredByApi: suspend () -> Boolean,
    private val isAllowed: () -> Boolean = { true },
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            if (!isAllowed()) return Result.success()
            // This worker owns only the legacy repository. Never downgrade a cloud context to a handle.
            if (inputData.getString(UID) != null) return Result.success()
            if (deferredByApi()) return Result.retry()
            val outcome = when (inputData.getString(KIND)) {
                CATALOG -> synchronization.catalog()
                PROFILE -> {
                    val handle = inputData.getString(HANDLE)
                    val version = inputData.getLong(VERSION, -1)
                    if (handle.isNullOrBlank() || version < 0) return Result.failure()
                    synchronization.profile(ProfileLink.of(handle, version))
                }
                else -> return Result.failure()
            }
            when (outcome) {
                SyncWorkResult.SUCCESS, SyncWorkResult.OBSOLETE -> Result.success()
                SyncWorkResult.RETRY -> Result.retry()
                SyncWorkResult.FAILURE -> Result.failure()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A transient local persistence failure must not kill periodic maintenance.
            Result.retry()
        }
    }

    companion object {
        const val KIND = "kind"
        const val PROFILE = "profile"
        const val CATALOG = "catalog"
        const val HANDLE = "handle"
        const val VERSION = "version"
        const val UID = "uid"
        const val BINDING_VERSION = "bindingVersion"
    }
}

class SynchronizationWorkerFactory(
    private val synchronization: BackgroundSynchronization,
    private val deferredByApi: suspend () -> Boolean,
    private val isAllowed: () -> Boolean = { true },
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SynchronizationWorker::class.java.name) {
            SynchronizationWorker(appContext, workerParameters, synchronization, deferredByApi, isAllowed)
        } else null
}
