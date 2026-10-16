package dev.lorem.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.ConfigurationUiState
import dev.lorem.app.ui.PreparationUiState
import dev.lorem.app.ui.theme.LoremTheme

@Preview(name = "Primeiro acesso", widthDp = 360, heightDp = 800)
@Preview(name = "Primeiro acesso escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Conexão fonte 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Composable
private fun ConfigurationPreview() {
    LoremTheme { ConfigurationScreen(ConfigurationUiState(handle = "tourist"), null, {}, {}) }
}

@Preview(name = "Início", widthDp = 360, heightDp = 800)
@Preview(name = "Início escuro", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Início fonte 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Início paisagem", widthDp = 640, heightDp = 360)
@Composable
private fun HomePreview() {
    LoremTheme { HomeScreen(previewProfile, true, {}, finishedIpsumCount = 12, acceptedIpsumCount = 7, latestFinishedIpsumId = 12) }
}

@Preview(name = "Continuar offline", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveHomePreview() {
    LoremTheme { HomeScreen(previewProfile, true, {}, hasActiveIpsum = true, preparation = previewPreparation) }
}

@Preview(name = "Preparação pendente", widthDp = 360, heightDp = 800)
@Composable
private fun PreparingHomePreview() {
    LoremTheme { HomeScreen(previewProfile.copy(lastSyncEpochMillis = 0), false, {}, preparation = PreparationUiState(ProfileLink.of("tourist", 1))) }
}

@Preview(name = "Configurações offline", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    LoremTheme { ConfigurationScreen(ConfigurationUiState(handle = "tourist"), previewProfile, {}, {}, preparation = previewPreparation) }
}

// Illustrative preview data never enters the application flow.
private val previewProfile = LocalProfile("tourist", "Tourist", 1500, 1320, null, 1_700_000_000_000)
private val previewPreparation = PreparationUiState(ProfileLink.of("tourist", 1),
    history = SynchronizationState(lastSuccessEpochMillis = 1_700_000_000_000,
        failure = "offline", failureKind = SyncFailureKind.NETWORK),
    catalog = SynchronizationState(lastSuccessEpochMillis = 1_700_000_000_000))
