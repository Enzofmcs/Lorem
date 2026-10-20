package dev.lorem.app.domain.rollout

import dev.lorem.app.domain.model.*

/** Device archive has no Firebase owner. Only public problem exclusions may be synchronized. */
data class LegacyArchive(
    val ready: Boolean,
    val profile: LocalProfile?,
    val attempts: List<Ipsum>,
    val active: Ipsum?,
    val boundaryMillis: Long?,
)

interface LegacyMigration {
    suspend fun reconcile(): LegacyArchive
    suspend fun close(reason: IpsumFailureReason): LegacyArchive
    suspend fun export(handle: String): dev.lorem.app.domain.IpsumStudyExport? = null
    suspend fun exclusions(handle: String): Set<ProblemId>
}
