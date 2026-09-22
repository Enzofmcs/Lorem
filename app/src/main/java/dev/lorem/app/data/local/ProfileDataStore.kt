package dev.lorem.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val PROFILE_STORE_NAME = "local_profile"

val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PROFILE_STORE_NAME,
)
