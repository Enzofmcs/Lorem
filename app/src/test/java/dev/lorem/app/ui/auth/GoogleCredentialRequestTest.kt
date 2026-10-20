package dev.lorem.app.ui.auth

import android.app.Application
import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleCredentialRequestTest {
    @Test fun `provider codes survive AndroidX wrapping without account or raw message`() {
        val message = ApiException(Status(16, "[28444] synthetic detail sdk-contract@example.invalid synthetic-token")).message
        val error = GetCredentialCancellationException(message)
        assertEquals("GetCredentialCancellationException:origin:google_api:status:16:detail_code:28444",
            googleProviderDiagnostic(error))
        assertEquals("GetCredentialUnknownException:origin:google_api:status:10",
            googleProviderDiagnostic(GetCredentialUnknownException(ApiException(Status(10, "synthetic-token")).message)))
    }

    @Test fun `Activity cancellation is distinct from Google status and unspecified cancellation`() {
        assertEquals("GetCredentialCancellationException:origin:activity_result:result:0",
            googleProviderDiagnostic(GetCredentialCancellationException("activity is cancelled by the user.")))
        assertEquals("GetCredentialCancellationException:origin:unknown",
            googleProviderDiagnostic(GetCredentialCancellationException()))
    }

    @Test fun `arbitrary provider messages cannot escape into diagnostic logs`() {
        for (message in listOf("sdk-contract@example.invalid token=secret", "secret 16: [28444]",
            "123456789: token", "activity is cancelled by the user. token=secret")) {
            assertEquals("GetCredentialUnknownException:origin:unknown",
                googleProviderDiagnostic(GetCredentialUnknownException(message)))
        }
    }

    @Test fun `both Google credential variants decode using the real Google ID SDK`() {
        val google = GoogleIdTokenCredential.Builder()
            .setId("sdk-contract@example.invalid").setIdToken("synthetic-sdk-token").build()
        for (type in listOf(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)) {
            assertEquals("synthetic-sdk-token", googleIdToken(CustomCredential(type, google.data)))
        }
    }

    @Test fun `unrelated credential cannot become a Google sign in`() {
        assertThrows(GoogleCredentialUnavailable::class.java) {
            googleIdToken(CustomCredential("unrelated-provider", Bundle()))
        }
    }

    @Test fun `Google type with malformed bundle is rejected by SDK decoder`() {
        assertThrows(GoogleCredentialUnavailable::class.java) {
            googleIdToken(CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle()))
        }
    }
}
