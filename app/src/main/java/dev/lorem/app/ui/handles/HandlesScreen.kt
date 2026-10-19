package dev.lorem.app.ui.handles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.domain.handles.*

@Composable
fun AccountHandlesDestination(uid: String, repository: HandlesRepository) {
    val owner = remember(uid) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model: HandlesViewModel = viewModel(viewModelStoreOwner = owner,
        factory = remember(uid, repository) { HandlesViewModel.factory(uid, repository) })
    val state by model.state.collectAsStateWithLifecycle()
    HandlesScreen(state, model::input, model::add, model::select, model::refresh)
}

@Composable
fun HandlesScreen(
    state: HandlesUiState,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onSelect: (AccountHandle) -> Unit,
    onRetry: () -> Unit,
) {
    val snapshot = state.snapshot
    val canAdd = snapshot.serverConfirmed && !state.busy && state.input.isNotBlank()
    Column(Modifier.fillMaxWidth().testTag("handle-onboarding"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Seus handles", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Adicione seus handles do Codeforces. Cada novo vínculo começa com 800 de Rating Lorem.")
        OutlinedTextField(state.input, onInput, label = { Text("Handle do Codeforces") },
            singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("handle-input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd() }))
        Button(onClick = onAdd, enabled = canAdd, modifier = Modifier.testTag("add-handle")) {
            Text(if (state.busy) "Salvando…" else "Adicionar handle")
        }
        if (state.loading) CircularProgressIndicator()
        if (snapshot.locked) Text("Você tem um Ipsum em andamento. A troca de handle está bloqueada.")
        if (!snapshot.serverConfirmed) Text("Aguardando conexão para confirmar seus handles.")
        state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("handle-error")) }
        if (state.failure != null || !snapshot.serverConfirmed) {
            TextButton(onClick = onRetry, enabled = !state.busy) { Text("Tentar novamente") }
        }
        if (snapshot.handles.isEmpty() && snapshot.serverConfirmed) Text("Você ainda não cadastrou nenhum handle.")
        snapshot.handles.forEach { profile ->
            val selected = profile == snapshot.selected
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { onSelect(profile) },
                        enabled = snapshot.serverConfirmed && !state.busy && !snapshot.locked,
                        modifier = Modifier.testTag("select-handle-${profile.identity.key}"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.identity.canonical, style = MaterialTheme.typography.titleMedium)
                        if (!snapshot.locked) {
                            Text("Rating Lorem: ${profile.loremRating}")
                            profile.consolidatedRating?.let { Text("Faixa consolidada: $it") }
                        }
                        if (selected) Text("Selecionado", modifier = Modifier.testTag("selected-handle"))
                    }
                }
            }
        }
        snapshot.selected?.let { Text("Handle selecionado: ${it.identity.canonical}") }
    }
}

private fun HandleFailure.message() = when (this) {
    HandleFailure.INVALID_HANDLE -> "Digite um handle válido do Codeforces."
    HandleFailure.NOT_FOUND -> "Esse handle não foi encontrado no Codeforces."
    HandleFailure.CODEFORCES -> "Não foi possível consultar o Codeforces. Tente novamente."
    HandleFailure.NETWORK -> "Não foi possível confirmar a operação. Verifique a conexão e tente novamente."
    HandleFailure.RATE_LIMITED -> "O Codeforces pediu uma pausa nas consultas. Tente novamente em instantes."
    HandleFailure.CONFIGURATION -> "O serviço de handles está indisponível. Tente novamente."
    HandleFailure.INVALID_SESSION -> "Sua sessão mudou. Entre novamente para acessar seus handles."
    HandleFailure.QUOTA -> "O limite de uso do serviço foi atingido. Tente novamente mais tarde."
    HandleFailure.IPSUM_ACTIVE -> "Encerre o Ipsum em andamento antes de trocar de handle."
}
