package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.ConsolidationProgress
import dev.lorem.app.domain.ConsolidationRules
import java.util.Locale

@Composable
fun ConsolidationCard(progress: ConsolidationProgress, compact: Boolean = false) {
    val next = progress.nextBand
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Faixa consolidada: ${progress.consolidatedRating ?: "ainda não consolidada"}", style = MaterialTheme.typography.titleMedium)
            Text("A maior faixa alcançada é preservada. O Rating Lorem acompanha seu desempenho atual.")
            Text("Próxima faixa: ${next.rating}–${next.rating + 99}")
            if (!compact) {
                Text("Progresso nos últimos ${ConsolidationRules.WINDOW} Ipsums encerrados:")
                Text("Tentativas na faixa: ${next.attempts}/${ConsolidationRules.MIN_ATTEMPTS}; faltam ${(ConsolidationRules.MIN_ATTEMPTS - next.attempts).coerceAtLeast(0)}.")
                val rate = if (next.attempts == 0) "sem dados" else String.format(Locale.ROOT, "%.1f%%", next.accepted * 100.0 / next.attempts)
                Text("AC na faixa: $rate (${next.accepted}/${next.attempts}); mínimo ${ConsolidationRules.MIN_AC_PERCENT}%.")
                Text("ACs sem dica: ${next.acceptedWithoutHint}/${ConsolidationRules.MIN_WITHOUT_HINT}; faltam ${(ConsolidationRules.MIN_WITHOUT_HINT - next.acceptedWithoutHint).coerceAtLeast(0)}.")
                Text("Tags em ACs sem dica: ${next.tagCount}/${ConsolidationRules.MIN_TAGS}; faltam ${(ConsolidationRules.MIN_TAGS - next.tagCount).coerceAtLeast(0)}.")
                Text("Conjuntos de tags: ${next.styleCount}/${ConsolidationRules.MIN_STYLES}; faltam ${(ConsolidationRules.MIN_STYLES - next.styleCount).coerceAtLeast(0)}.")
            }
        }
    }
}
