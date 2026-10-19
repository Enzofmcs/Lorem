package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.ConfigurationUiState
import dev.lorem.app.ui.PreparationUiState
import dev.lorem.app.ui.components.LoremPrimaryAction

@Composable
fun ConfigurationScreen(
    state: ConfigurationUiState,
    existingProfile: LocalProfile?,
    onHandleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    preparation: PreparationUiState? = null,
    onRefreshHistory: () -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
    onRefreshPending: () -> Unit = {},
    onRefreshProfile: () -> Unit = {},
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
    themeError: String? = null,
    studyExportAction: @Composable () -> Unit = {},
) {
    var showChangeConfirmation by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val canConfirm = state.handle.isNotBlank() && !state.isLoading
    val isChanging = existingProfile != null &&
        !existingProfile.handle.equals(state.handle.trim(), ignoreCase = true)
    fun connect() {
        if (!canConfirm) return
        focus.clearFocus()
        if (isChanging) showChangeConfirmation = true else onConfirm()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (existingProfile == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lorem", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
                Text("Um problema. Um treino. Seu próximo passo.", style = MaterialTheme.typography.titleLarge)
                Text("Conecte seu perfil para receber problemas inéditos e acompanhar sua evolução.")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (existingProfile == null) "Conecte seu Codeforces" else "Perfil",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                existingProfile?.let {
                    Text("Conectado como @${it.handle}")
                    Text("Rating oficial: ${it.officialRating ?: "não disponível"}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Usamos somente dados públicos do Codeforces. Não é necessária uma senha.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.handle,
                    onValueChange = onHandleChange,
                    enabled = !state.isLoading,
                    label = { Text("Handle do Codeforces") },
                    prefix = { Text("@") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    isError = state.errorMessage != null,
                    supportingText = state.errorMessage?.let { message -> { Text(message) } },
                )
                LoremPrimaryAction(
                    label = if (state.isLoading) "Validando perfil…" else if (existingProfile == null) "Conectar Codeforces" else "Atualizar handle",
                    enabled = canConfirm, busy = state.isLoading, onClick = ::connect,
                )
            }
        }
        if (existingProfile != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preparação e sincronização", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Text("Tudo é preparado automaticamente. Você pode acompanhar cada etapa e tentar novamente quando precisar.",
                    style = MaterialTheme.typography.bodySmall)
                if (preparation == null) {
                    Text("Carregando o estado salvo da preparação…")
                } else {
                    PreparationSummary(preparation)
                    PreparationStep("Histórico", preparation.history, onRefreshHistory, "Atualizar histórico agora")
                    PreparationStep("Catálogo", preparation.catalog, onRefreshCatalog, "Atualizar catálogo agora")
                    PreparationStep("Pendências", preparation.pending, onRefreshPending, "Atualizar pendências agora")
                    PreparationStep("Perfil", preparation.metadata, onRefreshProfile, "Atualizar perfil agora")
                }
            }
            studyExportAction()
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePicker(themePreference, onThemeChange, themeError)
            }
        }
    }
    if (showChangeConfirmation) {
        AlertDialog(
            onDismissRequest = { showChangeConfirmation = false },
            title = { Text("Trocar handle?") },
            text = { Text("O perfil local atual será substituído depois que o novo handle for validado.") },
            confirmButton = {
                TextButton(onClick = { showChangeConfirmation = false; onConfirm() }, enabled = canConfirm) { Text("Trocar") }
            },
            dismissButton = { TextButton(onClick = { showChangeConfirmation = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PreparationStep(label: String, state: SynchronizationState, onRefresh: () -> Unit, action: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SynchronizationStage(label, state)
            OutlinedButton(onClick = onRefresh, enabled = !state.running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(action) }
        }
    }
}
