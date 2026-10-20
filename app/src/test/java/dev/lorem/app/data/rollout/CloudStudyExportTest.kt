package dev.lorem.app.data.rollout

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.data.local.toStudyJson
import dev.lorem.app.domain.IpsumStudyExport
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CloudStudyExportTest {
    @Test fun `cloud export excludes direct identities and distinguishes account samples without splitting a repeated handle on this device`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "study-${java.util.UUID.randomUUID()}.preferences_pb") }
            val identities = StudyPseudonyms(store)
            val participant = identities.participant("TOURIST")
            assertEquals(participant, identities.participant("tourist"))
            assertNotEquals(participant, identities.participant("Petr"))
            fun snapshot(namespace: String) = IpsumStudyExport(ProfileLink.forAccount("private-uid", "tourist", 1, 1), participant,
                1_800_000_000_000, listOf(StatisticsFixtures.finished.first().copy(studyRecordingVersion = "spark-2h-v1")), 1,
                "ipsum_cloud", namespace)
            val scopeA = identities.sampleScope("private-uid", "cf_tourist")
            assertEquals(scopeA, identities.sampleScope("private-uid", "cf_tourist"))
            val a = snapshot(scopeA).toStudyJson("0.1")
            val b = snapshot(identities.sampleScope("other-uid", "cf_tourist")).toStudyJson("0.1")
            val json = JSONObject(a)
            assertEquals(2, json.getInt("schema_version"))
            assertEquals("binding-800-v1", json.getString("initial_rating_policy"))
            assertEquals(7200000L, json.getLong("time_limit_ms"))
            assertFalse(a.contains("private-uid")); assertFalse(a.contains("tourist")); assertFalse(a.contains(scopeA))
            assertEquals(1, json.getJSONArray("attempts").length())
            assertFalse(json.getJSONArray("attempts").getJSONObject(0).getBoolean("hint_used"))
            assertNotEquals(json.getJSONArray("attempts").getJSONObject(0).getString("sample_id"),
                JSONObject(b).getJSONArray("attempts").getJSONObject(0).getString("sample_id"))
        } finally { scope.cancel() }
    }
}
