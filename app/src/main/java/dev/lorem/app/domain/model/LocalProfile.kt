package dev.lorem.app.domain.model

data class LocalProfile(
    val handle: String,
    val displayName: String,
    val officialRating: Int?,
    val loremRating: Int,
    val consolidatedRating: Int?,
    val lastSyncEpochMillis: Long,
)
