package dev.lorem.app.domain

import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.flow.first

/** In-memory snapshot for one profile; encoding must whitelist fields, never serialize Ipsum wholesale. */
data class IpsumStudyExport(
    val link: ProfileLink,
    val participantKey: String,
    val exportedAtEpochMillis: Long,
    val attempts: List<Ipsum>,
    val activeOmitted: Int,
    val dataKind: String,
)

class ExportIpsumStudy(private val repository: LoremRepository, private val nowMillis: () -> Long = System::currentTimeMillis) {
    suspend operator fun invoke(): IpsumStudyExport? {
        val link = repository.profileLink.first() ?: return null
        return repository.withProfileLink(link) {
            val owned = repository.ipsums.first().filter { it.ownerHandle.trim().equals(link.handle, true) }
            IpsumStudyExport(link, repository.studyParticipantKey(), nowMillis(),
                owned.filter { it.status != IpsumStatus.ACTIVE }, owned.count { it.status == IpsumStatus.ACTIVE }, repository.studyDataKind)
        }
    }
}
