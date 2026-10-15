package dev.lorem.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.domain.sync.SynchronizationStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Small resumable cursors live alongside the profile; problem evidence remains in Room. */
class PreferencesSynchronizationStore(private val dataStore: DataStore<Preferences>) : SynchronizationStore {
    private fun preference(key: String) = stringPreferencesKey("sync_v1_$key")
    override fun observe(key: String) = dataStore.data.map { decode(it[preference(key)]) }
    override suspend fun read(key: String) = observe(key).first()
    override suspend fun write(key: String, state: SynchronizationState) {
        val json = JSONObject().apply {
            put("running", state.running)
            put("retryNotBefore", state.retryNotBeforeEpochMillis)
            state.lastSuccessEpochMillis?.let { put("success", it) }
            state.failure?.let { put("failure", it) }
            put("from", state.nextFrom)
            put("count", state.importedCount)
            put("watermark", state.watermarkEpochMillis)
            state.querySinceEpochMillis?.let { put("since", it) }
            put("newest", state.newestSeenEpochMillis)
            put("catchingUp", state.catchingUp)
            put("pending", JSONObject().apply {
                state.pendingSubmissions.forEach { (id, time) -> put(id.toString(), time) }
            })
        }
        dataStore.edit { it[preference(key)] = json.toString() }
    }

    private fun decode(value: String?): SynchronizationState {
        if (value == null) return SynchronizationState()
        val json = JSONObject(value)
        val pending = json.optJSONObject("pending") ?: JSONObject()
        return SynchronizationState(
            running = json.optBoolean("running"),
            retryNotBeforeEpochMillis = json.optLong("retryNotBefore"),
            lastSuccessEpochMillis = if (json.has("success")) json.getLong("success") else null,
            failure = if (json.has("failure")) json.getString("failure") else null,
            nextFrom = json.optInt("from", 1),
            importedCount = json.optInt("count"),
            watermarkEpochMillis = json.optLong("watermark"),
            querySinceEpochMillis = if (json.has("since")) json.getLong("since") else null,
            newestSeenEpochMillis = json.optLong("newest"),
            catchingUp = json.optBoolean("catchingUp"),
            pendingSubmissions = pending.keys().asSequence().associate { it.toLong() to pending.getLong(it) },
        )
    }
}
