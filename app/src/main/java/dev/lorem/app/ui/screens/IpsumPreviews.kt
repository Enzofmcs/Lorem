package dev.lorem.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.IpsumResultUiState
import dev.lorem.app.ui.IpsumTrackingUiState
import dev.lorem.app.ui.theme.LoremTheme

@Preview(name = "Treino claro", widthDp = 360, heightDp = 800)
@Preview(name = "Treino escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Treino 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Treino paisagem", widthDp = 640, heightDp = 360)
@Composable
private fun IpsumPreview() {
    LoremTheme { IpsumScreen(previewAttempt, nowMillis = { 3_661_000 }) }
}

@Preview(name = "Treino com dica offline", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HintIpsumPreview() {
    LoremTheme { IpsumScreen(previewAttempt.copy(hintRevealedAtEpochMillis = 2000, errorCount = 2),
        nowMillis = { 3_661_000 }, trackingState = IpsumTrackingUiState(ProfileLink.of("preview", 1), 1, null,
            SynchronizationState(lastSuccessEpochMillis = 1000, failure = "offline", failureKind = SyncFailureKind.NETWORK))) }
}

@Preview(name = "Resultado claro", widthDp = 360, heightDp = 800)
@Preview(name = "Resultado escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Resultado 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Composable
private fun ResultPreview() {
    LoremTheme { IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(previewAttempt.copy(status = IpsumStatus.COMPLETED,
        endedAtEpochMillis = 3_661_000, ratingDelta = 24, finalLoremRating = 1224, expectedTimeMillis = 5_400_000), emptyList())), {}) }
}

@Preview(name = "Resultado sem AC", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingResultPreview() {
    LoremTheme { IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(previewAttempt.copy(status = IpsumStatus.SOLVED_OUTSIDE,
        endedAtEpochMillis = 3_661_000, ratingDelta = -20, finalLoremRating = 1180, solvedOutsideAtEpochMillis = 1_700_000_000_000,
        failureReason = IpsumFailureReason.IMPLEMENTATION_NOT_COMPLETED), listOf("WRONG_ANSWER"))), {}) }
}

// Illustrative snapshots only; no preview state is used by the application.
private val previewAttempt = Ipsum(1, "preview", CodeforcesProblem(ProblemId(100, "A"), "Um problema para o próximo treino", 1500, setOf("dp", "graphs")),
    1200, IpsumCategory.CHALLENGE, 1300, 1400, 1500, 100, 1000, status = IpsumStatus.ACTIVE)
