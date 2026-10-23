package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.ui.IpsumTrackingUiState
import dev.lorem.app.ui.SubmissionCheckUiState
import dev.lorem.app.ui.components.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun IpsumScreen(
    ipsum: Ipsum?,
    onNavigateHome: () -> Unit = {},
    onRevealHint: () -> Unit = {},
    submissionCheckState: SubmissionCheckUiState = SubmissionCheckUiState.Idle,
    onVerifySubmissions: () -> Unit = {},
    onEndWithoutAc: (IpsumFailureReason?) -> Unit = {},
    nowMillis: () -> Long = System::currentTimeMillis,
    dataSourceNotice: String? = null,
    trackingState: IpsumTrackingUiState? = null,
    onMonitorIpsum: suspend (Long) -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val monitor by rememberUpdatedState(onMonitorIpsum)
    LaunchedEffect(ipsum?.id, lifecycle) {
        ipsum?.id?.let { dev.lorem.app.ui.monitorResumedIpsum(lifecycle, it) { id -> monitor(id) } }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoremBadge("Ipsum ativo", LoremTone.NEUTRAL)
        if (ipsum == null) {
            if (submissionCheckState !is SubmissionCheckUiState.Success || !submissionCheckState.completed) {
                CircularProgressIndicator()
                Text("Carregando o Ipsum salvo…")
            }
            SubmissionFeedback(submissionCheckState)
        } else {
            ActiveIpsumContent(ipsum, onRevealHint, submissionCheckState, onVerifySubmissions,
                onEndWithoutAc, nowMillis, trackingState)
        }
        dataSourceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onNavigateHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("Voltar ao início") }
    }
}

