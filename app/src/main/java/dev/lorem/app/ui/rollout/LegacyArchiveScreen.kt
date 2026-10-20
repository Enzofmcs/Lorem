package dev.lorem.app.ui.rollout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.lorem.app.domain.rollout.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.components.IpsumStudyExportAction
import kotlinx.coroutines.*

@Composable
fun LegacyArchiveDestination(migration: LegacyMigration) {
    var archive by remember(migration) { mutableStateOf<LegacyArchive?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun update(reason: IpsumFailureReason? = null) {
        if (busy) return
        busy = true
        scope.launch {
            try { archive = if (reason == null) migration.reconcile() else migration.close(reason); error = null }
            catch (e: Exception) { if (e is CancellationException) throw e; error = "Não foi possível verificar o treino anterior. Tente novamente com conexão." }
            finally { busy = false }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(migration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            update()
            while (true) { delay(15_000); if (archive?.ready != true) update() }
        }
    }
    if (archive?.let { it.ready && it.attempts.isEmpty() && it.profile == null } == true) return
    Card(Modifier.fillMaxWidth().testTag("legacy-archive")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Arquivo local deste aparelho", style = MaterialTheme.typography.titleMedium)
            Text("Seus registros anteriores ficam preservados aqui, somente para consulta. Eles não pertencem à conta Google conectada e não são enviados ao Firebase.")
            Text("Cada novo handle na nuvem começa em 800. Um handle já cadastrado recupera seu saldo; problemas antigos continuam excluídos das recomendações.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || archive == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            val old = archive
            if (old?.active != null) {
                Text("Treino anterior · ${old.active.problem.name}")
                Text("O início original foi preservado. O limite é de duas horas; um novo treino aguarda esta verificação.")
                val uri = LocalUriHandler.current
                if (old.boundaryMillis == null && System.currentTimeMillis() < old.active.startedAtEpochMillis + 7_200_000) {
                    OutlinedButton(onClick = { uri.openUri(old.active.problemUrl()) }) { Text("Abrir problema anterior") }
                    TextButton(onClick = { closing = true }, enabled = !busy) { Text("Encerrar tentativa anterior") }
                } else Text("Tentativa fechada · aguardando verificar submissões no prazo.")
            }
            if (old?.ready == true && old.attempts.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher arquivo" else "Consultar ${old.attempts.size} tentativas anteriores") }
                if (expanded) {
                    old.profile?.let { Text("${it.handle} · Rating anterior: ${it.loremRating} · faixa: ${it.consolidatedRating ?: "sem amostra"}") }
                    old.attempts.groupBy { it.ownerHandle }.forEach { (handle, attempts) ->
                        Text(handle, style = MaterialTheme.typography.titleSmall)
                        attempts.forEach { i ->
                            val status = when (i.status) {
                                IpsumStatus.COMPLETED -> "AC na tentativa"
                                IpsumStatus.SOLVED_OUTSIDE -> "Resolvido após o encerramento"
                                else -> "Pendente"
                            }
                            val seconds = (i.totalTimeMillis() ?: 0) / 1000
                            val duration = "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
                            Text("${i.problem.name} · $status · ${i.initialLoremRating} → ${i.finalLoremRating ?: "sem avaliação"}")
                            Text("$duration · ${if (i.hintRevealedAtEpochMillis != null) "Com dica" else "Sem dica"} · ${i.failureReason?.label ?: ""}")
                        }
                        IpsumStudyExportAction(prepare = { migration.export(handle) }, isCurrent = { true })
                    }
                }
            }
            if (old?.ready != true || error != null) TextButton(onClick = { update() }, enabled = !busy) { Text("Verificar treino anterior") }
        }
    }
    if (closing) AlertDialog(onDismissRequest = { closing = false }, title = { Text("Por que encerrar a tentativa anterior?") },
        text = { Column { IpsumFailureReason.entries.forEach { reason -> TextButton(onClick = { closing = false; update(reason) }) { Text(reason.label) } } } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { closing = false }) { Text("Continuar tentando") } })
}
