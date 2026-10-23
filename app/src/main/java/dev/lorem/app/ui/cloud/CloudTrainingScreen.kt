package dev.lorem.app.ui.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.lorem.app.domain.*
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.*
import dev.lorem.app.ui.components.LoremScaffold
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Authoritative closed data only, checked again at the UI boundary. */
internal fun CloudTrainingUiState.activeAttempt(): CloudIpsum? {
    val profile = snapshot?.handles?.firstOrNull { it.key == selectedKey } ?: return null
    return snapshot.active?.takeIf { it.handleKey == profile.key && it.bindingVersion == profile.bindingVersion &&
        it.ipsum.ownerHandle.equals(profile.handle, true) }
}

internal fun CloudTrainingUiState.closedAttempts(): List<CloudIpsum> {
    val profile = snapshot?.handles?.firstOrNull { it.key == selectedKey } ?: return emptyList()
    return snapshot.attempts.filter { it.handleKey == profile.key && it.bindingVersion == profile.bindingVersion &&
        it.state == CloudIpsumState.FINAL && it.ipsum.ownerHandle.equals(profile.handle, true) }
}

@Composable
fun CloudTrainingDestination(uid: String, repository: CloudTrainingRepository,
    theme: ThemePreference = ThemePreference.SYSTEM, onThemeChange: (ThemePreference) -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
) {
    val model: CloudTrainingViewModel = viewModel(key = "cloud:$uid", factory = CloudTrainingViewModel.factory(uid, repository))
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember(model) { mutableStateOf(model.now()) }
    val latest by rememberUpdatedState(state)
    LaunchedEffect(lifecycle, model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.refresh()
            launch { while (true) { now = model.now(); delay(250) } }
            launch { while (true) {
                delay(15_000)
                if (latest.snapshot?.active != null) model.refresh()
                else model.updatePending()
            } }
        }
    }
    val active = state.snapshot?.active
    val expired = active != null && now?.let { it >= active.expiresAtMillis } == true
    LaunchedEffect(active?.id, expired) { if (expired) model.refresh() }
    CloudTrainingApp(uid, state, now, model::add, model::select, model::start, model::hint, model::close,
        model::refresh, { model.updatePending(true) }, theme, onThemeChange) {
        settingsContent()
        state.selectedKey?.takeIf { state.snapshot?.active == null }?.let { selected ->
            dev.lorem.app.ui.components.IpsumStudyExportAction(
                prepare = { repository.exportStudy(uid, selected) },
                isCurrent = { repository.selectedHandle(uid) == selected })
        }
    }
}

