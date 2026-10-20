package dev.lorem.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.data.local.LocalLoremRepository
import dev.lorem.app.data.local.LoremDatabase
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Real local storage, with only the external Codeforces service scripted. */
class TrainingHarness(scope: CoroutineScope) : AutoCloseable {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    val databaseFile = File(context.cacheDir, "journey-${System.nanoTime()}.db")
    private val preferencesFile = File(context.cacheDir, "journey-${System.nanoTime()}.preferences_pb")
    private val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
    val appearance = dev.lorem.app.data.local.PreferencesAppearanceRepository(store)
    var database = openDatabase()
        private set
    var repository = openRepository()
        private set
    val remote = ScriptedTrainingCodeforces()

    private fun openDatabase() = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath)
        .addMigrations(LoremDatabase.MIGRATION_9_10, LoremDatabase.MIGRATION_10_11, LoremDatabase.MIGRATION_11_12, LoremDatabase.MIGRATION_12_13, LoremDatabase.MIGRATION_13_14).build()
    private fun openRepository() = LocalLoremRepository(store, database.problemHistoryDao(), database.problemCatalogDao(), database.ipsumDao())
    fun reopen() {
        database.close()
        database = openDatabase()
        repository = openRepository()
    }
    override fun close() { database.close() }
}

class ScriptedTrainingCodeforces : CodeforcesRepository {
    @Volatile var submissions: SubmissionHistoryResult = SubmissionHistoryResult.Success(emptyList())
    @Volatile var waitingStatus: CompletableDeferred<SubmissionHistoryResult>? = null
    @Volatile var waitingStatusStarted: CompletableDeferred<Unit>? = null
    @Volatile var userResult: UserLookupResult = UserLookupResult.Success(CodeforcesUser("tourist", "Tourist", 1200))
    val catalog = (1000L..1003L).map { id ->
        CodeforcesProblem(ProblemId(id, "A"), "Training $id", 1200, setOf(listOf("dp", "math", "greedy", "graphs")[(id - 1000).toInt()])).withVerifiedTestOrigin()
    }
    override suspend fun user(handle: String) = userResult
    override suspend fun submissionHistory(handle: String): SubmissionHistoryResult {
        waitingStatusStarted?.complete(Unit)
        return waitingStatus?.await() ?: submissions
    }
    override suspend fun problems() = ProblemCatalogResult.Success(catalog)
}
