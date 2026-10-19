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

/** Run with -Plorem.firebaseAuthEnabled=true, without google-services.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `opt in entry never displays an existing legacy profile when Firebase is unconfigured`() {
        assumeTrue(BuildConfig.FIREBASE_AUTH_ENABLED)
        val container = (compose.activity.application as LoremApplication).container
        runBlocking { container.loremRepository.saveProfile(StatisticsFixtures.profile) }
        compose.onNodeWithText("O login ainda não está disponível nesta versão do aplicativo.").assertIsDisplayed()
        compose.onNodeWithTag("google-sign-in").assertIsNotEnabled()
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.onNodeWithText("Iniciar Ipsum").assertDoesNotExist()
        compose.onNodeWithText("tourist", substring = true).assertDoesNotExist()
    }
}
