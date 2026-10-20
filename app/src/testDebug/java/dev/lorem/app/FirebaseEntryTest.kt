package dev.lorem.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Run with -Plorem.firebaseAuthEnabled=true, with or without google-services.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `default Firebase entry respects configuration and excludes legacy training`() {
        assumeTrue(BuildConfig.FIREBASE_AUTH_ENABLED)
        val context = compose.activity
        val oauthResource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (oauthResource != 0) {
            // This also checks that the real Google Services resources reach MainActivity.
            compose.onNodeWithTag("google-sign-in").assertIsEnabled()
            compose.onNodeWithText("O login ainda não está disponível nesta versão do aplicativo.").assertDoesNotExist()
        } else {
            compose.onNodeWithText("O login ainda não está disponível nesta versão do aplicativo.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("google-sign-in").assertIsNotEnabled()
        }
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.onNodeWithText("Iniciar Ipsum").assertDoesNotExist()
        compose.onNodeWithText("tourist", substring = true).assertDoesNotExist()
    }
}
