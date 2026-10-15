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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.ui.ConfigurationUiState

@Composable
fun ConfigurationScreen(
    state: ConfigurationUiState,
    existingProfile: LocalProfile?,
    onHandleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    preparation: dev.lorem.app.ui.PreparationUiState? = null,
    onRefreshHistory: () -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
    onRefreshPending: () -> Unit = {},
    onRefreshProfile: () -> Unit = {},
) {
    var showChangeConfirmation by remember { mutableStateOf(false) }
    val isChanging = existingProfile != null &&
        !existingProfile.handle.equals(state.handle.trim(), ignoreCase = true)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Vincular Codeforces", style = MaterialTheme.typography.headlineMedium)
            Text("Informe seu handle público para validar e salvar seu perfil neste dispositivo.")
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.handle,
                onValueChange = onHandleChange,
                enabled = !state.isLoading,
                label = { Text("Handle do Codeforces") },
                singleLine = true,
                isError = state.errorMessage != null,
                supportingText = state.errorMessage?.let { message ->
                    { Text(message) }
                },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.handle.isNotBlank() && !state.isLoading,
                onClick = {
                    if (isChanging) showChangeConfirmation = true else onConfirm()
                },
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(if (existingProfile == null) "Validar e abrir Início" else "Atualizar handle")
                }
            }
            if (existingProfile != null && preparation != null) {
                PreparationStatus(preparation)
                androidx.compose.material3.OutlinedButton(onClick = onRefreshHistory, enabled = !preparation.history.running) { Text("Atualizar histórico agora") }
                androidx.compose.material3.OutlinedButton(onClick = onRefreshCatalog, enabled = !preparation.catalog.running) { Text("Atualizar catálogo agora") }
                androidx.compose.material3.OutlinedButton(onClick = onRefreshPending, enabled = !preparation.pending.running) { Text("Atualizar pendências agora") }
                androidx.compose.material3.OutlinedButton(onClick = onRefreshProfile, enabled = !preparation.metadata.running) { Text("Atualizar perfil agora") }
            }
        }
    }
    if (showChangeConfirmation) {
        AlertDialog(
            onDismissRequest = { showChangeConfirmation = false },
            title = { Text("Trocar handle?") },
            text = { Text("O perfil local atual será substituído depois que o novo handle for validado.") },
            confirmButton = {
                TextButton(onClick = {
                    showChangeConfirmation = false
                    onConfirm()
                }) { Text("Trocar") }
            },
            dismissButton = {
                TextButton(onClick = { showChangeConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}
