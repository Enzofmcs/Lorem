package dev.lorem.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.DUEL_SCALE
import dev.lorem.app.domain.VirtualDuelResult
import dev.lorem.app.domain.VirtualDuelSnapshot
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.cloud.CloudTrainingApp
import dev.lorem.app.ui.cloud.CloudTrainingUiState
import dev.lorem.app.ui.screens.RatingComparison
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudJourneyVisualTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val active = CloudIpsum("sample", "cf_tourist", 0, CloudIpsumState.ACTIVE,
        Ipsum(1, "Tourist", CodeforcesProblem(ProblemId(2000, "A"), "Travessia do labirinto", 900, setOf("greedy")),
            920, IpsumCategory.CURRENT_LEVEL, 900, 900, 900, 0, 1_700_000_000_000,
            status = IpsumStatus.ACTIVE, studyRecordingVersion = CLOUD_RULE_VERSION,
            virtualDuelSnapshot = VirtualDuelSnapshot(800 * DUEL_SCALE, 900, 0)))
    private val final = active.copy(state = CloudIpsumState.FINAL, ipsum = active.ipsum.copy(
        status = IpsumStatus.COMPLETED, endedAtEpochMillis = active.ipsum.startedAtEpochMillis + 3_600_000,
        finalLoremRating = 944, ratingDelta = 24,
        virtualDuelResult = VirtualDuelResult(800 * DUEL_SCALE, -8 * DUEL_SCALE, 792 * DUEL_SCALE, 750_000, 750_000, 850_000)))
    private fun state(attempt: CloudIpsum? = null, closed: List<CloudIpsum> = emptyList()) = CloudTrainingUiState(
        CloudSnapshot(listOf(CloudHandle("cf_tourist", "Tourist", 920, null, 0, duelRatingMicros = 800 * DUEL_SCALE)),
            attempt, closed + listOfNotNull(attempt), true), "cf_tourist")

    @Test fun `capture the four cloud journey states in light and dark`() {
        val screen = mutableStateOf(state())
        val dark = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        val account = mutableIntStateOf(0)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) {
                LoremTheme(darkTheme = dark.value) {
                    CloudTrainingApp(account.intValue.toString(), screen.value, active.ipsum.startedAtEpochMillis + 1_500_000,
                        {}, {}, { screen.value = state(active) }, {}, {}, {}, {})
                }
            }
        }
        for ((theme, fontScale) in listOf("light" to 1f, "dark" to 1f, "light-font200" to 2f, "dark-font200" to 2f)) {
            compose.runOnIdle {
                dark.value = theme.startsWith("dark")
                scale.floatValue = fontScale
                screen.value = state()
                account.intValue++
            }
            save("inicio-$theme")
            compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Travessia do labirinto").assertIsDisplayed()
            save("ativo-$theme")
            compose.onNodeWithText("Abrir no Codeforces").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Voltar ao início").performScrollTo().performClick()
            save("retomada-$theme")
            compose.onAllNodes(hasText("Retomar Ipsum") or hasText("Continuar Ipsum")).onFirst().performScrollTo().performClick()
            compose.runOnIdle { screen.value = state(closed = listOf(final)) }
            compose.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
            save("resultado-$theme")
        }
    }

    @Test fun `one Dolor point with older Lorem history renders in both themes on a small screen`() {
        val dark = mutableStateOf(false)
        val historical = final.ipsum.copy(id = 2, endedAtEpochMillis = active.ipsum.startedAtEpochMillis - 1,
            startedAtEpochMillis = active.ipsum.startedAtEpochMillis - 3_600_000,
            problem = active.ipsum.problem.copy(id = ProblemId(1999, "B"), name = "Travessia anterior"),
            finalLoremRating = 920, virtualDuelSnapshot = null, virtualDuelResult = null)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                LoremTheme(darkTheme = dark.value) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                            RatingComparison(listOf(final.ipsum, historical))
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            compose.onNodeWithTag("statistics-evolution-chart").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Rating Lorem: 944 (+24)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Rating Dolor: 792 (−8)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Evolução dos ratings").performScrollTo().assertIsDisplayed()
            save(if (isDark) "grafico-dark-font200" else "grafico-light-font200")
            compose.onNodeWithText("Ipsum anterior").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Rating Dolor: sem avaliação").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Próximo Ipsum").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("statistics-evolution-chart").performScrollTo().performTouchInput {
                click(Offset(10f, centerY))
            }
            compose.onNodeWithText("Rating Dolor: sem avaliação").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Próximo Ipsum").performScrollTo().performClick()
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 } ?: view
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("build/reports/visual/cloud-journey/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
