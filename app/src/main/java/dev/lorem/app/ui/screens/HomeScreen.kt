package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.ConsolidationProgress
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.ui.PreparationUiState
import dev.lorem.app.ui.StartIpsumUiState
import dev.lorem.app.ui.components.*
import dev.lorem.app.ui.navigation.LoremDestination

@Composable
fun HomeScreen(
    profile: LocalProfile,
    catalogReady: Boolean,
    onNavigate: (String) -> Unit,
    hasActiveIpsum: Boolean = false,
    latestFinishedIpsumId: Long? = null,
    finishedIpsumCount: Int = 0,
    acceptedIpsumCount: Int = 0,
    startIpsumState: StartIpsumUiState = StartIpsumUiState.Idle,
    onStartIpsum: () -> Unit = {},
    consolidation: ConsolidationProgress? = null,
    preparation: PreparationUiState? = null,
    dataSourceNotice: String? = null,
) {
    var showConsolidation by rememberSaveable(profile.handle) { mutableStateOf(false) }
    val blockedReason = when {
        hasActiveIpsum -> null
        profile.lastSyncEpochMillis <= 0 -> "Seu histórico está sendo preparado. A primeira importação precisa terminar para garantir um problema inédito."
        !catalogReady && preparation?.catalog?.lastSuccessEpochMillis != null -> "Nenhum problema com data de origem verificada desde 01/07/2020 está disponível. Atualize o catálogo em Configurações."
        !catalogReady -> "Estamos preparando os problemas do seu treino. Aguarde o catálogo ficar disponível."
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Olá, ${profile.displayName}", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text("@${profile.handle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LoremMetricCard("Rating Lorem", profile.loremRating.toString(),
            supportingText = "Rating oficial Codeforces: ${profile.officialRating ?: "não disponível"} · referência do perfil")
        LoremPrimaryAction(
            label = if (hasActiveIpsum) "Continuar Ipsum" else "Iniciar Ipsum",
            onClick = { if (hasActiveIpsum) onNavigate(LoremDestination.Ipsum.route) else onStartIpsum() },
            enabled = blockedReason == null,
            busy = !hasActiveIpsum && startIpsumState == StartIpsumUiState.Loading,
        )
        if (hasActiveIpsum) {
            Text("Seu treino continua de onde parou. O tempo segue contando.", style = MaterialTheme.typography.bodySmall)
        } else {
            blockedReason?.let { Text(it) }
            when (startIpsumState) {
                StartIpsumUiState.Loading -> Text("Escolhendo e salvando um problema…")
                is StartIpsumUiState.Error -> LoremErrorState(startIpsumState.message)
                else -> Unit
            }
        }
        preparation?.let { PreparationSummary(it) }
        if (blockedReason != null) {
            TextButton(onClick = { onNavigate(LoremDestination.Configuration.route) }) { Text("Ver preparação") }
        }
        dataSourceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seus treinos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (finishedIpsumCount == 0) {
                    Text("Seu primeiro Ipsum começa aqui.")
                    Text("Ao encerrar um treino, você verá seu resumo e resultado.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Ipsums encerrados: $finishedIpsumCount")
                    Text("AC durante o Ipsum: $acceptedIpsumCount de $finishedIpsumCount")
                }
                latestFinishedIpsumId?.let { id ->
                    OutlinedButton(onClick = { onNavigate("${LoremDestination.Result.route}/$id") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver último resultado") }
                }
            }
        }
        consolidation?.let {
            ConsolidationCard(it, compact = !showConsolidation)
            TextButton(onClick = { showConsolidation = !showConsolidation }) {
                Text(if (showConsolidation) "Recolher progresso da faixa" else "Ver progresso da faixa")
            }
        }
    }
}

internal fun formatSyncTime(epochMillis: Long): String = java.time.Instant.ofEpochMilli(epochMillis)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
