package dev.lorem.app.data.cloud

import android.content.Context
import androidx.work.*
import dev.lorem.app.BuildConfig
import dev.lorem.app.LoremApplication
import dev.lorem.app.domain.cloud.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class CloudIpsumScheduler(private val context: Context) {
    fun schedule(uid: String, attempt: CloudIpsum, now: Long) {
        val verifying = attempt.state == CloudIpsumState.CLOSED_AWAITING_VERIFICATION
        val work = OneTimeWorkRequestBuilder<CloudIpsumWorker>()
            .setInputData(workDataOf("uid" to uid, "handle" to attempt.handleKey, "ipsum" to attempt.id,
                "version" to attempt.version))
            .setInitialDelay(if (verifying) 0 else (attempt.expiresAtMillis - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG).addTag("$TAG:$uid").build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$uid:${attempt.handleKey}:${attempt.id}:${if (verifying) "verify" else "deadline"}", ExistingWorkPolicy.KEEP, work)
    }
    fun maintenance(uid: String) {
        val work = PeriodicWorkRequestBuilder<CloudPendingWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("uid" to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG).addTag("$TAG:$uid").build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("$TAG:pending:$uid", ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun cancel(uid: String) { WorkManager.getInstance(context).cancelAllWorkByTag("$TAG:$uid") }
    companion object { const val TAG = "firebase-ipsum" }
}

/** Default WorkManager construction is intentional: the application owns the shared API gate and mutex. */
class CloudIpsumWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_AUTH_ENABLED) return Result.success()
        val uid = inputData.getString("uid") ?: return Result.failure()
        val key = inputData.getString("handle") ?: return Result.failure()
        val id = inputData.getString("ipsum") ?: return Result.failure()
        val version = inputData.getLong("version", -1)
        val container = (applicationContext as LoremApplication).container
        val currentUid = container.validatedCloudUid()
        if (currentUid != uid) return if (currentUid == null) Result.retry() else Result.success()
        val repository = container.cloudTraining ?: return Result.retry()
        return try {
            if (reconcileCloudWork(uid, key, id, version, container::validatedCloudUid, repository)) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}

internal suspend fun reconcileCloudWork(uid: String, key: String, id: String, version: Long,
    validatedUid: () -> String?, repository: CloudTrainingRepository): Boolean {
    if (validatedUid() != uid) return false
    val snapshot = repository.load(uid, key)
    if (!snapshot.online) return false
    val active = snapshot.active ?: return true
    if (active.id != id || active.handleKey != key || active.version != version) return true
    if (validatedUid() != uid) return false
    return repository.reconcile(uid, active)
}


/** Best effort device work, never a promise of an exact server execution time. */
class CloudPendingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_AUTH_ENABLED) return Result.success()
        val uid = inputData.getString("uid") ?: return Result.failure()
        val container = (applicationContext as LoremApplication).container
        val current = container.validatedCloudUid()
        if (current != uid) return if (current == null) Result.retry() else Result.success()
        val repository = container.cloudTraining ?: return Result.retry()
        return try {
            if (maintainCloudWork(uid, container::validatedCloudUid, repository)) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}

internal suspend fun maintainCloudWork(uid: String, validatedUid: () -> String?, repository: CloudTrainingRepository): Boolean {
    if (validatedUid() != uid) return false
    val key = repository.selectedHandle(uid) ?: return true
    val snapshot = repository.load(uid, key)
    if (!snapshot.online || validatedUid() != uid) return false
    snapshot.active?.let { return repository.reconcile(uid, it) }
    repository.refreshPending(uid, key)
    return validatedUid() == uid
}
