package dev.lorem.app.data.cloud

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CloudIpsumSchedulerTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val attempt = CloudIpsum("id", "tourist", 4, CloudIpsumState.ACTIVE,
        Ipsum(1, "tourist", CodeforcesProblem(ProblemId(2000, "A"), "P", 800, setOf("math")),
            800, IpsumCategory.FLUENCY, 800, 800, 800, 0, 1000, status = IpsumStatus.ACTIVE))
    @Before fun initialize() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(Executors.newSingleThreadExecutor()).build())
    }
    @Test fun `schedule is unique per uid and attempt with original delay and network constraint`() {
        val scheduler = CloudIpsumScheduler(context)
        val manager = WorkManager.getInstance(context)
        scheduler.schedule("A", attempt, 1000)
        val first = manager.getWorkInfosForUniqueWork("firebase-ipsum:A:tourist:id:deadline").get().single()
        assertEquals(IPSUM_LIMIT_MILLIS, first.initialDelayMillis)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        scheduler.schedule("A", attempt, 60_000)
        assertEquals(first.id, manager.getWorkInfosForUniqueWork("firebase-ipsum:A:tourist:id:deadline").get().single().id)
        scheduler.schedule("B", attempt, 1000)
        assertNotEquals(first.id, manager.getWorkInfosForUniqueWork("firebase-ipsum:B:tourist:id:deadline").get().single().id)
        scheduler.cancel("A")
        manager.cancelAllWorkByTag("firebase-ipsum:A").result.get()
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork("firebase-ipsum:A:tourist:id:deadline").get().single().state)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfosForUniqueWork("firebase-ipsum:B:tourist:id:deadline").get().single().state)
        scheduler.schedule("A", attempt, 61_000)
        val repaired = manager.getWorkInfosForUniqueWork("firebase-ipsum:A:tourist:id:deadline").get()
            .single { it.state == WorkInfo.State.ENQUEUED }
        assertNotEquals(first.id, repaired.id)
        assertEquals(IPSUM_LIMIT_MILLIS - 60_000, repaired.initialDelayMillis)
    }
    @Test fun `pending maintenance is unique constrained periodic and canceled with account`() {
        val scheduler = CloudIpsumScheduler(context)
        val manager = WorkManager.getInstance(context)
        scheduler.maintenance("A")
        val first = manager.getWorkInfosForUniqueWork("firebase-ipsum:pending:A").get().single()
        scheduler.maintenance("A")
        assertEquals(first.id, manager.getWorkInfosForUniqueWork("firebase-ipsum:pending:A").get().single().id)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(900_000L, first.periodicityInfo!!.repeatIntervalMillis)
        scheduler.cancel("A")
        manager.cancelAllWorkByTag("firebase-ipsum:A").result.get()
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork("firebase-ipsum:pending:A").get().single().state)
    }

}
