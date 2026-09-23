package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.Ipsum

@Composable
fun IpsumScreen(
    ipsum: Ipsum?,
    onNavigateHome: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ipsum ativo", style = MaterialTheme.typography.headlineMedium)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateHome,
        ) {
            Text("Voltar para o início")
        }
        if (ipsum == null) {
            Text("Carregando o Ipsum salvo…")
        } else {
            Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge)
            Text("Problema ${ipsum.problem.id}")
            Text("O rating, as tags e a categoria ficam ocultos durante o Ipsum.")
        }
    }
}
