package dev.lorem.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.runtime.mutableStateOf
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.IpsumScreen
import dev.lorem.app.ui.SubmissionCheckUiState
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lorem.app.domain.model.RecommendationFactors
import dev.lorem.app.domain.model.TagNeed
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IpsumScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `active screen hides rating tags and category`() {
        composeRule.setContent { LoremTheme { IpsumScreen(ipsum(), nowMillis = { 3_661_001L }) } }

        composeRule.onNodeWithText("Secret Problem").assertIsDisplayed()
        composeRule.onNodeWithText("O rating, as tags e a categoria ficam ocultos durante o Ipsum.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1500", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("dynamic programming", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("CHALLENGE", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Problema 100A").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Abrir no Codeforces").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("elapsed-timer").performScrollTo().performClick()
        composeRule.onNodeWithTag("elapsed-timer").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Última consulta: 01:01:01"))
    }

    @Test
    fun `hint confirmation reveals only tags and verification is a real action`() {
        var revealRequested = false
        var verifyRequested = false
        composeRule.setContent {
            LoremTheme { IpsumScreen(
                ipsum(),
                onRevealHint = { revealRequested = true },
                onVerifySubmissions = { verifyRequested = true },
                nowMillis = { 1L },
            ) }
        }

        composeRule.onNodeWithText("dynamic programming", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Revelar tópicos").performScrollTo().performClick()
        composeRule.onNodeWithText("O uso da dica será registrado e não poderá ser desfeito.").assertIsDisplayed()
        composeRule.onNodeWithText("Revelar").performClick()
        composeRule.waitForIdle()
        assertTrue(revealRequested)

        composeRule.onNodeWithText("Atualizar agora").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(verifyRequested)
        composeRule.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
        composeRule.onNodeWithText("Encerrar sem AC?").assertIsDisplayed()
        composeRule.onNodeWithText("Encerrar").assertIsNotEnabled()
        composeRule.onNodeWithText("Não encontrei a lógica.").performClick()
        composeRule.onNodeWithText("Encerrar").assertIsEnabled()
    }

    @Test
    fun `manual ending offers every confirmed reason and submits the selected one`() {
        var submittedReason: IpsumFailureReason? = null
        composeRule.setContent {
            LoremTheme {
                IpsumScreen(
                    ipsum(),
                    onEndWithoutAc = { submittedReason = it },
                    nowMillis = { 1L },
                )
            }
        }

        composeRule.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
        IpsumFailureReason.entries.forEach { reason ->
            composeRule.onNodeWithText(reason.label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Encerrar").assertIsNotEnabled()

        composeRule.onNodeWithText(IpsumFailureReason.IMPLEMENTATION_ERROR.label)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Encerrar").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertTrue(submittedReason == IpsumFailureReason.IMPLEMENTATION_ERROR)
    }

    @Test
    fun `verification shows loading success and recoverable error states`() {
        val state = mutableStateOf<SubmissionCheckUiState>(SubmissionCheckUiState.Loading)
        val active = mutableStateOf<Ipsum?>(ipsum())
        composeRule.setContent { LoremTheme { IpsumScreen(active.value, submissionCheckState = state.value) } }
        composeRule.onNodeWithText("Consultando submissões no Codeforces…").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            active.value = null
            state.value = SubmissionCheckUiState.Success(2, 1, true)
        }
        composeRule.onNodeWithText("AC reconhecido. Ipsum encerrado com 1 erro(s).").assertIsDisplayed()

        composeRule.runOnIdle {
            active.value = ipsum()
            state.value = SubmissionCheckUiState.Error("Sem conexão")
        }
        composeRule.onNodeWithText("Sem conexão").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `persisted hint displays tags but keeps rating and category hidden`() {
        composeRule.setContent {
            LoremTheme { IpsumScreen(ipsum().copy(hintRevealedAtEpochMillis = 2L), nowMillis = { 2L }) }
        }

        composeRule.onNodeWithText("Tópicos: dynamic programming").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1500", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("CHALLENGE", substring = true).assertDoesNotExist()
    }

    @Test
    fun `all accessibility trees keep recommendation data private before and after persisted hint`() {
        val active = mutableStateOf(ipsum().copy(recommendationFactors = RecommendationFactors(
            listOf(TagNeed("dynamic programming", 0, 0, 0, 0)), 0)))
        val restoration = StateRestorationTester(composeRule)
        var reveals = 0
        restoration.setContent { LoremTheme { IpsumScreen(active.value, nowMillis = { 1L },
            onRevealHint = { reveals++; active.value = active.value.copy(hintRevealedAtEpochMillis = 2) }) } }
        assertPrivate(includeTags = true)
        composeRule.onNodeWithText("Revelar tópicos").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(0, reveals)
        assertPrivate(includeTags = true)
        composeRule.onNodeWithText("Revelar tópicos").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Revelar tópicos?").assertIsDisplayed()
        composeRule.onNodeWithText("Revelar").performClick()
        assertEquals(1, reveals)
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Tópicos: dynamic programming").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Revelar tópicos").assertDoesNotExist()
        assertPrivate(includeTags = false)
    }

    @Test
    fun `ending confirmation and exactly one selected reason survive recreation`() {
        var ended = 0
        var reason: IpsumFailureReason? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { LoremTheme { IpsumScreen(ipsum(), nowMillis = { 1L },
            onEndWithoutAc = { ended++; reason = it }) } }
        composeRule.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
        composeRule.onNodeWithText("Encerrar").assertIsNotEnabled()
        composeRule.onNodeWithText(IpsumFailureReason.LOGIC_NOT_FOUND.label).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Encerrar sem AC?").assertIsDisplayed()
        composeRule.onNodeWithText(IpsumFailureReason.LOGIC_NOT_FOUND.label).assertIsSelected()
        composeRule.onNodeWithText("Encerrar").assertIsEnabled().performClick()
        assertEquals(1, ended)
        assertEquals(IpsumFailureReason.LOGIC_NOT_FOUND, reason)
        composeRule.onNodeWithText("Encerrar sem AC?").assertDoesNotExist()
    }

    @Test
    fun `timer reads current persisted elapsed time on request and does not live announce on resume or recreation`() {
        var now = 3_661_001L
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).also { it.currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle = registry
        }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            LoremTheme { IpsumScreen(ipsum(), nowMillis = { now }) }
        } }
        val timer = composeRule.onNodeWithTag("elapsed-timer")
        timer.performScrollTo().performClick()
        timer.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Última consulta: 01:01:01"))
        timer.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED; now += 60_000 }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.onNodeWithTag("elapsed-value", useUnmergedTree = true).assertTextEquals("01:02:01")
        timer.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Última consulta: 01:01:01"))
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("elapsed-value", useUnmergedTree = true).assertTextEquals("01:02:01")
        timer.performScrollTo().performClick()
        timer.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Última consulta: 01:02:01"))
    }

    @Test
    fun `primary action opens the exact Codeforces problem`() {
        var opened: String? = null
        composeRule.setContent { CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
            override fun openUri(uri: String) { opened = uri }
        }) { LoremTheme { IpsumScreen(ipsum(), nowMillis = { 1L }) } } }
        composeRule.onNodeWithText("Abrir no Codeforces").performScrollTo().performClick()
        assertEquals("https://codeforces.com/contest/100/problem/A", opened)
    }

    private fun assertPrivate(includeTags: Boolean) {
        for (unmerged in listOf(false, true)) {
            val nodes = composeRule.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = unmerged).fetchSemanticsNodes()
            val accessible = nodes.joinToString { node ->
                listOf(node.config.getOrElse(SemanticsProperties.Text) { emptyList() },
                    node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() },
                    node.config.getOrElse(SemanticsProperties.StateDescription) { "" }).toString()
            }
            val forbidden = listOf("1500", "1200", "1300", "1400", "CHALLENGE", "Desafio", "Por que este problema?", "Tópicos novos") +
                if (includeTags) listOf("dynamic programming") else emptyList()
            forbidden.forEach { assertFalse("Hidden information in accessibility tree: $it", accessible.contains(it)) }
        }
    }

    private fun ipsum() = Ipsum(
        id = 1,
        ownerHandle = "tourist",
        problem = CodeforcesProblem(ProblemId(100, "A"), "Secret Problem", 1500, setOf("dynamic programming")),
        initialLoremRating = 1200,
        category = IpsumCategory.CHALLENGE,
        desiredRatingMin = 1300,
        desiredRatingMax = 1400,
        selectedRating = 1500,
        fallbackDistance = 100,
        startedAtEpochMillis = 1,
        status = IpsumStatus.ACTIVE,
    )
}
