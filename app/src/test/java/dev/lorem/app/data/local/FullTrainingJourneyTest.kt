package dev.lorem.app.data.local

import dev.lorem.app.TrainingHarness
import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FullTrainingJourneyTest {
    @Test fun `simultaneous starts keep one active and a late hint cannot change a finished result`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(trainingProfile())
            env.repository.replaceProblemCatalog(env.remote.catalog, 1000)
            val start = StartIpsum(env.repository, RecommendIpsum(RandomSource { if (it == 3) 1 else 0 })) { 1_000_000 }
            val outcomes = listOf(async { start() }, async { start() }).map { it.await() }
            assertEquals(1, outcomes.count { it is StartIpsumResult.Success })
            assertEquals(1, outcomes.count { it is StartIpsumResult.AlreadyActive })
            val saved = env.repository.activeIpsum.first()!!
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { env.repository.saveProfile(trainingProfile(handle = "other")) }
            }
            env.repository.recordIpsumSubmissions(saved.id, listOf(IpsumSubmission(1, saved.id, "OK", saved.startedAtEpochMillis + 1000)))
            val original = env.repository.getIpsumResult(saved.id)!!
            env.repository.revealIpsumHint(saved.id, saved.startedAtEpochMillis + 2000)
            assertEquals(original, env.repository.getIpsumResult(saved.id))
            assertNull(original.ipsum.hintRevealedAtEpochMillis)
        }
    }

    @Test fun `local journey imports excludes persists completes upsolves and recommends again`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            var now = 1_000_000L
            val repository = env.repository
            repository.saveProfile(trainingProfile().copy(lastSyncEpochMillis = 0))
            val previous = env.remote.catalog.last()
            env.remote.submissions = SubmissionHistoryResult.Success(listOf(submission(1, previous.id, "WRONG_ANSWER", now - 1000)))
            assertTrue(SynchronizeProblemHistory(repository, env.remote) { now }() is ProblemHistorySyncResult.Success)
            assertTrue(SynchronizeProblemCatalog(repository, env.remote) { now }() is ProblemCatalogSyncResult.Success)
            val start = StartIpsum(repository, RecommendIpsum(RandomSource { bound -> if (bound == 3) 1 else 0 })) { now }
            val first = (start() as StartIpsumResult.Success).ipsum
            assertNotEquals(previous.id, first.problem.id)
            assertTrue(start() is StartIpsumResult.AlreadyActive)
            assertNotNull(first.recommendationFactors)
            repository.revealIpsumHint(first.id, now + 100)
            val wa = submission(2, first.problem.id, "WRONG_ANSWER", now + 1000)
            val ac = submission(3, first.problem.id, "OK", now + 2000)
            env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa, wa, ac))
            assertTrue((VerifyIpsumSubmissions(repository, env.remote)() as VerifyIpsumResult.Success).completed)
            val result = repository.getIpsumResult(first.id)!!
            assertEquals(1, result.ipsum.errorCount)
            assertEquals(18, result.ipsum.ratingDelta)
            val ratingAfterFirst = repository.profile.first()!!.loremRating
            env.reopen()
            assertEquals(result, env.repository.getIpsumResult(first.id))
            assertEquals(ratingAfterFirst, env.repository.profile.first()!!.loremRating)
            assertEquals(first.recommendationFactors, env.repository.getIpsumResult(first.id)!!.ipsum.recommendationFactors)
            now += 10_000
            val nextStart = StartIpsum(env.repository, RecommendIpsum(RandomSource { bound -> if (bound == 3) 1 else 0 })) { now }
            val second = (nextStart() as StartIpsumResult.Success).ipsum
            assertNotEquals(first.problem.id, second.problem.id)
            assertNotEquals(previous.id, second.problem.id)
            assertTrue(env.repository.endIpsumWithoutAc(second.id, IpsumFailureReason.LOGIC_NOT_FOUND, now + 1000))
            assertFalse(env.repository.endIpsumWithoutAc(second.id, IpsumFailureReason.TIME_EXPIRED, now + 2000))
            val ratingAfterFailure = env.repository.profile.first()!!.loremRating
            assertTrue(ratingAfterFailure < ratingAfterFirst)
            val before = buildIpsumStatistics(env.repository.profile.first()!!, env.repository.ipsums.first())
            env.remote.submissions = SubmissionHistoryResult.NetworkFailure
            assertEquals(UpdatePendingIpsumsResult.NetworkFailure, UpdatePendingIpsums(env.repository, env.remote)())
            env.remote.submissions = SubmissionHistoryResult.Success(listOf(submission(4, second.problem.id, "OK", now + 3000)))
            assertEquals(UpdatePendingIpsumsResult.Success(1), UpdatePendingIpsums(env.repository, env.remote)())
            assertEquals(UpdatePendingIpsumsResult.Success(0), UpdatePendingIpsums(env.repository, env.remote)())
            val after = buildIpsumStatistics(env.repository.profile.first()!!, env.repository.ipsums.first())
            assertEquals(before.overall.copy(pending = 0, solvedOutside = 1), after.overall)
            assertEquals(before.ratingEvolution, after.ratingEvolution)
            assertEquals(ratingAfterFailure, env.repository.profile.first()!!.loremRating)
            assertTrue(nextStart() is StartIpsumResult.Success)
        }
    }

    @Test fun `startup recovers Room rating write and old result cannot apply points after a rating cycle`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(trainingProfile())
            val first = env.repository.createActiveIpsum(trainingIpsum(1, IpsumStatus.ACTIVE).copy(id = 0))
            env.database.ipsumDao().record(first.id, listOf(IpsumSubmissionEntity(1, first.id, "OK", first.startedAtEpochMillis + 1000)))
            env.reopen()
            assertEquals(1224, env.repository.profile.first()!!.loremRating)
            env.database.ipsumDao().insert(trainingIpsum(2).copy(initialLoremRating = 1224, finalLoremRating = 1200, ratingDelta = -24).toEntity())
            assertEquals(1200, env.repository.profile.first()!!.loremRating)
            env.repository.getIpsumResult(first.id)
            assertEquals(1200, env.repository.profile.first()!!.loremRating)
        }
    }

    @Test fun `historical milestone recovers persists survives losses and remains isolated by handle`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(trainingProfile(rating = 1518))
            val rows = qualifiedBand(1400) + (9L..29L).map { trainingIpsum(it, IpsumStatus.PENDING, 1400) }
            rows.forEach { env.database.ipsumDao().insert(it.toEntity()) }
            assertEquals(1400, env.repository.profile.first()!!.consolidatedRating)
            assertEquals(1518, env.repository.profile.first()!!.loremRating)
            env.reopen()
            assertEquals(1400, env.repository.profile.first()!!.consolidatedRating)
            val active = env.repository.createActiveIpsum(trainingIpsum(30, IpsumStatus.ACTIVE, 1400).copy(id = 0, initialLoremRating = 1518))
            env.repository.endIpsumWithoutAc(active.id, IpsumFailureReason.TIME_EXPIRED, active.startedAtEpochMillis + 1000)
            assertTrue(env.repository.profile.first()!!.loremRating < 1518)
            assertEquals(1400, env.repository.profile.first()!!.consolidatedRating)
            env.repository.saveProfile(trainingProfile(handle = "other"))
            assertNull(env.repository.profile.first()!!.consolidatedRating)
            assertNull(env.repository.getIpsumResult(1))
            env.repository.saveProfile(trainingProfile())
            assertEquals(1400, env.repository.profile.first()!!.consolidatedRating)
        }
    }

    @Test fun `late history sync preserves current rating milestone and new profile`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(trainingProfile(consolidated = 1400))
            val response = CompletableDeferred<SubmissionHistoryResult>()
            env.remote.waitingStatus = response
            env.remote.waitingStatusStarted = CompletableDeferred()
            val sync = async { SynchronizeProblemHistory(env.repository, env.remote) { 9999 }() }
            env.remote.waitingStatusStarted!!.await()
            val active = env.repository.createActiveIpsum(trainingIpsum(1, IpsumStatus.ACTIVE).copy(id = 0))
            env.repository.endIpsumWithoutAc(active.id, IpsumFailureReason.TIME_EXPIRED, active.startedAtEpochMillis + 1000)
            val rating = env.repository.profile.first()!!.loremRating
            response.complete(SubmissionHistoryResult.Success(emptyList()))
            assertTrue(sync.await() is ProblemHistorySyncResult.Success)
            assertEquals(rating, env.repository.profile.first()!!.loremRating)
            assertEquals(1400, env.repository.profile.first()!!.consolidatedRating)
            env.repository.updateProfileMetadata("tourist", "Updated", 1500)
            assertEquals(rating, env.repository.profile.first()!!.loremRating)
            env.remote.waitingStatus = CompletableDeferred()
            env.remote.waitingStatusStarted = CompletableDeferred()
            val oldAccountSync = async { SynchronizeProblemHistory(env.repository, env.remote) { 12345 }() }
            env.remote.waitingStatusStarted!!.await()
            env.repository.saveProfile(trainingProfile(handle = "other"))
            env.remote.waitingStatus!!.complete(SubmissionHistoryResult.Success(emptyList()))
            assertTrue(oldAccountSync.await() is ProblemHistorySyncResult.Success)
            env.repository.markHistorySynchronized("tourist", 12345)
            env.repository.updateProfileMetadata("tourist", "Wrong", 9999)
            assertEquals("other", env.repository.profile.first()!!.handle)
            assertEquals(1L, env.repository.profile.first()!!.lastSyncEpochMillis)
        }
    }

    @Test fun `version 9 database upgrades with Room validation and original results intact`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            val original = trainingIpsum(1, IpsumStatus.PENDING).copy(ratingDelta = -20, finalLoremRating = 1180)
            env.database.ipsumDao().insert(original.toEntity())
            env.database.close()
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext()).name(env.databaseFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
            val db = helper.writableDatabase
            val columns = mutableListOf<String>()
            val definitions = mutableListOf<String>()
            db.query("PRAGMA table_info(ipsums)").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (name == "recommendationFactors") continue
                    columns += name
                    definitions += "$name ${cursor.getString(2)}" +
                        (if (cursor.getInt(5) > 0) " PRIMARY KEY AUTOINCREMENT" else "") +
                        (if (cursor.getInt(3) > 0) " NOT NULL" else "") +
                        (if (!cursor.isNull(4)) " DEFAULT ${cursor.getString(4)}" else "")
                }
            }
            db.execSQL("ALTER TABLE ipsums RENAME TO ipsums_v10")
            db.execSQL("CREATE TABLE ipsums (${definitions.joinToString()})")
            db.execSQL("INSERT INTO ipsums (${columns.joinToString()}) SELECT ${columns.joinToString()} FROM ipsums_v10")
            db.execSQL("DROP TABLE ipsums_v10")
            db.execSQL("CREATE UNIQUE INDEX index_ipsums_activeSlot ON ipsums(activeSlot)")
            db.execSQL("PRAGMA user_version = 9")
            helper.close()
            env.reopen()
            assertEquals(original, env.database.ipsumDao().find(1)!!.toDomain())
            assertEquals(10, env.database.openHelper.readableDatabase.version)
        }
    }

    private fun submission(id: Long, problemId: ProblemId, verdict: String, at: Long) =
        CodeforcesSubmission(id, problemId, verdict, Instant.ofEpochMilli(at))
}
