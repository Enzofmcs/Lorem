package dev.lorem.app.ui.auth

import android.app.Application
import android.os.Bundle
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleCredentialRequestTest {
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
