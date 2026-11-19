package dev.lorem.app.mock

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dev.lorem.app.BuildConfig
import dev.lorem.app.domain.buildIpsumHistory
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.ui.StatisticsUiState
import dev.lorem.app.ui.screens.StatisticsScreen
import dev.lorem.app.ui.theme.LoremTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val SHEET_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MockImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cloud = if (BuildConfig.MOCK_USE_EMULATOR) null else mockCloudFirebase(this)
        setContent {
            LoremTheme {
                if (BuildConfig.MOCK_USE_EMULATOR) {
                    MockImportApp(mockImportViewModel(), "Firestore Emulator · porta 8082")
                } else {
                    MockCloudLogin(cloud)
                }
            }
        }
    }
}

internal data class ImportState(
    val workbook: MockWorkbook? = null,
    val saved: SavedMockHistory? = null,
    val message: String? = null,
    val error: Boolean = false,
    val busy: Boolean = false,
    val hasLast: Boolean = false,
)

internal class MockImportViewModel(
    app: Application,
    private val firebase: FirebaseApp?,
    uid: String?,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("mock-import", Context.MODE_PRIVATE)
    private val storageKey = if (firebase == null) "last-dataset" else "last-dataset.${firebase.options.projectId}.$uid"
    private val store by lazy {
        if (firebase == null) MockFirestore(mockFirestoreClient(app))
        else MockFirestore.forUser(FirebaseFirestore.getInstance(firebase), requireNotNull(uid))
    }
    val state = MutableStateFlow(ImportState(hasLast = lastId() != null))

    init {
        if (lastId() != null) reload()
    }

    fun choose(uri: Uri) {
        if (state.value.busy) return
        state.value = state.value.copy(workbook = null)
        run("Validando a planilha…") {
            val workbook = withContext(Dispatchers.IO) {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw WorkbookException("Não foi possível abrir o arquivo selecionado.")
                stream.use(::readMockWorkbook)
            }
            state.value = state.value.copy(workbook = workbook,
                message = "${workbook.ipsums.size} linhas válidas. Confira a prévia e confirme a gravação.")
        }
    }

    fun save() {
        val workbook = state.value.workbook ?: return
        run("Gravando e conferindo o lote no Firestore…") {
            val result = withTimeout(45_000) { store.import(workbook) }
            prefs.edit().putString(storageKey, result.history.id).apply()
            val message = if (result.inserted) "200 Ipsums gravados e conferidos no Firestore."
                else "Este lote já estava no Firestore. Foram conferidos 200 Ipsums, sem duplicatas."
            state.value = state.value.copy(saved = result.history, hasLast = true, message = message)
        }
    }

    fun reload() {
        val id = lastId() ?: return
        run("Buscando o último lote no Firestore…") {
            val result = withTimeout(30_000) { store.load(id) }
            if (result == null) {
                prefs.edit().remove(storageKey).apply()
                state.value = state.value.copy(saved = null, hasLast = false,
                    message = "O último lote não está no Firestore. Selecione a planilha para importá-lo.")
            } else {
                state.value = state.value.copy(saved = result, message = "200 Ipsums conferidos no Firestore.")
            }
        }
    }

    private fun lastId(): String? = prefs.getString(storageKey, null)?.takeIf(MOCK_ID::matches)

    private fun connectionError(): String = if (firebase == null)
        "Confira o emulador e o adb reverse e tente novamente." else
        "Confira a internet e tente novamente com a mesma planilha."

    private fun run(message: String, action: suspend () -> Unit) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = false, message = message)
        viewModelScope.launch {
            try {
                action()
            } catch (_: TimeoutCancellationException) {
                showError("O Firestore não respondeu a tempo. " + connectionError())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: WorkbookException) {
                showError(exception.message.orEmpty())
            } catch (exception: FirebaseFirestoreException) {
                val message = when {
                    exception.code != FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "A conexão com o Firestore falhou (${exception.code.name}). " + connectionError()
                    firebase == null ->
                        "O Firestore recusou a operação. Inicie o emulador com o arquivo firebase.json do Lorem Mock."
                    else -> "O Firestore recusou a operação. Confira as regras do importador no projeto exibido no app."
                }
                showError(message)
            } catch (exception: IllegalStateException) {
                showError(exception.message ?: "Não foi possível confirmar o lote no Firestore.")
            } catch (_: Exception) {
                showError("Não foi possível concluir a operação. Confira o formato .xlsx ou a conexão com o Firestore.")
            } finally {
                state.value = state.value.copy(busy = false)
            }
        }
    }

    private fun showError(message: String) {
        state.value = state.value.copy(error = true, message = message)
    }
}

