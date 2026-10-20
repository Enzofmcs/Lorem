package dev.lorem.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.data.auth.FirebaseAuthentication
import dev.lorem.app.data.auth.authLog
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.repository.AppearanceRepository
import dev.lorem.app.ui.theme.LoremTheme
import dev.lorem.app.domain.cloud.CloudTrainingRepository
import dev.lorem.app.ui.cloud.CloudTrainingDestination
import dev.lorem.app.domain.handles.HandlesRepository
import dev.lorem.app.ui.handles.AccountHandlesDestination
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun FirebaseLoginApp(authentication: FirebaseAuthentication, appearance: AppearanceRepository,
    handlesRepository: HandlesRepository? = null, training: CloudTrainingRepository? = null, migration: dev.lorem.app.domain.rollout.LegacyMigration? = null) {
    val model: AuthenticationViewModel = viewModel(factory = AuthenticationViewModel.factory(authentication.repository))
    val session by model.session.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val theme by appearance.themePreference.collectAsStateWithLifecycle(ThemePreference.SYSTEM)
    val appearanceScope = rememberCoroutineScope()
    var appearanceError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        authLog("ui_auth_state:" + when (val current = session) {
            AuthenticationState.Loading -> "validating"
            AuthenticationState.SignedOut -> "signed_out"
            is AuthenticationState.SignedIn -> "signed_in"
            is AuthenticationState.Failed -> "failed:${current.reason.name}"
        })
    }
    val context = LocalContext.current
    val credentials = remember(authentication.googleWebClientId) {
        authentication.googleWebClientId?.let { GoogleCredentialRequest(context.applicationContext, it) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) model.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LoremTheme(preference = theme) {
        AuthenticationScreen(session, busy, error,
            fullScreenSignedIn = training != null,
            onSignIn = { model.signIn { credentials?.getIdToken(context) ?: throw GoogleCredentialUnavailable() } },
            onRetry = model::refresh,
            onSignOut = { model.signOut { credentials?.clearState() } },
            deviceContent = { migration?.let { dev.lorem.app.ui.rollout.LegacyArchiveDestination(it) } },
            signedInContent = { account ->
                if (training != null) CloudTrainingDestination(account.uid, training, theme,
                    onThemeChange = { preference -> appearanceScope.launch {
                        try { appearance.setThemePreference(preference); appearanceError = null }
                        catch (e: Exception) { if (e is CancellationException) throw e; appearanceError = "Não foi possível salvar o tema. Tente novamente." }
                    } }, settingsContent = {
                        Text("Conta Google conectada", style = MaterialTheme.typography.titleLarge)
                        Text(account.displayName ?: account.email ?: "Sua conta")
                        OutlinedButton(onClick = { model.signOut { credentials?.clearState() } }, modifier = Modifier.testTag("google-sign-out")) {
                            Text("Sair ou trocar de conta")
                        }
                        appearanceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        migration?.let { dev.lorem.app.ui.rollout.LegacyArchiveDestination(it) }
                    })
                else if (handlesRepository != null) AccountHandlesDestination(account.uid, handlesRepository)
                else HandleOnboardingDestination()
            },
        )
    }
}

/** UID keys remove remembered navigation/inputs when the account changes. No legacy profile enters this gate. */
@Composable
fun AuthenticationScreen(
    state: AuthenticationState,
    busy: Boolean,
    error: AuthenticationFailure?,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    fullScreenSignedIn: Boolean = false,
    deviceContent: @Composable () -> Unit = {},
    signedInContent: @Composable (GoogleAccount) -> Unit = { HandleOnboardingDestination() },
) {
    if (fullScreenSignedIn && state is AuthenticationState.SignedIn) {
        key(state.account.uid) { signedInContent(state.account) }
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Lorem", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
            deviceContent()
            val failure = error ?: (state as? AuthenticationState.Failed)?.reason
            failure?.let {
                Text(it.message(), color = if (it == AuthenticationFailure.CANCELLED)
                    MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("google-sign-in-feedback"))
            }
            when (state) {
                AuthenticationState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Text("Preparando sua sessão…")
                    TextButton(onClick = onSignOut) { Text("Voltar ao login") }
                }
                is AuthenticationState.SignedIn -> key(state.account.uid) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Conta Google conectada", style = MaterialTheme.typography.titleLarge)
                            Text(state.account.displayName ?: state.account.email ?: "Sua conta")
                            if (state.account.displayName != null) state.account.email?.let { Text(it) }
                            OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("google-sign-out")) {
                                Text("Sair ou trocar de conta")
                            }
                        }
                    }
                    signedInContent(state.account)
                }
                else -> {
                    Text("Seu próximo treino começa aqui.", style = MaterialTheme.typography.headlineSmall)
                    Text("Entre com sua conta Google para acessar o Lorem.")
                    Button(onClick = onSignIn,
                        enabled = !busy && failure != AuthenticationFailure.CONFIGURATION,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("google-sign-in")) {
                        Text(if (busy) "Entrando…" else "Entrar com Google")
                    }
                    if (state is AuthenticationState.Failed && state.canRetry) {
                        TextButton(onClick = onRetry, enabled = !busy) { Text("Tentar novamente") }
                        TextButton(onClick = onSignOut, enabled = !busy) { Text("Sair ou trocar de conta") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandleOnboardingDestination() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("handle-onboarding")) {
        Text("Seus handles", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Não foi possível carregar seus handles. Tente entrar novamente.")
    }
}

private fun AuthenticationFailure.message() = when (this) {
    AuthenticationFailure.CONFIGURATION -> "O login ainda não está disponível nesta versão do aplicativo."
    AuthenticationFailure.NETWORK -> "Não foi possível confirmar sua sessão. Verifique a conexão e tente novamente."
    AuthenticationFailure.QUOTA -> "O limite de uso do serviço foi atingido. Tente novamente mais tarde."
    AuthenticationFailure.INVALID_SESSION -> "Sua sessão expirou. Entre novamente com Google."
    AuthenticationFailure.PROVIDER_NOT_ALLOWED -> "Use sua conta Google para entrar no Lorem."
    AuthenticationFailure.CANCELLED -> "A entrada foi interrompida pelo Google. Tente novamente."
    AuthenticationFailure.CREDENTIAL_UNAVAILABLE -> "O Google não retornou uma credencial de acesso. Tente novamente."
    AuthenticationFailure.UNKNOWN -> "Não foi possível entrar. Tente novamente."
}
