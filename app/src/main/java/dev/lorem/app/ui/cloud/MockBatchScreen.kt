package dev.lorem.app.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestoreException
import dev.lorem.app.data.cloud.MOCK_ID
import dev.lorem.app.data.cloud.MockBatchReader
import dev.lorem.app.data.cloud.SavedMockHistory
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.ui.StatisticsUiState
import dev.lorem.app.ui.screens.StatisticsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Composable
internal fun MockBatchScreen(reader: MockBatchReader, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var requestedId by rememberSaveable { mutableStateOf<String?>(null) }
    var request by rememberSaveable { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember(reader) { mutableStateOf<SavedMockHistory?>(null) }

    LaunchedEffect(reader, requestedId, request) {
        val id = requestedId ?: return@LaunchedEffect
        busy = true
        error = null
        try {
            saved = withTimeout(30_000) { reader.load(id) }
            if (saved == null) error = "Lote não encontrado. Confira o ID, a conta Google e o projeto Firebase."
        } catch (_: TimeoutCancellationException) {
            error = "O Firestore demorou a responder. Confira a conexão e tente novamente."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: FirebaseFirestoreException) {
            error = if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Acesso recusado. Entre com a conta Google que gravou o lote e confira as regras do Firestore."
            } else {
                "Falha ao ler o lote no Firestore (${exception.code.name})."
            }
        } catch (_: Exception) {
            error = "O lote no Firestore está incompleto ou inconsistente."
        } finally {
            busy = false
        }
    }

    val history = saved
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Voltar ao histórico") }
            Text("Lote importado", style = MaterialTheme.typography.headlineSmall)
            if (history == null) {
                Text("Cole o ID do lote exibido na aba Importar do Lorem Mock.")
                OutlinedTextField(input, { input = it }, label = { Text("ID do lote") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Button(enabled = input.isNotBlank() && !busy, onClick = {
                    val id = input.trim()
                    if (MOCK_ID.matches(id)) {
                        requestedId = id
                        request++
                    } else {
                        error = "Copie o ID completo do lote, começando por mock-."
                    }
                }) { Text("Abrir lote") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else {
                Text("Histórico simulado · @${history.profile.handle}")
                TextButton(onClick = {
                    requestedId = null
                    saved = null
                    selectedTab = 0
                }) { Text("Abrir outro lote") }
            }
        }

        if (history != null) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Histórico") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Estatísticas") })
            }
            if (selectedTab == 0) MockBatchHistory(history)
            else {
                val stats = remember(history) { buildIpsumStatistics(history.profile, history.ipsums) }
                StatisticsScreen(StatisticsUiState.Ready(stats), onNavigateHome = onBack)
            }
        }
    }
}
