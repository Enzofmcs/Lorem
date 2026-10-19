package dev.lorem.app.data.local

import androidx.room.*
import dev.lorem.app.data.handles.HandlesCache
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.sync.ProfileLink

@Entity(tableName = "account_handles", primaryKeys = ["uid", "handleKey", "bindingVersion"])
data class AccountHandleEntity(
    val uid: String,
    val handleKey: String,
    val bindingVersion: Long,
    val handle: String,
    val loremRating: Int,
    val consolidatedRating: Int?,
    val progressVersion: Long,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "account_selections")
data class AccountSelectionEntity(
    @PrimaryKey val uid: String,
    val handleKey: String?,
    val bindingVersion: Long?,
    val selectionVersion: Long,
    val locked: Boolean,
)

@Dao
abstract class AccountHandlesDao {
    @Query("SELECT * FROM account_handles WHERE uid = :uid ORDER BY handleKey")
    abstract suspend fun handles(uid: String): List<AccountHandleEntity>
    @Query("SELECT * FROM account_selections WHERE uid = :uid")
    abstract suspend fun selection(uid: String): AccountSelectionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(handles: List<AccountHandleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(selection: AccountSelectionEntity)
    @Query("DELETE FROM account_handles WHERE uid = :uid")
    protected abstract suspend fun deleteHandles(uid: String)

    @Transaction
    open suspend fun read(uid: String): HandlesSnapshot {
        val profiles = handles(uid).map { row ->
            val identity = HandleIdentity.parse(row.handle)
            check(identity.key == row.handleKey)
            AccountHandle(row.uid, identity, row.bindingVersion, row.loremRating,
                row.consolidatedRating, row.progressVersion, row.createdAtEpochMillis)
        }
        val selected = selection(uid)
        val target = selected?.let { saved -> profiles.firstOrNull {
            it.identity.key == saved.handleKey && it.bindingVersion == saved.bindingVersion
        } }
        val link = if (target != null) ProfileLink.forAccount(uid,
            target.identity.canonical, target.bindingVersion, selected.selectionVersion) else null
        return HandlesSnapshot(profiles, link, selected?.locked ?: false)
    }

    @Transaction
    open suspend fun save(uid: String, profiles: List<AccountHandleEntity>, selected: AccountSelectionEntity): HandlesSnapshot {
        require(selected.uid == uid && profiles.all { it.uid == uid })
        val previous = selection(uid)
        if (previous != null && previous.selectionVersion > selected.selectionVersion) return read(uid)
        if (previous != null && previous.selectionVersion == selected.selectionVersion &&
            (previous.handleKey != selected.handleKey || previous.bindingVersion != selected.bindingVersion)) {
            throw HandleException(HandleFailure.CONFIGURATION)
        }
        val older = handles(uid).associateBy { it.handleKey to it.bindingVersion }
        var fresh = true
        val merged = profiles.map { incoming ->
            val existing = older[incoming.handleKey to incoming.bindingVersion]
            if (existing != null && existing.progressVersion > incoming.progressVersion) {
                fresh = false
                existing
            } else incoming
        }
        deleteHandles(uid)
        insert(merged)
        insert(selected)
        return read(uid).copy(serverConfirmed = fresh)
    }
}

class RoomHandlesCache(private val dao: AccountHandlesDao) : HandlesCache {
    override suspend fun read(uid: String) = dao.read(uid)
    override suspend fun save(uid: String, snapshot: HandlesSnapshot): HandlesSnapshot {
        require(snapshot.serverConfirmed && snapshot.handles.all { it.uid == uid })
        val link = snapshot.selection
        require(link == null || (link.uid == uid && snapshot.selected != null))
        val selected = snapshot.selected
        return dao.save(uid, snapshot.handles.map { handle -> AccountHandleEntity(uid,
            handle.identity.key, handle.bindingVersion, handle.identity.canonical, handle.loremRating,
            handle.consolidatedRating, handle.progressVersion, handle.createdAtEpochMillis) },
            AccountSelectionEntity(uid, selected?.identity?.key, selected?.bindingVersion,
                link?.version ?: 0L, snapshot.locked))
    }
}
