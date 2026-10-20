package dev.lorem.app.data.rollout

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import java.security.MessageDigest
import java.util.UUID

/** Random installation-local identifiers. No direct identity or dictionary hash is exported. */
class StudyPseudonyms(private val preferences: DataStore<Preferences>) {
    suspend fun participant(handle: String): String = value("participant", dev.lorem.app.domain.sync.ProfileLink.of(handle, 0).handle)
    suspend fun sampleScope(uid: String, handle: String): String = value("sample", "${uid.length}:$uid:$handle")
    private suspend fun value(kind: String, identity: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val key = stringPreferencesKey(if (kind == "participant") "study_participant_$hash" else "study_cloud_sample_$hash")
        var result = ""
        preferences.edit { result = it[key] ?: UUID.randomUUID().toString().also { token -> it[key] = token } }
        return result
    }
}
