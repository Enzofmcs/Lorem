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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.ui.navigation.LoremDestination

@Composable
fun PlaceholderScreen(
    destination: LoremDestination,
    persistedName: String?,
    onNavigate: (String) -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(destination.title, style = MaterialTheme.typography.headlineMedium)
            Text("Tela provisória da História 00.")
            if (destination == LoremDestination.Home && persistedName != null) {
                Text("Dado local restaurado: $persistedName")
            }
            LoremDestination.all.forEach { target ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(target.route) },
                    enabled = target.route != destination.route,
                ) {
                    Text("Abrir ${target.title}")
                }
            }
        }
    }
}