@Composable
internal fun mockImportViewModel(firebase: FirebaseApp? = null, uid: String? = null): MockImportViewModel =
    viewModel(key = "lorem-mock/${firebase?.options?.projectId ?: MOCK_PROJECT}/${uid.orEmpty()}",
        factory = viewModelFactory {
            initializer { MockImportViewModel(checkNotNull(this[APPLICATION_KEY]), firebase, uid) }
        })

@Composable
internal fun MockImportApp(model: MockImportViewModel, target: String, onSignOut: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.choose(uri)
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    val saved = state.saved
    val currentTab = if (saved == null) 0 else tab
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (saved != null) {
                TabRow(selectedTabIndex = currentTab) {
                    listOf("Importar", "Histórico", "Estatísticas").forEachIndexed { index, label ->
                        Tab(selected = index == currentTab, onClick = { tab = index }, text = { Text(label) })
                    }
                }
            }
            when (currentTab) {
                1 -> if (saved != null) MockHistory(saved)
                2 -> if (saved != null) {
                    StatisticsScreen(StatisticsUiState.Ready(buildIpsumStatistics(saved.profile, saved.ipsums)),
                        onNavigateHome = { tab = 0 })
                }
                else -> ImportPanel(state, target,
                    { pickFile.launch(arrayOf(SHEET_MIME, "application/octet-stream")) },
                    model::save, model::reload, onSignOut)
            }
        }
    }
}

@Composable
private fun ImportPanel(
    state: ImportState,
    target: String,
    onChoose: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onSignOut: (() -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Lorem Mock", style = MaterialTheme.typography.headlineLarge)
            Text(target, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onChoose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Selecionar Excel (.xlsx)")
            }
        }
        if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.message?.let { message ->
            item {
                val color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Text(message, color = color)
            }
        }
        state.workbook?.let { workbook ->
            item {
                val byResult = workbook.ipsums.groupingBy(MockIpsum::outcome).eachCount()
                Text("Prévia do lote", style = MaterialTheme.typography.titleMedium)
                Text("${byResult[MockOutcome.AC] ?: 0} AC · ${byResult[MockOutcome.PENDENTE] ?: 0} pendentes · ${byResult[MockOutcome.UPSOLVE] ?: 0} upsolves")
            }
            items(workbook.ipsums.take(3)) { row ->
                Text("${row.contestId}${row.index} · ${row.name}")
            }
            item {
                Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Gravar 200 Ipsums")
                }
            }
        }
        state.saved?.let {
            item {
                Text("ID do lote gravado", style = MaterialTheme.typography.titleSmall)
                SelectionContainer { Text(it.id) }
            }
        }
        if (state.hasLast) item {
            OutlinedButton(onClick = onReload, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Recarregar último lote")
            }
        }
        if (onSignOut != null) item {
            OutlinedButton(onClick = onSignOut, enabled = !state.busy) { Text("Sair da conta Google") }
        }
    }
}

@Composable
private fun MockHistory(saved: SavedMockHistory) {
    val history = buildIpsumHistory(saved.ipsums)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Histórico · ${history.size} Ipsums", style = MaterialTheme.typography.headlineSmall) }
        items(history, key = { it.ipsum.id }) { item ->
            val ipsum = item.ipsum
            Text("${ipsum.problem.id.contestId}${ipsum.problem.id.index} · ${ipsum.problem.name}",
                style = MaterialTheme.typography.titleMedium)
            Text("${item.situation.label} · Rating ${ipsum.selectedRating}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top = 10.dp))
        }
    }
}
