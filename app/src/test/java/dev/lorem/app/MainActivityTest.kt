package dev.lorem.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app opens saves local data and visits every provisional screen`() {
        composeRule.onNodeWithText("Configuração").assertIsDisplayed()
        composeRule.onNodeWithText("Nome de teste").performTextInput("Ada")
        composeRule.onNodeWithText("Salvar e abrir Início").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Dado local restaurado: Ada")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Início").assertIsDisplayed()

        visit("Abrir Ipsum", "Ipsum")
        visit("Abrir Resultado", "Resultado")
        visit("Abrir Histórico", "Histórico")
        visit("Abrir Estatísticas", "Estatísticas")
        visit("Abrir Configuração", "Configuração")
    }

    private fun visit(action: String, expectedTitle: String) {
        composeRule.onNodeWithText(action).performClick()
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }
}
