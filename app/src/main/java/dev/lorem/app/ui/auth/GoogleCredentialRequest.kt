package dev.lorem.app.ui.auth

import android.content.Context
import dev.lorem.app.data.auth.authLog
import android.annotation.SuppressLint
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class GoogleSignInCancelled : Exception()
class GoogleCredentialUnavailable : Exception()

class GoogleCredentialRequest(context: Context, private val webClientId: String) {
    private val manager = CredentialManager.create(context)

    // Lint 1.5 does not follow the response decoder below. Its SDK contract is
    // exercised with both Google credential types and malformed/non-Google data.
    @SuppressLint("CredentialManagerSignInWithGoogle")
    suspend fun getIdToken(activityContext: Context): String {
        try {
            val request = GetCredentialRequest.Builder().addCredentialOption(
                GetSignInWithGoogleOption.Builder(webClientId).build(),
            ).build()
            val credential = manager.getCredential(activityContext, request).credential
            return googleIdToken(credential)
        } catch (error: GetCredentialCancellationException) {
            authLog("credential_provider_cancelled:${error.javaClass.simpleName}")
            throw GoogleSignInCancelled()
        } catch (error: NoCredentialException) {
            authLog("credential_provider_empty:${error.javaClass.simpleName}")
            throw GoogleCredentialUnavailable()
        } catch (error: GetCredentialException) {
            authLog("credential_provider_failed:${error.javaClass.simpleName}")
            throw GoogleCredentialUnavailable()
        }
    }

    suspend fun clearState() = manager.clearCredentialState(ClearCredentialStateRequest())
}

internal fun googleIdToken(credential: Credential): String {
    if (credential !is CustomCredential || credential.type !in setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
        )) throw GoogleCredentialUnavailable()
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (_: GoogleIdTokenParsingException) {
        throw GoogleCredentialUnavailable()
    }
}
