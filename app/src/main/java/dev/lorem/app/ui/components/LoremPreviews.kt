package dev.lorem.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.theme.LoremTheme

@Preview(name = "Base clara", widthDp = 360, heightDp = 800)
@Preview(name = "Base escura", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fonte 200%", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Paisagem escura", widthDp = 640, heightDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LoremFoundationPreview() {
    LoremTheme {
        LoremScaffold(LoremDestination.Home, true, false, {}, {}, {}) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LoremMetricCard("Rating Lorem", "1.200", supportingText = "Seu progresso no treino")
                LoremSyncStatus("Dados atualizados", LoremTone.SUCCESS, "Seu histórico está pronto para treinar.")
                LoremPrimaryAction("Iniciar novo Ipsum", {})
                LoremSyncStatus("Aguardando conexão", LoremTone.ATTENTION, "Você pode consultar os dados salvos.")
                LoremBadge("Resolvido no Ipsum", LoremTone.SUCCESS)
                LoremEmptyState("Seu histórico começa aqui", "Conclua um treino para acompanhar seu progresso.")
                LoremErrorState("Não foi possível atualizar. Tente novamente.")
            }
        }
    }
}
