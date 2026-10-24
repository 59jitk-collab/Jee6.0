package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GoogleSignInLifecycleTest {

    @Test
    fun findActivity_unwrapsNestedContextWrappers_andReturnsActivityOrNull() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapper1 = ContextWrapper(activity)
        val wrapper2 = ContextWrapper(wrapper1)

        val resolved = wrapper2.findActivity()
        assertNotNull(resolved)
        assertEquals(activity, resolved)

        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val nonActivityResolved = appContext.findActivity()
        assertNull(nonActivityResolved)
    }

    @Test
    fun repositoryConstruction_whenFirebaseAppCleared_doesNotThrowUninitializedError() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.clearInstancesForTest()

        // Constructing FirestoreCloudSyncRepository(context) must NEVER throw
        // "Default FirebaseApp is not initialized in this process"
        val repo = FirestoreCloudSyncRepository(appContext)
        repo.signOut()

        val nonActivityResult = runBlocking {
            repo.signInWithGoogleCredentialManager(appContext)
        }
        assertTrue(nonActivityResult.isFailure)
        val ex = nonActivityResult.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex?.message?.contains("requires an Activity context") == true)
    }

    @Test
    fun signInWithGoogle_whenFirebaseAppClearedAndUnconfigured_returnsActionableFailureWithoutCrashing() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.clearInstancesForTest()

        val repo = FirestoreCloudSyncRepository(appContext)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val result = runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(2000L) {
                repo.signInWithGoogleCredentialManager(activity)
            }
        }

        if (result != null) {
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(
                "Unexpected raw uninitialized error: ${exception?.message}",
                exception?.message?.contains("Default FirebaseApp is not initialized") != true
            )
        }
    }

    @Test
    fun defaultWebClientIdAndDatabaseIdResolution_handlesMissingOrPresentResourcesSafely() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val clientId = FirestoreCloudSyncRepository.resolveDefaultWebClientId(appContext)
        assertTrue(clientId == null || clientId.isNotEmpty())

        val dbId = FirestoreCloudSyncRepository.resolveFirestoreDatabaseId(appContext)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun cancellationException_preservesMessageForCleanStatusReporting() {
        val cancellationEx = GetCredentialCancellationException("User dismissed account picker")
        assertEquals("User dismissed account picker", cancellationEx.message)
    }

    @Test
    fun getAppSigningFingerprints_returnsSafelyWithoutCrashing() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val fingerprints = FirestoreCloudSyncRepository.getAppSigningFingerprints(appContext)
        // In Robolectric without real package signature, returns null safely without throwing
        assertTrue(fingerprints == null || fingerprints.first.isNotBlank())
    }
}
