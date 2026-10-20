package dev.lorem.app.ui.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.domain.scoringExplanation
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CloudTrainingDestination(uid: String, repository: CloudTrainingRepository) {
    val model: CloudTrainingViewModel = viewModel(key = "cloud:$uid", factory = CloudTrainingViewModel.factory(uid, repository))
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(model.now()) }
    LaunchedEffect(lifecycle, model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.refresh()
            launch { while (true) { now = model.now(); delay(250) } }
            launch { while (true) { delay(15_000); model.refresh() } }
        }
    }
    val active = state.snapshot?.active
    val expired = active != null && (now == null || now!! >= active.expiresAtMillis)
    LaunchedEffect(active?.id, expired) { if (expired) model.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    CloudTrainingScreen(state, now, model::add, model::select, model::start, model::hint, model::close, model::refresh, model::upsolve)
        val selected = state.selectedKey
        if (selected != null && state.snapshot?.active == null) {
            dev.lorem.app.ui.components.IpsumStudyExportAction(
                prepare = { repository.exportStudy(uid, selected) },
                isCurrent = { repository.selectedHandle(uid) == selected })
        }
    }
}

@Composable
fun CloudTrainingScreen(
    state: CloudTrainingUiState, now: Long?, onAdd: (String) -> Unit, onSelect: (String) -> Unit,
    onStart: () -> Unit, onHint: () -> Unit, onClose: (IpsumFailureReason) -> Unit,
    onRefresh: () -> Unit, onUpsolve: (CloudIpsum) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var confirmHint by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf(IpsumFailureReason.LOGIC_NOT_FOUND) }
    val snapshot = state.snapshot
    val active = snapshot?.active
    val profile = snapshot?.handles?.firstOrNull { it.key == state.selectedKey }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("cloud-training")) {
        Text("Seus handles", style = MaterialTheme.typography.headlineSmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (snapshot?.online == false) Text("Dados salvos neste aparelho. Conecte-se para confirmar o progresso.")
        snapshot?.handles?.forEach { handle ->
            OutlinedButton(onClick = { onSelect(handle.key) }, enabled = !state.busy && active == null) {
                Text(handle.handle + if (handle.key == state.selectedKey) " · selecionado" else "")
            }
        }
        if (active == null) {
            OutlinedTextField(input, { input = it }, label = { Text("Handle Codeforces") }, singleLine = true,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onAdd(input) }, enabled = input.isNotBlank() && !state.busy) { Text("Adicionar handle") }
            profile?.let {
                Text(it.handle, style = MaterialTheme.typography.titleLarge)
                Text("Rating Lorem: ${it.rating}")
                Text("Faixa consolidada: ${it.consolidatedRating ?: "ainda sem amostra"}")
                Button(onClick = onStart, enabled = !state.busy && snapshot.online) { Text("Iniciar Ipsum · até 2h") }
            }
        } else {
            val closed = active.state != CloudIpsumState.ACTIVE || now == null || now >= active.expiresAtMillis
            Text("Ipsum · ${active.ipsum.ownerHandle}", style = MaterialTheme.typography.titleLarge)
            Text(active.ipsum.problem.name)
            val remaining = now?.let { (active.expiresAtMillis - it).coerceIn(0, IPSUM_LIMIT_MILLIS) }
            Text(if (closed) "Treino fechado · aguardando verificação" else "Restam ${formatDuration(remaining!!)}")
            if (!closed && remaining != null) Text("Tempo decorrido: ${formatDuration(IPSUM_LIMIT_MILLIS - remaining)}")
            if (closed) Text("O resultado será confirmado após verificar as submissões no prazo. Atualize quando houver conexão.")
            else {
                val uri = LocalUriHandler.current
                Button(onClick = { uri.openUri(active.ipsum.problemUrl()) }) { Text("Abrir problema no Codeforces") }
                if (active.ipsum.hintRevealedAtEpochMillis != null) Text("Dica: ${active.ipsum.problem.tags.sorted().joinToString()}")
                else OutlinedButton(onClick = { confirmHint = true }, enabled = !state.busy && snapshot.online) { Text("Revelar dica") }
                TextButton(onClick = { confirmClose = true }, enabled = !state.busy && snapshot.online) { Text("Encerrar tentativa") }
            }
        }
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Atualizar progresso") }
        val closed = snapshot?.attempts.orEmpty().filter { it.state == CloudIpsumState.FINAL }
        if (active == null && closed.isNotEmpty() && profile != null) {
            val stats = buildIpsumStatistics(LocalProfile(profile.handle, profile.handle, null, profile.rating,
                profile.consolidatedRating, 0), closed.map { it.ipsum }, closed.map { it.ipsum.problem })
            // Reuse the product's statistics calculations; each handle sees only its own attempts.
            Text("Histórico e estatísticas", style = MaterialTheme.typography.titleLarge)
            Text("${closed.size} tentativas · ${closed.count { it.ipsum.status == IpsumStatus.COMPLETED }} AC")
            Text("Com dica: ${stats.overall.withHint.accepted}/${stats.overall.withHint.attempts} AC · sem dica: ${stats.overall.withoutHint.accepted}/${stats.overall.withoutHint.attempts} AC")
            stats.overall.averageTimeMillis?.let { Text("Tempo médio: ${formatDuration(it.toLong())} (${stats.overall.timedAttempts} tentativas)") }
            stats.byRating.forEach { Text("Rating ${it.problemRating}: ${it.attempts.accepted}/${it.attempts.total} AC") }
            stats.byTag.forEach { Text("${it.tag}: ${it.attempts.accepted}/${it.attempts.total} AC") }
            closed.forEach { attempt ->
                val i = attempt.ipsum
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(i.problem.name, style = MaterialTheme.typography.titleMedium)
                        val status = when (i.status) {
                            IpsumStatus.COMPLETED -> "AC no prazo"
                            IpsumStatus.SOLVED_OUTSIDE -> "Resolvido depois do encerramento"
                            else -> "Pendente"
                        }
                        val category = when (i.category) {
                            IpsumCategory.FLUENCY -> "Fluência"
                            IpsumCategory.CURRENT_LEVEL -> "Nível atual"
                            IpsumCategory.CHALLENGE -> "Desafio"
                        }
                        Text("$status · ${i.selectedRating} · $category")
                        Text("${formatDuration(i.totalTimeMillis() ?: 0)} · ${i.errorCount} erros · dica: ${if (i.hintRevealedAtEpochMillis != null) "sim" else "não"}")
                        i.failureReason?.let { Text(it.label) }
                        Text("Rating ${i.initialLoremRating} → ${i.finalLoremRating} (${i.ratingDelta})")
                        Text(scoringExplanation(i.scoringSnapshot, i.timeGain, i.status == IpsumStatus.COMPLETED))
                        Text(i.problem.tags.sorted().joinToString())
                        if (i.status == IpsumStatus.PENDING) TextButton(onClick = { onUpsolve(attempt) }, enabled = !state.busy) {
                            Text("Verificar AC posterior")
                        }
                    }
                }
            }
        }
    }
    val canTrain = active != null && active.state == CloudIpsumState.ACTIVE && now != null && now < active.expiresAtMillis
    if (confirmHint && canTrain) AlertDialog(onDismissRequest = { confirmHint = false },
        title = { Text("Revelar dica?") }, text = { Text("As tags serão reveladas e o uso de dica contará na avaliação.") },
        confirmButton = { TextButton(onClick = { confirmHint = false; onHint() }) { Text("Revelar") } },
        dismissButton = { TextButton(onClick = { confirmHint = false }) { Text("Continuar tentando") } })
    if (confirmClose && canTrain) AlertDialog(onDismissRequest = { confirmClose = false },
        title = { Text("Encerrar tentativa?") }, text = {
            Column {
                Text("Escolha o motivo. Submissões posteriores não contarão para esta avaliação.")
                IpsumFailureReason.entries.forEach { value ->
                    TextButton(onClick = { reason = value }) { Text((if (reason == value) "✓ " else "") + value.label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { confirmClose = false; onClose(reason) }) { Text("Encerrar") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Continuar tentando") } })
}

private fun formatDuration(millis: Long): String = "%d:%02d:%02d".format(millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60)
