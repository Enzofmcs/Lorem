package dev.lorem.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumResult
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import dev.lorem.app.ui.LoremApp
import dev.lorem.app.ui.theme.LoremTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `statistics route uses saved training history offline and returns to home`() {
        val local = MemoryLoremRepository(StatisticsFixtures.profile, StatisticsFixtures.finished)
        setApp(local, UserLookupResult.NetworkFailure)
        composeRule.onNodeWithTag("tab-statistics").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Rating Lorem atual: 1202").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("statistics-list")
            .performScrollToNode(hasText("Taxa de AC no Ipsum: 50,0% (2/4)"))
        composeRule.onNodeWithText("Taxa de AC no Ipsum: 50,0% (2/4)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-list").performScrollToNode(hasText("Voltar ao Início"))
        composeRule.onNodeWithText("Voltar ao Início").performScrollTo().performClick()
        composeRule.onNodeWithTag("tab-home").assertIsSelected()
    }

    @Test
    fun `valid handle persists profile and opens home`() {
        val local = MemoryLoremRepository()
        setApp(local, UserLookupResult.Success(CodeforcesUser("tourist", "Gennady", 4009)))

        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("tourist")
        composeRule.onNodeWithText("Conectar Codeforces").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { local.profile.value != null }

        composeRule.onNodeWithTag("tab-home").assertIsSelected()
        composeRule.onNodeWithText("Olá, Gennady").performScrollTo().assertIsDisplayed()
        assertEquals(4009, local.profile.value?.loremRating)
    }

    @Test
    fun `invalid handle shows error and does not navigate`() {
        setApp(MemoryLoremRepository(), UserLookupResult.UserNotFound)
        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("missing")
        composeRule.onNodeWithText("Conectar Codeforces").performScrollTo().performClick()

        composeRule.onNodeWithText("Usuário não encontrado no Codeforces.").assertIsDisplayed()
        composeRule.onNodeWithText("Conecte seu Codeforces").assertIsDisplayed()
    }

    @Test
    fun `network error preserves typed handle and stays on configuration`() {
        setApp(MemoryLoremRepository(), UserLookupResult.NetworkFailure)
        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("keep-me")
        composeRule.onNodeWithText("Conectar Codeforces").performScrollTo().performClick()

        composeRule.onNodeWithText("Handle do Codeforces")
            .assertTextEquals("Handle do Codeforces", "@", "keep-me", "Sem conexão com o Codeforces. Verifique a internet e tente novamente.")
        composeRule.onNodeWithText("Conecte seu Codeforces").assertIsDisplayed()
    }

    @Test
    fun `changing handle requires confirmation before replacing saved profile`() {
        val original = LocalProfile("old", "Old User", 1200, 1200, null, 1L)
        val local = MemoryLoremRepository(original)
        setApp(local, UserLookupResult.Success(CodeforcesUser("new", "New User", 1500)))

        composeRule.onNodeWithContentDescription("Configurações").performClick()
        composeRule.onNodeWithText("Handle do Codeforces").performTextReplacement("new")
        composeRule.onNodeWithText("Atualizar handle").performScrollTo().performClick()

        composeRule.onNodeWithText("Trocar handle?").assertIsDisplayed()
        assertEquals(original, local.profile.value)
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onAllNodesWithText("Trocar handle?").assertCountEquals(0)
        assertEquals(original, local.profile.value)

        composeRule.onNodeWithText("Atualizar handle").performScrollTo().performClick()
        composeRule.onNodeWithText("Trocar").performClick()
        composeRule.waitUntil(5_000) { local.profile.value?.handle == "new" }

        composeRule.onNodeWithText("Olá, New User").performScrollTo().assertIsDisplayed()
        assertEquals(1500, local.profile.value?.officialRating)
    }

    @Test
    fun `failed handle change keeps existing profile`() {
        val original = LocalProfile("old", "Old User", 1200, 1200, null, 1L)
        val local = MemoryLoremRepository(original)
        setApp(local, UserLookupResult.UserNotFound)

        composeRule.onNodeWithContentDescription("Configurações").performClick()
        composeRule.onNodeWithText("Handle do Codeforces").performTextReplacement("missing")
        composeRule.onNodeWithText("Atualizar handle").performScrollTo().performClick()
        composeRule.onNodeWithText("Trocar").performClick()

        composeRule.onNodeWithText("Usuário não encontrado no Codeforces.").assertIsDisplayed()
        assertEquals(original, local.profile.value)
    }

    @Test
    fun `open result from home loads the latest finished ipsum`() {
        val finished = Ipsum(
            id = 7L,
            ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(100L, "A"), "Finished problem", 1500, setOf("dp")),
            initialLoremRating = 1200,
            category = IpsumCategory.CHALLENGE,
            desiredRatingMin = 1300,
            desiredRatingMax = 1400,
            selectedRating = 1500,
            fallbackDistance = 100,
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 61_000L,
            status = IpsumStatus.COMPLETED,
        )
        val local = MemoryLoremRepository(
            initialProfile = LocalProfile("tourist", "Tourist", 1500, 1200, null, 1L),
            initialIpsums = listOf(finished),
        )
        setApp(local, UserLookupResult.NetworkFailure)

        composeRule.onNodeWithText("Ver último resultado").performScrollTo().performClick()

        composeRule.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
        composeRule.onNodeWithText("Finished problem").assertIsDisplayed()
    }

    @Test
    fun `home summary counts only finished owned attempts and opens latest result by end time`() {
        val latest = StatisticsFixtures.attempt(1, IpsumStatus.COMPLETED).copy(startedAtEpochMillis = 1000, endedAtEpochMillis = 5000,
            problem = CodeforcesProblem(ProblemId(100, "A"), "Latest by date", 1200, setOf("dp")))
        val older = StatisticsFixtures.attempt(2, IpsumStatus.SOLVED_OUTSIDE).copy(startedAtEpochMillis = 1000, endedAtEpochMillis = 4000)
        val active = StatisticsFixtures.attempt(3, IpsumStatus.ACTIVE).copy(endedAtEpochMillis = null)
        val foreign = latest.copy(id = 4, ownerHandle = "another", endedAtEpochMillis = 9000)
        setApp(MemoryLoremRepository(StatisticsFixtures.profile, listOf(latest, older, active, foreign)), UserLookupResult.NetworkFailure)
        composeRule.onNodeWithText("Ipsums encerrados: 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AC durante o Ipsum: 1 de 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ver último resultado").performScrollTo().performClick()
        composeRule.onNodeWithText("Latest by date").assertIsDisplayed()
    }

    private fun setApp(local: LoremRepository, result: UserLookupResult) {
        composeRule.setContent {
            LoremTheme {
                LoremApp(local, ResultCodeforcesRepository(result))
            }
        }
        composeRule.waitForIdle()
    }
}

