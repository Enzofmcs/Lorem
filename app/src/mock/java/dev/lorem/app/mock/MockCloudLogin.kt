package dev.lorem.app.mock

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import dev.lorem.app.data.auth.FirebaseGoogleIdentitySource
import dev.lorem.app.data.auth.GoogleSessionRepository
import dev.lorem.app.ui.auth.AuthenticationScreen
import dev.lorem.app.ui.auth.AuthenticationViewModel
import dev.lorem.app.ui.auth.GoogleCredentialRequest

private const val APP_NAME = "lorem-mock-cloud"

internal fun mockCloudFirebase(context: Context): FirebaseApp? {
    val options = FirebaseOptions.fromResource(context) ?: return null
    if (options.projectId.isNullOrBlank() || options.apiKey.isNullOrBlank()) return null
    return FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
        ?: FirebaseApp.initializeApp(context, options, APP_NAME)
}

private class MockSessionViewModel(firebase: FirebaseApp) : ViewModel() {
    // Imports create their own batch documents. No training account bootstrap is needed.
    val repository = GoogleSessionRepository(
        FirebaseGoogleIdentitySource(FirebaseAuth.getInstance(firebase)), {}, viewModelScope,
    )
}

@Composable
@SuppressLint("DiscouragedApi")
internal fun MockCloudLogin(firebase: FirebaseApp?) {
    val context = LocalContext.current
    val projectId = firebase?.options?.projectId
    val webClientId = remember(context) {
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            .takeIf { it != 0 }?.let(context::getString)?.takeIf(String::isNotBlank)
    }
    if (firebase == null || projectId == null || webClientId == null) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lorem Mock", style = MaterialTheme.typography.headlineMedium)
            Text("Confira o google-services.json da variante mock e baixe a versão atualizada após habilitar o login Google.")
        }
        return
    }
    val sessionModel: MockSessionViewModel = viewModel(factory = viewModelFactory {
        initializer { MockSessionViewModel(firebase) }
    })
    val model: AuthenticationViewModel = viewModel(factory = AuthenticationViewModel.factory(sessionModel.repository))
    val session by model.session.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val credentials = remember(webClientId) { GoogleCredentialRequest(context.applicationContext, webClientId) }
    val signOut: () -> Unit = { model.signOut { credentials.clearState() } }

    AuthenticationScreen(
        state = session, busy = busy, error = error,
        onSignIn = { model.signIn { credentials.getIdToken(context) } },
        onRetry = model::refresh, onSignOut = signOut, fullScreenSignedIn = true,
        deviceContent = { Text("Lorem Mock · Firebase de teste: $projectId") },
    ) { account ->
        MockImportApp(mockImportViewModel(firebase, account.uid), "Firebase · $projectId", signOut)
    }
}
