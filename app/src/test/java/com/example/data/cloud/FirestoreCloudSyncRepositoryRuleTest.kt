package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreCloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertChapter_validPayloadWithNullableTotals_createsAndUpdatesDocument() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreCloudSyncRepository(firestore, auth)
        val docId = "ch_organic_${UUID.randomUUID().toString().replace("-", "_")}"

        val initialChapter = CloudChapter(
            id = docId,
            userId = aliceUid,
            subject = "Organic Chemistry",
            unit = "GOC & Isomerism",
            chapterName = "General Organic Chemistry",
            totalLectures = null, // Nullable total preserved
            completedLectures = 0L,
            notesDone = false,
            masteryLevel = "NOT_STARTED",
            isWeak = true,
            isDeleted = false,
            clientUpdatedAt = 1730000000000L
        )

        val createdDocId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertChapter(aliceUid, initialChapter).getOrThrow()
        }
        assertEquals(docId, createdDocId)

        // Verify null totalLectures is preserved on read
        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getChapterById(aliceUid, docId).getOrThrow()
        }
        assertNotNull(fetched)
        assertNull(fetched?.totalLectures)
        assertEquals("General Organic Chemistry", fetched?.chapterName)

        // Update chapter progress and verify createdAt immutability passes security rules
        val updatedDocId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertChapter(
                aliceUid,
                initialChapter.copy(
                    completedLectures = 3L,
                    notesDone = true,
                    masteryLevel = "PRACTICING",
                    clientUpdatedAt = 1730000050000L
                )
            ).getOrThrow()
        }
        assertEquals(docId, updatedDocId)
    }

    @Test
    fun observeUserChapters_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreCloudSyncRepository(firestore, auth)
        val docId = "ch_phys_${UUID.randomUUID().toString().replace("-", "_")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertChapter(
                aliceUid,
                CloudChapter(
                    id = docId,
                    userId = aliceUid,
                    subject = "Physics",
                    unit = "Mechanics",
                    chapterName = "Rotational Dynamics",
                    totalLectures = 14L,
                    completedLectures = 6L,
                    notesDone = true,
                    masteryLevel = "PRACTICING",
                    isWeak = false,
                    isDeleted = false,
                    clientUpdatedAt = 1730000000000L
                )
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserChapters(aliceUid).first { list -> list.any { it.id == docId } }
        }
        assertTrue(emitted.any { it.id == docId && it.totalLectures == 14L })
    }

    @Test
    fun getChapterById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreCloudSyncRepository(firestore, auth)
        val docId = "ch_private_${UUID.randomUUID().toString().replace("-", "_")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertChapter(
                aliceUid,
                CloudChapter(
                    id = docId,
                    userId = aliceUid,
                    subject = "Mathematics",
                    unit = "Calculus",
                    chapterName = "Definite Integration",
                    totalLectures = 10L,
                    completedLectures = 5L
                )
            ).getOrThrow()
        }

        // Switch to Bob and verify Bob cannot read Alice's chapter
        signInTestUser(BOB_EMAIL)
        val crossUserResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getChapterById(aliceUid, docId)
        }
        assertTrue(crossUserResult.isFailure)
        val ex = crossUserResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeUserChapters_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = FirestoreCloudSyncRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserChapters(aliceUid).first()
            }
            fail("Expected exception when observing chapters while unauthenticated")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_jee@test.com"
        const val BOB_EMAIL = "bob_jee@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
