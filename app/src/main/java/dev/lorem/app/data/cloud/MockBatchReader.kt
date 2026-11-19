package dev.lorem.app.data.cloud

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dev.lorem.app.domain.cloud.CloudIpsumState
import dev.lorem.app.domain.cloud.cloudHandleKey
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.LocalProfile
import kotlinx.coroutines.tasks.await

internal const val MOCK_COUNT = 200
internal const val MOCK_HANDLE = "mock_excel"
internal const val MOCK_RULE_VERSION = "mock-excel-v1"
internal val MOCK_ID = Regex("mock-[0-9a-f]{64}")

internal data class SavedMockHistory(val id: String, val profile: LocalProfile, val ipsums: List<Ipsum>)

internal fun mockBatchesForUser(db: FirebaseFirestore, uid: String): CollectionReference =
    db.collection("loremMockImports").document(uid).collection("batches")

internal class MockBatchReader(private val batches: CollectionReference) {
    private val key = cloudHandleKey(MOCK_HANDLE)

    suspend fun load(id: String): SavedMockHistory? {
        require(MOCK_ID.matches(id))
        val account = batches.document(id)
        val metadata = account.get(Source.SERVER).await()
        if (!metadata.exists()) return null
        check(metadata.getString("mockDatasetId") == id && metadata.getLong("mockIpsumCount") == MOCK_COUNT.toLong()) {
            "O lote existente não corresponde à planilha."
        }
        val handle = account.collection("handles").document(key)
        val storedHandle = handle.get(Source.SERVER).await().cloudHandle()
        val ipsums = handle.collection("ipsums").get(Source.SERVER).await().documents.map {
            val item = it.cloudIpsum()
            check(item.state == CloudIpsumState.FINAL && it.getString("ruleVersion") == MOCK_RULE_VERSION) {
                "O lote contém um Ipsum que não é da importação."
            }
            item.ipsum
        }.sortedBy(Ipsum::id)
        check(ipsums.size == MOCK_COUNT && ipsums.map(Ipsum::id) == (1L..MOCK_COUNT.toLong()).toList() &&
            storedHandle.version == MOCK_COUNT.toLong() && storedHandle.rating == ipsums.last().finalLoremRating) {
            "O lote no Firestore está incompleto ou inconsistente."
        }
        val profile = LocalProfile(MOCK_HANDLE, "Histórico simulado", null, storedHandle.rating,
            storedHandle.consolidatedRating, 0L)
        return SavedMockHistory(id, profile, ipsums)
    }
}