@Composable
fun CloudTrainingApp(uid: String, state: CloudTrainingUiState, now: Long?,
    onAdd: (String) -> Unit, onSelect: (String) -> Unit, onStart: () -> Unit, onHint: () -> Unit,
    onClose: (IpsumFailureReason) -> Unit, onRefresh: () -> Unit, onPending: () -> Unit,
    theme: ThemePreference = ThemePreference.SYSTEM, onThemeChange: (ThemePreference) -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
) {
    val profile = state.snapshot?.handles?.firstOrNull { it.key == state.selectedKey }
    // A new NavController/SaveableStateHolder per account+binding prevents filter, scroll and result leaks.
    key(uid, state.selectedKey, profile?.bindingVersion) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val destination = LoremDestination.entries.firstOrNull { it.route == entry?.destination?.route?.substringBefore('/') }
        val active = state.activeAttempt()
        val closed = state.closedAttempts()
        val ipsums = closed.map { it.ipsum }
        val localProfile = profile?.let { LocalProfile(it.handle, it.handle, null, it.rating, it.consolidatedRating, 0) }
        val consolidation = profile?.let { calculateConsolidatedRating(ipsums, it.consolidatedRating, it.handle, it.rating) }
        var requested by rememberSaveable { mutableStateOf<String?>(null) }
        var resumed by rememberSaveable { mutableStateOf(false) }
        fun home() { nav.navigate(LoremDestination.Home.route) { popUpTo(LoremDestination.Home.route); launchSingleTop = true } }
        fun tab(target: LoremDestination) { nav.navigate(target.route) {
            popUpTo(LoremDestination.Home.route) { saveState = true }; launchSingleTop = true; restoreState = true
        } }
        LaunchedEffect(active?.id, closed, entry?.destination?.route) {
            if (entry == null) return@LaunchedEffect
            if (active != null && !resumed) {
                resumed = true; requested = active.id
                nav.navigate(LoremDestination.Ipsum.route) { launchSingleTop = true }
            }
            val finished = closed.firstOrNull { it.id == requested }
            if (active == null && finished != null && nav.currentDestination?.route == LoremDestination.Ipsum.route) {
                nav.navigate("${LoremDestination.Result.route}/${finished.id}") {
                    popUpTo(LoremDestination.Ipsum.route) { inclusive = true }; launchSingleTop = true
                }
            }
        }
        // Permit every subsequent start to resume, without stealing navigation while inspecting history.
        LaunchedEffect(active?.id) { if (active == null && requested != null) resumed = false }
        LoremScaffold(destination, showSettings = destination in listOf(LoremDestination.Home, LoremDestination.History, LoremDestination.Statistics),
            canGoBack = destination in listOf(LoremDestination.Configuration, LoremDestination.Ipsum, LoremDestination.Result),
            onBack = { if (!nav.popBackStack()) home() }, onSettings = { tab(LoremDestination.Configuration) }, onTab = ::tab) {
            Column(Modifier.fillMaxSize().testTag("cloud-training")) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                if (state.snapshot?.online == false) Text("Offline · dados salvos neste aparelho; conecte-se para confirmar o progresso.", Modifier.padding(12.dp))
                if (state.snapshot == null) {
                    Column(Modifier.padding(24.dp)) {
                        Text(if (state.error == null) "Carregando progresso da conta…" else "O progresso não pôde ser confirmado.")
                        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Tentar novamente") }
                        // Account recovery remains reachable during a configuration or permission failure.
                        settingsContent()
                    }
                } else NavHost(nav, startDestination = LoremDestination.Home.route, modifier = Modifier.weight(1f)) {
                    composable(LoremDestination.Home.route) {
                        if (localProfile == null) Column(Modifier.padding(24.dp)) {
                            Text("Selecione ou adicione seu handle para começar.")
                            Button(onClick = { tab(LoremDestination.Configuration) }) { Text("Configurar handle") }
                        } else Column {
                            if (dev.lorem.app.BuildConfig.VIRTUAL_DUEL_ENABLED) Text(
                                "Treino com Rating Dolor · sem dicas · desistir conta como não resolver até 120 min.", Modifier.padding(16.dp))
                            state.preparation?.let { Text("${it.stage.label}: ${it.detail}", Modifier.padding(16.dp)) }
                            HomeScreen(localProfile, catalogReady = false, requiresLocalPreparation = false,
                                showDolorRating = true, dolorRatingMicros = profile?.duelRatingMicros,
                                onNavigate = { route ->
                                    if (route == LoremDestination.Ipsum.route) { requested = active?.id; nav.navigate(route) }
                                    else if (route.startsWith("${LoremDestination.Result.route}/")) {
                                        closed.firstOrNull { it.ipsum.id == route.substringAfterLast('/').toLongOrNull() }?.let {
                                            nav.navigate("${LoremDestination.Result.route}/${it.id}")
                                        }
                                    } else nav.navigate(route)
                                }, hasActiveIpsum = active != null,
                                latestFinishedIpsumId = closed.maxByOrNull { it.ipsum.endedAtEpochMillis ?: 0 }?.ipsum?.id,
                                finishedIpsumCount = closed.size, acceptedIpsumCount = ipsums.count { it.status == IpsumStatus.COMPLETED },
                                startIpsumState = if (state.busy) StartIpsumUiState.Loading else state.error?.let { StartIpsumUiState.Error(it) } ?: StartIpsumUiState.Idle,
                                onStartIpsum = onStart, startEnabled = state.snapshot.online && !state.busy, consolidation = consolidation,
                                dataSourceNotice = "O início verifica histórico, catálogo e perfil antes de escolher um problema. Prazo máximo: 2h.")
                        }
                    }
                    composable(LoremDestination.Ipsum.route) {
                        CloudTrainingScreen(state, now, onHint, onClose, onRefresh) { home() }
                    }
                    composable("${LoremDestination.Result.route}/{id}") { back ->
                        val result = closed.firstOrNull { it.id == back.arguments?.getString("id") }
                        IpsumResultScreen(result?.let { IpsumResultUiState.Ready(IpsumResult(it.ipsum, emptyList(), false)) }
                            ?: IpsumResultUiState.Error("Resultado não disponível neste contexto."), ::home)
                    }
                    composable(LoremDestination.History.route) {
                        HistoryScreen(ipsums, updateState = state.pendingUpdate, onUpdatePending = onPending,
                            onNavigateHome = ::home, ownerHandle = profile?.handle,
                            onOpenResult = { i -> closed.firstOrNull { it.ipsum.id == i.id }?.let { nav.navigate("${LoremDestination.Result.route}/${it.id}") } })
                    }
                    composable(LoremDestination.Statistics.route) {
                        val stats = localProfile?.let { buildIpsumStatistics(it, ipsums, ipsums.map { i -> i.problem }) }
                        StatisticsScreen(stats?.let { StatisticsUiState.Ready(it) } ?: StatisticsUiState.NoProfile, ::home, consolidation,
                            showDolorRating = true, dolorRatingMicros = profile?.duelRatingMicros)
                    }
                    composable(LoremDestination.Configuration.route) {
                        CloudSettings(state, onAdd, onSelect, onStart, onRefresh, onPending, theme, onThemeChange, settingsContent)
                    }
                }
            }
        }
    }
}

