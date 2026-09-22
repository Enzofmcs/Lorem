package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.ui.navigation.LoremDestination

@Composable
fun ConfigurationScreen(
    persistedName: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var name by rememberSaveable(persistedName) { mutableStateOf(persistedName) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Configuração", style = MaterialTheme.typography.headlineMedium)
            Text("Dado local temporário para validar a persistência da estrutura básica.")
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome de teste") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
                onClick = { onSave(name) },
            ) {
                Text("Salvar e abrir Início")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = persistedName.isNotEmpty(),
                onClick = {
                    name = ""
                    onClear()
                },
            ) {
                Text("Apagar dado local")
            }
            LoremDestination.all
                .filterNot { it == LoremDestination.Configuration }
                .forEach { destination ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigate(destination.route) },
                    ) {
                        Text("Visitar ${destination.title}")
                    }
                }
        }
    }
}
