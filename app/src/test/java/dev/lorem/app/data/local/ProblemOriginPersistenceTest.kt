package dev.lorem.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.withVerifiedTestOrigin
import dev.lorem.app.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProblemOriginPersistenceTest {
    @Test fun `verified cache and new Ipsum retain provenance while legacy stays unverified`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoremDatabase::class.java).build()
        try {
            val legacy = CodeforcesProblem(ProblemId(1,"A"), "Legacy", 1200, setOf("dp"))
            val verified = legacy.copy(id = ProblemId(2,"A")).withVerifiedTestOrigin()
            db.problemCatalogDao().replaceAll(listOf(legacy, verified).map { it.toCatalogEntity() })
            val restored = db.problemCatalogDao().observeAll().first().map { it.toDomain() }
            assertEquals(listOf(legacy, verified), restored)
            assertFalse(restored[0].isEligibleForNewRecommendation())
            assertTrue(restored[1].isEligibleForNewRecommendation())
            val ipsum = Ipsum(1,"tourist",verified,1200,IpsumCategory.CURRENT_LEVEL,1200,1200,1200,0,1234,status=IpsumStatus.ACTIVE)
            assertEquals(verified.origin, ipsum.toEntity().toDomain().problem.origin)
            db.ipsumDao().insert(ipsum.toEntity())
            db.problemCatalogDao().replaceAll(emptyList())
            val active = db.ipsumDao().observeActive().first()!!.toDomain()
            assertEquals(verified.origin, active.problem.origin)
            assertEquals(1234L, active.startedAtEpochMillis)
            assertEquals(1200, active.initialLoremRating)
            assertNull(legacy.toCatalogEntity().copy(originMetadata="broken").toDomain().origin)
        } finally { db.close() }
    }
}
