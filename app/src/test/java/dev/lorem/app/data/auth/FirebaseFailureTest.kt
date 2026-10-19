package dev.lorem.app.data.auth

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import dev.lorem.app.domain.auth.AuthenticationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirebaseFailureTest {
    @Test fun `Firestore outages contention and quota remain recoverable`() {
        for (code in listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED)) {
            assertEquals(AuthenticationFailure.NETWORK, firebaseFailureOf(FirebaseFirestoreException("simulated SDK failure", code)))
        }
        assertEquals(AuthenticationFailure.QUOTA, firebaseFailureOf(FirebaseFirestoreException("simulated SDK failure", Code.RESOURCE_EXHAUSTED)))
        assertEquals(AuthenticationFailure.INVALID_SESSION, firebaseFailureOf(FirebaseFirestoreException("simulated SDK failure", Code.UNAUTHENTICATED)))
    }

    @Test fun `missing Rules or incompatible schema do not falsely report a wrong Google provider`() {
        for (code in listOf(Code.PERMISSION_DENIED, Code.NOT_FOUND, Code.FAILED_PRECONDITION)) {
            assertEquals(AuthenticationFailure.CONFIGURATION, firebaseFailureOf(FirebaseFirestoreException("simulated SDK failure", code)))
        }
    }

    @Test fun `task wrapper preserves cancellation and explicit bootstrap failures`() = runTest {
        val cancellation = CancellationException("cancelled session")
        val configuration = AuthenticationException(AuthenticationFailure.CONFIGURATION)
        for (expected in listOf(cancellation, configuration)) {
            try {
                firebaseCall<Unit> { throw expected }
                fail("An unsuccessful transaction cannot authorize a session")
            } catch (actual: Exception) {
                assertSame(expected, actual)
            }
        }
    }
}
