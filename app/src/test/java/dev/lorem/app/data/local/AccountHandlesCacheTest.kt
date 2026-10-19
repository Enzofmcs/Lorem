package dev.lorem.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountHandlesCacheTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun profile(uid: String, handle: String = "Tourist", rating: Int = 800, progress: Long = 0) =
        AccountHandle(uid, HandleIdentity.parse(handle), 1, rating, if (progress > 0) 1200 else null, progress, 1234)
    private fun snapshot(profile: AccountHandle, version: Long) = HandlesSnapshot(listOf(profile),
        ProfileLink.forAccount(profile.uid, profile.identity.canonical, profile.bindingVersion, version), serverConfirmed = true)

    @Test fun `persistent cache separates same handle by uid and restores each selected progress`() = runBlocking {
        val name = "handles-${System.nanoTime()}.db"
        var database = Room.databaseBuilder(context, LoremDatabase::class.java, name).build()
        try {
            var cache = RoomHandlesCache(database.accountHandlesDao())
            cache.save("a", snapshot(profile("a", rating = 1377, progress = 4), 1))
            cache.save("b", snapshot(profile("b"), 1))
            database.close()
            database = Room.databaseBuilder(context, LoremDatabase::class.java, name).build()
            cache = RoomHandlesCache(database.accountHandlesDao())
            val a = cache.read("a"); val b = cache.read("b")
            assertEquals(1377, a.selected!!.loremRating); assertEquals(1200, a.selected!!.consolidatedRating)
            assertEquals(800, b.selected!!.loremRating); assertNull(b.selected!!.consolidatedRating)
            assertFalse(a.serverConfirmed)
            assertNotEquals(syncKey(SyncDataset.HISTORY, a.selection), syncKey(SyncDataset.HISTORY, b.selection))
            assertTrue(cache.read("unknown").handles.isEmpty())
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun `late snapshot cannot roll back selection version or training progress`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        try {
            val cache = RoomHandlesCache(database.accountHandlesDao())
            cache.save("a", snapshot(profile("a", "Tourist", 1400, 7), 1))
            cache.save("a", HandlesSnapshot(listOf(profile("a", "Tourist", 1400, 7), profile("a", "Petr")),
                ProfileLink.forAccount("a", "Petr", 1, 2), serverConfirmed = true))
            val stale = cache.save("a", snapshot(profile("a", "Tourist", 800), 1))
            assertEquals("petr", stale.selection!!.handle); assertFalse(stale.serverConfirmed)
            val oldProgress = cache.save("a", HandlesSnapshot(listOf(profile("a", "Tourist", 800), profile("a", "Petr")),
                ProfileLink.forAccount("a", "Petr", 1, 2), serverConfirmed = true))
            assertEquals(1400, oldProgress.handles.first { it.identity.key == "cf_tourist" }.loremRating)
            assertFalse(oldProgress.serverConfirmed)
        } finally { database.close() }
    }

    @Test fun `cache rejects another uid and sync identities include binding and selection generations`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        try {
            val cache = RoomHandlesCache(database.accountHandlesDao())
            try { cache.save("b", snapshot(profile("a"), 1)); fail("Cross uid write") }
            catch (_: IllegalArgumentException) { }
            assertTrue(cache.read("a").handles.isEmpty()); assertTrue(cache.read("b").handles.isEmpty())
            val a = ProfileLink.forAccount("a", "tourist", 1, 1)
            val keys = listOf(a, a.copy(uid = "b"), a.copy(version = 2), a.copy(bindingVersion = 2), ProfileLink.of("tourist", 1))
                .map { syncKey(SyncDataset.HISTORY, it) }
            assertEquals(5, keys.distinct().size)
            assertEquals("tourist:1:HISTORY", keys.last())
        } finally { database.close() }
    }
}