private class MemoryLoremRepository(
    initialProfile: LocalProfile? = null,
    initialIpsums: List<Ipsum> = emptyList(),
) : LoremRepository {
    override val profile = MutableStateFlow(initialProfile)
    override val problemHistory = MutableStateFlow<List<ProblemHistory>>(emptyList())
    override val problemCatalog = MutableStateFlow<List<CodeforcesProblem>>(emptyList())
    override val catalogLastUpdatedEpochMillis = MutableStateFlow<Long?>(null)
    override val ipsums = MutableStateFlow(initialIpsums)
    override val activeIpsum = MutableStateFlow<Ipsum?>(null)
    override suspend fun saveProfile(profile: LocalProfile) { this.profile.value = profile }
    override suspend fun clearProfile() { profile.value = null }
    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
        problemHistory.value = history
    }
    override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) {
        problemCatalog.value = problems
        catalogLastUpdatedEpochMillis.value = updatedAtEpochMillis
    }
    override suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum {
        if (activeIpsum.value != null) throw ActiveIpsumAlreadyExistsException()
        val saved = ipsum.copy(id = 1)
        ipsums.value += saved
        activeIpsum.value = saved
        return saved
    }
    override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) {
        activeIpsum.value = activeIpsum.value?.takeIf { it.id == ipsumId }?.let {
            if (it.hintRevealedAtEpochMillis == null) it.copy(hintRevealedAtEpochMillis = revealedAtEpochMillis) else it
        }
    }
    override suspend fun getIpsumResult(ipsumId: Long): IpsumResult? = ipsums.value
        .firstOrNull { it.id == ipsumId && it.status != IpsumStatus.ACTIVE }
        ?.let { IpsumResult(it, emptyList()) }
}

private class ResultCodeforcesRepository(
    private val result: UserLookupResult,
) : CodeforcesRepository {
    override suspend fun user(handle: String) = result
    override suspend fun submissionHistory(handle: String) =
        SubmissionHistoryResult.Success(emptyList())
    override suspend fun problems(): ProblemCatalogResult = ProblemCatalogResult.Success(emptyList())
}
