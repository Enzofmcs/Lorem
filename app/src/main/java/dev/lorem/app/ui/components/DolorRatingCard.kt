package dev.lorem.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.VirtualDuelResult
import java.math.BigDecimal

/** Presentation only: keep every saved fractional point, without inventing a starting balance. */
internal fun formatDolorRating(micros: Long, signed: Boolean = false): String {
    val value = BigDecimal.valueOf(micros, 6).stripTrailingZeros().toPlainString()
        .replace('.', ',').replace('-', '−')
    return if (signed && micros >= 0) "+$value" else value
}

@Composable
fun DolorRatingCard(ratingMicros: Long?, modifier: Modifier = Modifier, result: VirtualDuelResult? = null,
    emptyMessage: String = "Ainda sem avaliação Dolor.") {
    Card(modifier.fillMaxWidth().testTag("rating-dolor").semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rating Dolor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("Experimental · Duelo virtual", style = MaterialTheme.typography.bodySmall)
            if (result != null) {
                Text("Antes: ${formatDolorRating(result.beforeMicros)}", style = MaterialTheme.typography.titleMedium)
                Text("Agora: ${formatDolorRating(result.afterMicros)}", style = MaterialTheme.typography.headlineSmall)
                Text("Variação Dolor: ${formatDolorRating(result.deltaMicros, signed = true)} pontos",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.deltaMicros >= 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error)
            } else if (ratingMicros != null) {
                Text(formatDolorRating(ratingMicros), style = MaterialTheme.typography.headlineSmall)
            } else {
                Text(emptyMessage, style = MaterialTheme.typography.bodyLarge)
            }
            Text("Saldo independente do Rating Lorem.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
