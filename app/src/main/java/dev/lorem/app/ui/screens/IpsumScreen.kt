package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.elapsedIpsumMillis
import dev.lorem.app.domain.model.problemUrl
import dev.lorem.app.ui.SubmissionCheckUiState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
) {
    var now by remember(ipsum?.id) { mutableLongStateOf(nowMillis()) }
    var dialog by remember { mutableStateOf<IpsumDialog?>(null) }
    var selectedReason by remember(ipsum?.id) { mutableStateOf<IpsumFailureReason?>(null) }
    val uriHandler = LocalUriHandler.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ipsum != null) onVerifySubmissions()
    }

    LaunchedEffect(ipsum?.id) {
        while (true) {
            now = nowMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ipsum ativo", style = MaterialTheme.typography.headlineMedium)
        Button(modifier = Modifier.fillMaxWidth(), onClick = onNavigateHome) {
            Text("Voltar para o início")
        }
        if (ipsum == null) {
            if (submissionCheckState !is SubmissionCheckUiState.Success || !submissionCheckState.completed) {
                Text("Carregando o Ipsum salvo…")
            }
        } else {
            Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge)
            Text("Problema ${ipsum.problem.id}")
            Text("Tempo decorrido: ${formatElapsed(elapsedIpsumMillis(ipsum.startedAtEpochMillis, now))}")
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { uriHandler.openUri(ipsum.problemUrl()) },
            ) { Text("Abrir problema no Codeforces") }

            if (ipsum.hintRevealedAtEpochMillis == null) {
                Text("O rating, as tags e a categoria ficam ocultos durante o Ipsum.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { dialog = IpsumDialog.RevealHint },
                ) { Text("Revelar tópicos") }
            } else {
                Text("Tópicos: ${ipsum.problem.tags.sorted().joinToString()}")
                Text("O rating e a categoria continuam ocultos durante o Ipsum.")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = submissionCheckState != SubmissionCheckUiState.Loading,
                onClick = onVerifySubmissions,
            ) {
                if (submissionCheckState == SubmissionCheckUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Text("Verificar submissões")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { dialog = IpsumDialog.EndWithoutAc },
            ) { Text("Encerrar sem AC") }
        }
        when (submissionCheckState) {
            SubmissionCheckUiState.Idle -> Unit
            SubmissionCheckUiState.Loading -> Text("Consultando submissões no Codeforces…")
            is SubmissionCheckUiState.Error -> Text(submissionCheckState.message, color = MaterialTheme.colorScheme.error)
            is SubmissionCheckUiState.Success -> Text(
                if (submissionCheckState.completed) {
                    "AC reconhecido. Ipsum encerrado com ${submissionCheckState.errorCount} erro(s)."
                } else if (submissionCheckState.newSubmissionCount == 0) {
                    "Nenhuma nova submissão definitiva encontrada."
                } else {
                    "Submissões atualizadas: ${submissionCheckState.errorCount} erro(s) antes do AC."
                },
            )
        }
    }

    when (dialog) {
        IpsumDialog.RevealHint -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Revelar tópicos?") },
            text = { Text("O uso da dica será registrado e não poderá ser desfeito.") },
            confirmButton = {
                TextButton(onClick = { dialog = null; onRevealHint() }) { Text("Revelar") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
        IpsumDialog.EndWithoutAc -> BoundaryDialog(
            selectedReason = selectedReason,
            onSelect = { selectedReason = it },
            onConfirm = { onEndWithoutAc(selectedReason) },
            onDismiss = { dialog = null },
        )
        null -> Unit
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("Selecione exatamente um motivo:")
            IpsumFailureReason.entries.forEach { reason ->
                androidx.compose.foundation.layout.Row {
                    RadioButton(selected = selectedReason == reason, onClick = { onSelect(reason) })
                    Text(reason.label, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    },
    confirmButton = {
        TextButton(enabled = selectedReason != null, onClick = { onConfirm(); onDismiss() }) { Text("Encerrar") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
)

private enum class IpsumDialog { RevealHint, EndWithoutAc }

fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}
