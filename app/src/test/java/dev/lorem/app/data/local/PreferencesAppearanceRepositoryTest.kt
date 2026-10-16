package dev.lorem.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.model.ThemePreference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesAppearanceRepositoryTest {
    @Test fun `all choices persist after closing storage and profile changes preserve appearance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "appearance-${System.nanoTime()}.preferences_pb")
        val db = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        try {
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            var appearance = PreferencesAppearanceRepository(store)
            assertEquals(ThemePreference.SYSTEM, appearance.themePreference.first())
            store.edit { it[stringPreferencesKey("appearance_theme")] = "future-or-invalid" }
            assertEquals(ThemePreference.SYSTEM, appearance.themePreference.first())
            for (choice in listOf(ThemePreference.DARK, ThemePreference.LIGHT, ThemePreference.SYSTEM)) {
                appearance.setThemePreference(choice)
                scope.coroutineContext[Job]!!.cancelAndJoin()
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
                appearance = PreferencesAppearanceRepository(store)
                assertEquals(choice, appearance.themePreference.first())
                val local = LocalLoremRepository(store, db.problemHistoryDao(), db.problemCatalogDao(), db.ipsumDao())
                local.saveProfile(StatisticsFixtures.profile)
                local.saveProfile(StatisticsFixtures.profile.copy(handle = "other"))
                local.clearProfile()
                assertEquals(choice, appearance.themePreference.first())
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
        } finally { db.close(); file.delete() }
    }
}
