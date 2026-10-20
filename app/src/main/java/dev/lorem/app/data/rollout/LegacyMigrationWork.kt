package dev.lorem.app.data.rollout

import android.content.Context
import androidx.work.*
import dev.lorem.app.BuildConfig
import dev.lorem.app.LoremApplication
import kotlinx.coroutines.CancellationException

class LegacyMigrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_AUTH_ENABLED) return Result.success()
        return try {
            if ((applicationContext as LoremApplication).container.legacyMigration.reconcile().ready) Result.success() else Result.retry()
        } catch (e: Exception) { if (e is CancellationException) throw e; Result.retry() }
    }
}
fun scheduleLegacyMigration(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("legacy-rollout-v1", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<LegacyMigrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build())
}
