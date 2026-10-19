package dev.lorem.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import dev.lorem.app.domain.auth.*
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.ui.auth.AuthenticationScreen
import dev.lorem.app.ui.handles.*
import dev.lorem.app.ui.theme.LoremTheme
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandlesScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun profile(uid: String, handle: String = "Tourist", rating: Int = 800) =
        AccountHandle(uid, HandleIdentity.parse(handle), 1, rating, null, if (rating == 800) 0 else 5, 1234)
    private fun snapshot(uid: String, rating: Int) = HandlesSnapshot(listOf(profile(uid, rating = rating)),
        ProfileLink.forAccount(uid, "Tourist", 1, 1), serverConfirmed = true)

    private inner class Repository : HandlesRepository {
        val values = mapOf("a" to MutableStateFlow(snapshot("a", 1377)), "b" to MutableStateFlow(snapshot("b", 800)))
        val collectors = mutableMapOf<String, Int>()
        var additions = 0; var selections = 0
        override fun observe(uid: String): Flow<HandlesSnapshot> = flow {
            collectors[uid] = (collectors[uid] ?: 0) + 1
            try { emitAll(values.getValue(uid)) }
            finally { collectors[uid] = (collectors[uid] ?: 1) - 1 }
        }
        override suspend fun add(uid: String, input: String): AccountHandle {
            additions++
            val created = profile(uid, "Petr")
            values.getValue(uid).update { it.copy(handles = it.handles + created) }
            return created
        }
        override suspend fun select(uid: String, handleKey: String, bindingVersion: Long) {
            selections++
            values.getValue(uid).update { old ->
                val selected = old.handles.first { it.identity.key == handleKey }
                old.copy(selection = ProfileLink.forAccount(uid, selected.identity.canonical, bindingVersion,
                    (old.selection?.version ?: 0) + 1))
            }
        }
    }

    @Test fun `account switch clears inputs old progress and listeners and sign out disposes the handle destination`() {
        val repository = Repository()
        val session = mutableStateOf<AuthenticationState>(AuthenticationState.SignedIn(GoogleAccount("a", "Conta A", null)))
        compose.setContent { LoremTheme {
            AuthenticationScreen(session.value, false, null, {}, {}, { session.value = AuthenticationState.SignedOut }) {
                AccountHandlesDestination(it.uid, repository)
            }
        } }
        compose.onNodeWithTag("handle-input").performScrollTo().performTextInput("private-account-a")
        compose.runOnIdle { assertEquals(1, repository.collectors["a"]); session.value = AuthenticationState.SignedIn(GoogleAccount("b", "Conta B", null)) }
        compose.onNodeWithTag("handle-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("private-account-a").assertDoesNotExist()
        compose.onNodeWithText("Rating Lorem: 1377").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, repository.collectors["a"]); assertEquals(1, repository.collectors["b"]) }
        compose.onNodeWithTag("google-sign-out").performScrollTo().performClick()
        compose.onNodeWithTag("handle-onboarding").assertDoesNotExist()
        compose.onNodeWithTag("google-sign-in").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, repository.collectors["b"]) }
    }

    @Test fun `add and select another handle preserves the previous rating and shows the confirmed selection`() {
        val repository = Repository()
        compose.setContent { LoremTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AccountHandlesDestination("a", repository)
        } } }
        compose.onNodeWithTag("handle-input").performTextInput("Petr")
        compose.onNodeWithTag("add-handle").performClick()
        compose.onNodeWithText("Petr").assertExists()
        compose.onNodeWithText("Rating Lorem: 1377").assertExists()
        compose.onNodeWithTag("select-handle-cf_petr").performScrollTo().performClick()
        compose.onNodeWithTag("select-handle-cf_petr").assertIsSelected()
        compose.onNodeWithText("Handle selecionado: Petr").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, repository.additions); assertEquals(1, repository.selections)
            assertEquals(1377, repository.values.getValue("a").value.handles.first().loremRating)
        }
    }

    @Test fun `cached state cannot switch and active Ipsum hides ratings while locking selection`() {
        val snapshot = snapshot("a", 1377).copy(handles = listOf(profile("a", rating = 1377), profile("a", "Petr")))
        val state = mutableStateOf(HandlesUiState(snapshot.copy(serverConfirmed = false), input = "Petr", loading = false))
        compose.setContent { LoremTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HandlesScreen(state.value, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("select-handle-cf_petr").assertIsNotEnabled()
        compose.onNodeWithTag("add-handle").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(snapshot = snapshot.copy(locked = true)) }
        compose.onNodeWithTag("select-handle-cf_petr").assertIsNotEnabled()
        compose.onNodeWithText("Rating Lorem: 1377").assertDoesNotExist()
        compose.onNodeWithText("Rating Lorem: 800").assertDoesNotExist()
        compose.onNodeWithText("Você tem um Ipsum em andamento. A troca de handle está bloqueada.").performScrollTo().assertIsDisplayed()
    }
}
