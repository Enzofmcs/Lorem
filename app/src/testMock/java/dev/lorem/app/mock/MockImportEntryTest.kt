package dev.lorem.app.mock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.firebase.FirebaseOptions
import dev.lorem.app.BuildConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MockImportEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MockImportActivity>()

    @Test fun `entry identifies its Firebase project or shows what is needed to connect`() {
        val activity = compose.activity
        val firebase = FirebaseOptions.fromResource(activity)
        val webClientId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)

        when {
            BuildConfig.MOCK_USE_EMULATOR -> {
                compose.onNodeWithText("Firestore Emulator · porta 8082").assertIsDisplayed()
                compose.onNodeWithText("Selecionar Excel (.xlsx)").assertIsDisplayed()
            }
            firebase == null -> {
                compose.onNodeWithText("Configuração do Firebase ausente neste APK.").assertIsDisplayed()
                compose.onNodeWithText("Selecionar Excel (.xlsx)").assertDoesNotExist()
            }
            webClientId == 0 -> {
                compose.onNodeWithText("Configuração do login Google ausente neste APK.").assertIsDisplayed()
                compose.onNodeWithText("Firestore Emulator · porta 8082").assertDoesNotExist()
            }
            else -> {
                compose.onNodeWithText("Lorem Mock · Firebase · ${firebase.projectId}").assertIsDisplayed()
                compose.onNodeWithText("Entrar com Google").assertIsDisplayed()
            }
        }
    }
}
