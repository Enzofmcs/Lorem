package dev.lorem.app.domain

import dev.lorem.app.data.fake.FakeLoremRepository
import dev.lorem.app.withVerifiedTestOrigin
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.ProfileLinkChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IpsumStudyTest {
    private fun profile(observed: Long?) = LocalProfile("tourist","Tourist",1300,1200,null,1,observed)
    private val problem = CodeforcesProblem(ProblemId(7,"A"),"Example",1200,setOf("dp")).withVerifiedTestOrigin()
    private fun starter(repo: LoremRepository) = StartIpsum(repo, RecommendIpsum(RandomSource { if (it == 3) 1 else 0 }), { 5000L })

    @Test fun `CF snapshot is before start and never reconstructed from current profile`() = runTest {
        for (observed in listOf(null, 6000L, 4000L)) {
            val repo = FakeLoremRepository(profile(observed))
            repo.replaceProblemCatalog(listOf(problem),1)
            val started = (starter(repo)() as StartIpsumResult.Success).ipsum
            assertEquals(if (observed == 4000L) 1300 else null, started.initialCodeforcesRating)
            assertEquals(if (observed == 4000L) 4000L else null, started.initialCodeforcesRatingObservedAtEpochMillis)
            assertEquals("ipsum-study-v1", started.studyRecordingVersion)
            repo.updateProfileMetadata("tourist","Tourist",2000,7000)
            assertEquals(started.initialCodeforcesRating, repo.activeIpsum.first()!!.initialCodeforcesRating)
        }
    }

    @Test fun `export omits active keeps legacy absences and isolates profile`() = runTest {
        val repo = FakeLoremRepository(profile(null))
        repo.replaceProblemCatalog(listOf(problem),1)
        val active = (starter(repo)() as StartIpsumResult.Success).ipsum
        assertEquals(1, ExportIpsumStudy(repo)().let { it!!.activeOmitted })
        assertTrue(ExportIpsumStudy(repo)()!!.attempts.isEmpty())
        repo.endIpsumWithoutAc(active.id,IpsumFailureReason.TIME_EXPIRED,6000)
        val snapshot = ExportIpsumStudy(repo)()!!
        assertEquals("synthetic",snapshot.dataKind)
        assertEquals(IpsumStatus.PENDING,snapshot.attempts.single().status)
        assertEquals(6000L,snapshot.attempts.single().endedAtEpochMillis)
        assertNull(snapshot.attempts.single().initialCodeforcesRating)
        repo.saveProfile(profile(null).copy(handle="other"))
        assertTrue(ExportIpsumStudy(repo)()!!.attempts.isEmpty())
        assertNotEquals(snapshot.participantKey,ExportIpsumStudy(repo)()!!.participantKey)
    }
}
