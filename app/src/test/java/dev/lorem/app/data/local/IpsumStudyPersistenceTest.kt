package dev.lorem.app.data.local

import dev.lorem.app.TrainingHarness
import dev.lorem.app.domain.ExportIpsumStudy
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.trainingIpsum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.StartIpsum
import dev.lorem.app.domain.StartIpsumResult
import dev.lorem.app.domain.RecommendIpsum
import dev.lorem.app.domain.RandomSource
import dev.lorem.app.withVerifiedTestOrigin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IpsumStudyPersistenceTest {
    @Test fun `Android export contract from real storage records initial CF and first AC only`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            val begin=VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS+100000
            env.repository.saveProfile(LocalProfile("tourist","Do not export",1300,1200,null,1,begin-1000))
            val problem=CodeforcesProblem(ProblemId(101,"A"),"Do not export problem",1200,setOf("dp"))
                .withVerifiedTestOrigin().let { it.copy(origin=it.origin!!.copy(verifiedAtEpochMillis=begin-2000)) }
            env.repository.replaceProblemCatalog(listOf(problem),begin-2000)
            val started=(StartIpsum(env.repository,RecommendIpsum(RandomSource { if(it==3) 1 else 0 }),{ begin })() as StartIpsumResult.Success).ipsum
            env.repository.updateProfileMetadata("tourist","Do not export",2000,begin+20000)
            env.repository.recordIpsumSubmissions(started.id,listOf(IpsumSubmission(1,started.id,"WRONG_ANSWER",begin+10000),
                IpsumSubmission(2,started.id,"OK",begin+60000)))
            val synthetic=object : LoremRepository by env.repository {
                override val studyDataKind="synthetic"
                override suspend fun studyParticipantKey()="00000000-0000-0000-0000-000000000001"
            }
            val export=ExportIpsumStudy(synthetic,{begin+90000})()!!
            assertEquals(1300,export.attempts.single().initialCodeforcesRating)
            assertEquals(1200,export.attempts.single().initialLoremRating)
            val text=export.toStudyJson("synthetic-android-contract-v1")
            val row=JSONObject(text).getJSONArray("attempts").getJSONObject(0)
            assertEquals(begin+60000,row.getLong("first_ac_at_epoch_ms"))
            assertTrue(row.isNull("upsolve_ac_at_epoch_ms"))
            assertFalse(text.contains("tourist"));assertFalse(text.contains("Do not export"))
            System.getProperty("lorem.study.fixture")?.let { path ->
                java.io.File(path).apply { parentFile?.mkdirs();writeText(text+"\n") }
            }
        }
    }

    @Test fun `persistent opaque key snapshots and minimized JSON survive reopen and account changes`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(LocalProfile("tourist","Private name",1700,1200,null,1,1000))
            val original = trainingIpsum(7,IpsumStatus.PENDING).copy(initialCodeforcesRating=1500,
                initialCodeforcesRatingObservedAtEpochMillis=1000,studyRecordingVersion="ipsum-study-v1")
            env.database.ipsumDao().insert(original.toEntity())
            val key = env.repository.studyParticipantKey()
            env.reopen()
            val initial = ExportIpsumStudy(env.repository)()!!
            assertEquals(key,initial.participantKey)
            assertEquals(original,initial.attempts.single())
            env.repository.markIpsumsSolvedOutside(mapOf(original.id to requireNotNull(original.endedAtEpochMillis)+1000))
            val snapshot = ExportIpsumStudy(env.repository)()!!
            assertEquals(IpsumStatus.SOLVED_OUTSIDE,snapshot.attempts.single().status)
            assertEquals(original.endedAtEpochMillis,snapshot.attempts.single().endedAtEpochMillis)
            assertEquals(original.ratingDelta,snapshot.attempts.single().ratingDelta)
            val raw = snapshot.toStudyJson("0.1.0")
            assertFalse(raw.contains("tourist")); assertFalse(raw.contains("Private name"))
            assertFalse(raw.contains("problemName")); assertFalse(raw.contains("problemTags"))
            assertFalse(raw.contains(original.problem.name))
            val row=JSONObject(raw).getJSONArray("attempts").getJSONObject(0)
            assertEquals(1500,row.getInt("cf_rating_before"))
            assertTrue(row.isNull("interruptions")); assertTrue(row.isNull("first_ac_at_epoch_ms"))
            assertEquals(64,row.getString("sample_id").length)
            env.repository.clearProfile()
            env.repository.saveProfile(LocalProfile("other","Other",1000,1000,null,1))
            assertNotEquals(key,env.repository.studyParticipantKey())
            assertTrue(ExportIpsumStudy(env.repository)()!!.attempts.isEmpty())
            env.repository.saveProfile(LocalProfile("TOURIST","Private name",9999,1200,null,1))
            assertEquals(key,env.repository.studyParticipantKey())
            assertEquals(1500,env.repository.ipsums.first().single().initialCodeforcesRating)
        }
    }

    @Test fun `profile replacement waits for one consistent export snapshot`() = runTest {
        TrainingHarness(backgroundScope).use { env ->
            env.repository.saveProfile(LocalProfile("tourist","Tourist",1200,1200,null,1))
            val key=env.repository.studyParticipantKey()
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val source=object : LoremRepository by env.repository {
                override suspend fun studyParticipantKey(): String {
                    entered.complete(Unit);release.await();return env.repository.studyParticipantKey()
                }
            }
            val export=async { ExportIpsumStudy(source)()!! };entered.await()
            val replacement=async { env.repository.saveProfile(LocalProfile("other","Other",1500,1500,null,1)) }
            runCurrent();assertFalse(replacement.isCompleted)
            release.complete(Unit)
            assertEquals(key,export.await().participantKey)
            replacement.await()
            assertTrue(ExportIpsumStudy(env.repository)()!!.attempts.isEmpty())
            assertNotEquals(key,env.repository.studyParticipantKey())
        }
    }
}
