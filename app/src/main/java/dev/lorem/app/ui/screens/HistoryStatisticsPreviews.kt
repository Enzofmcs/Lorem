package dev.lorem.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.PendingUpdateUiState
import dev.lorem.app.ui.StatisticsUiState
import dev.lorem.app.ui.theme.LoremTheme

@Preview(name = "Histórico claro", widthDp = 360, heightDp = 800)
@Preview(name = "Histórico escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Histórico 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Histórico paisagem", widthDp = 640, heightDp = 360)
@Composable
private fun HistoryPreview() {
    LoremTheme { HistoryScreen(historyPreviewAttempts, updateState = PendingUpdateUiState.Error("Sem conexão. Seu histórico salvo continua disponível.")) }
}

@Preview(name = "Estatísticas claro", widthDp = 360, heightDp = 800)
@Preview(name = "Estatísticas escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Estatísticas 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Estatísticas paisagem", widthDp = 640, heightDp = 360)
@Composable
private fun StatisticsPreview() {
    LoremTheme { StatisticsScreen(StatisticsUiState.Ready(buildIpsumStatistics(
        LocalProfile("preview", "Preview", null, 1180, null, 1), historyPreviewAttempts))) }
}

@Preview(name = "Histórico vazio", widthDp = 360, heightDp = 800)
@Composable
private fun EmptyHistoryPreview() { LoremTheme { HistoryScreen(emptyList()) } }

// Illustrative local snapshots, used only in previews.
private val historyPreviewAttempts = listOf(Ipsum(1, "preview",
    CodeforcesProblem(ProblemId(100, "A"), "Um problema com nome extenso para revisar seu treinamento", 1200, setOf("implementation", "graphs")),
    1200, IpsumCategory.CURRENT_LEVEL, 1200, 1200, 1200, 0, 1_700_000_000_000,
    endedAtEpochMillis = 1_700_003_600_000, status = IpsumStatus.COMPLETED, ratingDelta = 24, finalLoremRating = 1224))
