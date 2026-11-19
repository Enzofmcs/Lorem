package dev.lorem.app.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.data.cloud.SavedMockHistory
import dev.lorem.app.domain.buildIpsumHistory

@Composable
internal fun MockBatchHistory(saved: SavedMockHistory) {
    val history = remember(saved.ipsums) { buildIpsumHistory(saved.ipsums) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Histórico · ${history.size} Ipsums", style = MaterialTheme.typography.headlineSmall) }
        items(history, key = { it.ipsum.id }) { item ->
            val ipsum = item.ipsum
            Text("${ipsum.problem.id.contestId}${ipsum.problem.id.index} · ${ipsum.problem.name}",
                style = MaterialTheme.typography.titleMedium)
            Text("${item.situation.label} · Rating ${ipsum.selectedRating}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top = 10.dp))
        }
    }
}
