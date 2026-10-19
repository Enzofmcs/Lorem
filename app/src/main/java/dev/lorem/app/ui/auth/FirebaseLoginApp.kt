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
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.repository.AppearanceRepository
import dev.lorem.app.ui.theme.LoremTheme

@Composable
fun FirebaseLoginApp(authentication: FirebaseAuthentication, appearance: AppearanceRepository) {
    val model: AuthenticationViewModel = viewModel(factory = AuthenticationViewModel.factory(authentication.repository))
    val session by model.session.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val theme by appearance.themePreference.collectAsStateWithLifecycle(ThemePreference.SYSTEM)
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
            onSignIn = { credentials?.let { model.signIn { it.getIdToken(context) } } },
            onRetry = model::refresh,
            onSignOut = { model.signOut { credentials?.clearState() } },
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
    signedInContent: @Composable (GoogleAccount) -> Unit = { HandleOnboardingDestination() },
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Lorem", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
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
                    val failure = error ?: (state as? AuthenticationState.Failed)?.reason
                    failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
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
        Text("A configuração dos handles ainda não está disponível nesta versão.")
    }
}

private fun AuthenticationFailure.message() = when (this) {
    AuthenticationFailure.CONFIGURATION -> "O login ainda não está disponível nesta versão do aplicativo."
    AuthenticationFailure.NETWORK -> "Não foi possível confirmar sua sessão. Verifique a conexão e tente novamente."
    AuthenticationFailure.INVALID_SESSION -> "Sua sessão expirou. Entre novamente com Google."
    AuthenticationFailure.PROVIDER_NOT_ALLOWED -> "Use sua conta Google para entrar no Lorem."
    AuthenticationFailure.UNKNOWN -> "Não foi possível entrar. Tente novamente."
}