@Composable
private fun ActiveIpsumContent(
    ipsum: Ipsum,
    onRevealHint: () -> Unit,
    submissionCheckState: SubmissionCheckUiState,
    onVerifySubmissions: () -> Unit,
    onEndWithoutAc: (IpsumFailureReason?) -> Unit,
    nowMillis: () -> Long,
    trackingState: IpsumTrackingUiState?,
) {
    var dialog by rememberSaveable(ipsum.id) { mutableStateOf<IpsumDialog?>(null) }
    var selectedReason by rememberSaveable(ipsum.id) { mutableStateOf<IpsumFailureReason?>(null) }
    val uriHandler = LocalUriHandler.current
    Text(ipsum.problem.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Problema ${ipsum.problem.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    ElapsedTimeCard(ipsum.id, ipsum.startedAtEpochMillis, nowMillis)
    Text("O tempo segue contando ao sair da tela.", style = MaterialTheme.typography.bodySmall)
    LoremPrimaryAction("Abrir no Codeforces", { uriHandler.openUri(ipsum.problemUrl()) })

    val synchronization = trackingState?.takeIf { it.activeIpsumId == ipsum.id }?.synchronization
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acompanhando submissões automaticamente", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text("Erros definitivos: ${ipsum.errorCount}")
            val status = when {
                synchronization?.running == true || submissionCheckState == SubmissionCheckUiState.Loading -> "Consultando submissões no Codeforces…"
                synchronization?.failureKind == SyncFailureKind.NETWORK -> "Sem conexão · seu treino continua salvo."
                synchronization?.failureKind == SyncFailureKind.RATE_LIMIT -> "Aguardando o Codeforces permitir nova consulta."
                synchronization?.failure != null -> "Não foi possível consultar. Tentaremos novamente."
                else -> "A consulta é retomada automaticamente ao voltar para esta tela."
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Última consulta concluída: ${synchronization?.lastSuccessEpochMillis?.let(::formatSyncTime) ?: "nenhuma ainda"}",
                style = MaterialTheme.typography.bodySmall)
            SubmissionFeedback(submissionCheckState)
            OutlinedButton(onClick = onVerifySubmissions,
                enabled = submissionCheckState != SubmissionCheckUiState.Loading && synchronization?.running != true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (synchronization?.failure != null || submissionCheckState is SubmissionCheckUiState.Error) "Tentar novamente" else "Atualizar agora")
            }
        }
    }
    if (ipsum.hintRevealedAtEpochMillis == null) {
        Text("O rating, as tags e a categoria ficam ocultos durante o Ipsum.", style = MaterialTheme.typography.bodySmall)
        if (ipsum.virtualDuelSnapshot == null) OutlinedButton(onClick = { dialog = IpsumDialog.RevealHint }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Revelar tópicos")
        }
    } else {
        LoremBadge("Dica registrada", LoremTone.ATTENTION)
        Text("Tópicos: ${ipsum.problem.tags.sorted().joinToString()}")
        Text("O rating e a categoria continuam ocultos durante o Ipsum.", style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = { dialog = IpsumDialog.EndWithoutAc }, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Encerrar sem AC") }

    when (dialog) {
        IpsumDialog.RevealHint -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Revelar tópicos?") },
            text = { Text("O uso da dica será registrado e não poderá ser desfeito.") },
            confirmButton = { TextButton(onClick = { dialog = null; onRevealHint() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Revelar") } },
            dismissButton = { TextButton(onClick = { dialog = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
        IpsumDialog.EndWithoutAc -> BoundaryDialog(selectedReason, { selectedReason = it },
            { onEndWithoutAc(selectedReason) }, { dialog = null })
        null -> Unit
    }
}

@Composable
private fun ElapsedTimeCard(ipsumId: Long, startedAt: Long, nowMillis: () -> Long) {
    var now by remember(ipsumId) { mutableLongStateOf(nowMillis()) }
    val clock by rememberUpdatedState(nowMillis)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var consultedTime by rememberSaveable(ipsumId) { mutableStateOf<String?>(null) }
    LaunchedEffect(ipsumId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = clock(); delay(1_000) }
        }
    }
    fun consult() { consultedTime = formatElapsed(elapsedIpsumMillis(startedAt, clock())) }
    // The ticking text is visual. Accessibility reads a snapshot only on explicit request,
    // so a focused timer does not produce a content announcement every second.
    Card(onClick = ::consult,
        modifier = Modifier.fillMaxWidth().testTag("elapsed-timer").clearAndSetSemantics {
            role = Role.Button
            contentDescription = "Tempo decorrido. Ative para consultar o tempo atual."
            consultedTime?.let { stateDescription = "Última consulta: $it" }
            onClick(label = "Consultar tempo decorrido") { consult(); true }
        }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tempo decorrido", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val value = formatElapsed(elapsedIpsumMillis(startedAt, now))
            val measurer = rememberTextMeasurer()
            val base = MaterialTheme.typography.displayLarge
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val available = (constraints.maxWidth - 1).coerceAtLeast(1).toFloat()
                val width = measurer.measure(value, style = base, softWrap = false, maxLines = 1)
                    .multiParagraph.intrinsics.maxIntrinsicWidth.coerceAtLeast(1f)
                var adjustment by remember(value.length, constraints.maxWidth, base, density.density, density.fontScale) { mutableFloatStateOf(1f) }
                val scale = (available / width).coerceAtMost(1f) * adjustment
                Text(value, Modifier.testTag("elapsed-value"), softWrap = false, maxLines = 1,
                    style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
                    onTextLayout = { layout ->
                        // Resolve any difference between premeasurement and the actual font/density.
                        val actualWidth = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                        if (actualWidth > available) adjustment *= (available / actualWidth).coerceAtMost(0.99f)
                    })
            }
        }
    }
}

@Composable
private fun SubmissionFeedback(state: SubmissionCheckUiState) {
    when (state) {
        SubmissionCheckUiState.Idle, SubmissionCheckUiState.Loading -> Unit
        is SubmissionCheckUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
        is SubmissionCheckUiState.Success -> Text(when {
            state.completed -> "AC reconhecido. Ipsum encerrado com ${state.errorCount} erro(s)."
            state.newSubmissionCount == 0 -> "Nenhuma nova submissão definitiva encontrada."
            else -> "Submissões atualizadas: ${state.errorCount} erro(s) antes do AC."
        }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BoundaryDialog(
    selectedReason: IpsumFailureReason?,
    onSelect: (IpsumFailureReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Encerrar sem AC?") },
    text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            Text("Selecione exatamente um motivo:")
            IpsumFailureReason.entries.forEach { reason ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = selectedReason == reason, onClick = { onSelect(reason) }, role = Role.RadioButton)
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedReason == reason, onClick = null)
                    Text(reason.label, Modifier.weight(1f))
                }
            }
        }
    },
    confirmButton = { TextButton(enabled = selectedReason != null, onClick = { onConfirm(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Encerrar") } },
    dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
)

private enum class IpsumDialog { RevealHint, EndWithoutAc }

fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3_600, totalSeconds % 3_600 / 60, totalSeconds % 60)
}