/** Reuses the established active screen; closed/untrusted time cannot expose training actions. */
@Composable
fun CloudTrainingScreen(state: CloudTrainingUiState, now: Long?, onHint: () -> Unit,
    onClose: (IpsumFailureReason) -> Unit, onRefresh: () -> Unit,
    onHome: () -> Unit = {},
) {
    val active = state.activeAttempt()
    val canTrain = active != null && active.state == CloudIpsumState.ACTIVE && now != null && now < active.expiresAtMillis
    if (canTrain) {
        IpsumScreen(active!!.ipsum, onNavigateHome = onHome, onRevealHint = onHint,
            onVerifySubmissions = onRefresh,
            submissionCheckState = if (state.busy) SubmissionCheckUiState.Loading else SubmissionCheckUiState.Idle,
            onEndWithoutAc = { it?.let(onClose) }, nowMillis = { requireNotNull(now) },
            dataSourceNotice = "Prazo absoluto de 2h · o processamento pode aguardar conexão.")
    } else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when {
            active == null -> "Confirmando o treino salvo…"
            active.state == CloudIpsumState.CLOSED_AWAITING_VERIFICATION -> "Treino fechado · aguardando verificação"
            now == null -> "Confirmando prazo do Ipsum salvo…"
            else -> "Prazo atingido · aguardando verificação"
        })
        Text(if (now == null) "Conecte-se para confirmar o relógio e as submissões." else "A verificação preserva o prazo original e pode aguardar rede ou sessão.")
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Atualizar progresso") }
        TextButton(onClick = onHome) { Text("Voltar ao início") }
    }
}

@Composable
private fun CloudSettings(state: CloudTrainingUiState, onAdd: (String) -> Unit, onSelect: (String) -> Unit,
    onStart: () -> Unit, onRefresh: () -> Unit, onPending: () -> Unit, theme: ThemePreference, onThemeChange: (ThemePreference) -> Unit,
    settingsContent: @Composable () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seus handles", style = MaterialTheme.typography.headlineSmall)
        if (state.snapshot?.active != null) Text("Conclua o Ipsum ativo antes de trocar de handle.")
        state.snapshot?.handles?.forEach { handle ->
            OutlinedButton(onClick = { onSelect(handle.key) }, enabled = !state.busy && state.snapshot.active == null) {
                Text(handle.handle + if (handle.key == state.selectedKey) " · selecionado" else "")
            }
        }
        OutlinedTextField(input, { input = it }, label = { Text("Handle Codeforces") }, singleLine = true, enabled = !state.busy && state.snapshot?.active == null)
        Button(onClick = { onAdd(input) }, enabled = input.isNotBlank() && !state.busy && state.snapshot?.active == null) { Text("Adicionar handle") }
        Text("Aparência", style = MaterialTheme.typography.titleLarge)
        ThemePreference.entries.forEach { preference ->
            FilterChip(selected = theme == preference, onClick = { onThemeChange(preference) },
                label = { Text(when (preference) { ThemePreference.SYSTEM -> "Sistema"; ThemePreference.LIGHT -> "Claro"; ThemePreference.DARK -> "Escuro" }) })
        }
        Text("Preparação e atualização", style = MaterialTheme.typography.titleLarge)
        state.preparation?.let { Text("${it.stage.label}: ${it.detail}") }
        Text("Histórico incompleto não autoriza treino. Tente iniciar novamente para retomar uma preparação interrompida.")
        if (state.preparation != null && state.snapshot?.active == null) {
            Button(onClick = onStart, enabled = !state.busy && state.snapshot?.online == true) { Text("Retomar preparação e iniciar Ipsum") }
        }
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Atualizar progresso") }
        TextButton(onClick = onPending, enabled = !state.busy) { Text("Atualizar pendências") }
        settingsContent()
    }
}
