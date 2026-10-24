package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.SyncIdentityHelper
import com.example.data.local.entities.SyncRecordState
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
): String {
    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val totalXp: Long = 0L,
    val currentLevel: Long = 1L,
    val currentStreakDays: Long = 0L,
    val lastCloudSyncEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudChapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val unit: String = "",
    val chapterName: String = "",
    val totalLectures: Long? = null,
    val completedLectures: Long = 0L,
    val notesDone: Boolean = false,
    val dibyDone: Long? = null,
    val dibyTotal: Long? = null,
    val classProblemsDone: Long? = null,
    val classProblemsTotal: Long? = null,
    val dppDone: Long? = null,
    val dppTotal: Long? = null,
    val pyqDone: Long? = null,
    val pyqTotal: Long? = null,
    val worksheetsDone: Long? = null,
    val worksheetsTotal: Long? = null,
    val masteryLevel: String = "NOT_STARTED",
    val isWeak: Boolean = false,
    val isDeleted: Boolean = false,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val dayNumber: Long = 1L,
    val focusSubjects: String = "",
    val assignedChapterNames: String = "",
    val targetLectures: Long = 0L,
    val completedLectures: Long = 0L,
    val targetProblems: Long = 0L,
    val completedProblems: Long = 0L,
    val notesReviewed: Boolean = false,
    val studyMinutes: Long = 0L,
    val breakMinutes: Long = 0L,
    val sleepHours: Double = 8.0,
    val isSundayTest: Boolean = false,
    val isDeleted: Boolean = false,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMistake(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val chapterName: String = "",
    val testName: String = "",
    val questionSnippet: String = "",
    val errorType: String = "",
    val conceptMissed: String = "",
    val correctTakeaway: String = "",
    val lessonLearned: String = "",
    val solutionNotes: String = "",
    val reviewCount: Long = 0L,
    val isResolved: Boolean = false,
    val isDeleted: Boolean = false,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudSyncSummary(
    val pushedChapters: Int = 0,
    val pushedSchedules: Int = 0,
    val pushedMistakes: Int = 0,
    val pulledChapters: Int = 0,
    val pulledSchedules: Int = 0,
    val pulledMistakes: Int = 0
)

class FirestoreCloudSyncRepository private constructor(
    private val appContext: Context?,
    private val injectedDb: FirebaseFirestore?,
    private val injectedAuth: FirebaseAuth?
) {
    constructor(
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth
    ) : this(
        appContext = null,
        injectedDb = db,
        injectedAuth = auth
    )

    constructor(context: Context) : this(
        appContext = context.applicationContext ?: context,
        injectedDb = null,
        injectedAuth = null
    )

    private val db: FirebaseFirestore
        get() {
            injectedDb?.let { return it }
            val ctx = checkNotNull(appContext) { "Application context is required to initialize Firestore." }
            if (!ensureFirebaseInitialized(ctx)) {
                throw IllegalStateException(
                    "Firebase Cloud Sync is not configured in this APK build (missing app/google-services.json during build). " +
                    "Offline JEE Study OS features remain fully available."
                )
            }
            return resolveFirestoreDatabase(ctx)
        }

    private val auth: FirebaseAuth
        get() {
            injectedAuth?.let { return it }
            val ctx = checkNotNull(appContext) { "Application context is required to initialize FirebaseAuth." }
            if (!ensureFirebaseInitialized(ctx)) {
                throw IllegalStateException(
                    "Firebase Auth is not configured in this APK build (missing app/google-services.json during build). " +
                    "Offline JEE Study OS features remain fully available."
                )
            }
            return Firebase.auth
        }

    suspend fun signInWithGoogleCredentialManager(activityContext: Context): Result<FirebaseUser> {
        return try {
            val activity = activityContext.findActivity()
                ?: return Result.failure(
                    IllegalArgumentException("Google Sign-In requires an Activity context (got ${activityContext.javaClass.name}).")
                )

            if (injectedAuth == null && !ensureFirebaseInitialized(activity)) {
                Log.w("FirestoreCloudSync", "Google Sign-In aborted: Default FirebaseApp is not configured in this build.")
                return Result.failure(
                    IllegalStateException(
                        "Firebase is not configured in this APK build (missing app/google-services.json during build). " +
                        "Add app/google-services.json or set the GOOGLE_SERVICES_JSON GitHub Actions secret and rebuild."
                    )
                )
            }

            val webClientId = resolveDefaultWebClientId(activity)
                ?: return Result.failure(
                    IllegalStateException(
                        "Google Sign-In is not configured: 'default_web_client_id' was not found in string resources. " +
                        "Ensure a valid google-services.json containing an OAuth Web Client (client_type 3) is present in the app module."
                    )
                )

            val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = try {
                credentialManager.getCredential(
                    request = request,
                    context = activity
                )
            } catch (e: GetCredentialCancellationException) {
                Log.i("FirestoreCloudSync", "Google Sign-In cancelled by user.")
                return Result.failure(e)
            } catch (e: GetCredentialCustomException) {
                Log.e("FirestoreCloudSync", "Google Sign-In custom credential exception [${e.type}]: ${e.message}")
                return Result.failure(e)
            } catch (e: GetCredentialException) {
                Log.e("FirestoreCloudSync", "Google Sign-In credential exception [${e.javaClass.simpleName}]: ${e.message}")
                return Result.failure(e)
            }

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                if (idToken.isBlank()) {
                    Log.e("FirestoreCloudSync", "Google ID token returned was blank.")
                    return Result.failure(IllegalStateException("Google ID token returned was blank."))
                }
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = checkNotNull(authResult.user) { "Firebase Auth user was null after sign-in" }
                Log.i("FirestoreCloudSync", "Google Sign-In successful for user [uid=${user.uid.take(6)}...]")
                Result.success(user)
            } else {
                Log.e("FirestoreCloudSync", "Unsupported credential type returned: ${credential.type}")
                Result.failure(IllegalStateException("Unsupported credential type returned: ${credential.type}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreCloudSync", "Google Sign-In error [${e.javaClass.simpleName}]: ${e.message}")
            Result.failure(e)
        }
    }

    fun signOut() {
        runCatching {
            if (injectedAuth != null) {
                injectedAuth.signOut()
            } else if (appContext != null && ensureFirebaseInitialized(appContext)) {
                Firebase.auth.signOut()
            }
        }.onFailure { e ->
            Log.w("FirestoreCloudSync", "Sign-out skipped or failed [${e.javaClass.simpleName}]: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Real-time Observation Streams (Aligned with firestore.rules)
    // -------------------------------------------------------------------------

    fun observeUserChapters(userId: String): Flow<List<CloudChapter>> {
        return db.collection("users").document(userId).collection("chapters")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudChapter::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    fun observeUserSchedules(userId: String): Flow<List<CloudSchedule>> {
        return db.collection("users").document(userId).collection("schedules")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    fun observeUserMistakes(userId: String): Flow<List<CloudMistake>> {
        return db.collection("users").document(userId).collection("mistakes")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudMistake::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    // -------------------------------------------------------------------------
    // Direct CRUD Operations (Validated by Tier 2 Rule Tests)
    // -------------------------------------------------------------------------

    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        totalXp: Long,
        currentLevel: Long,
        currentStreakDays: Long,
        lastCloudSyncEpochMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "displayName" to displayName.take(120).ifBlank { "JEE Aspirant" },
                    "totalXp" to totalXp.coerceIn(0L, 10_000_000L),
                    "currentLevel" to currentLevel.coerceIn(1L, 1000L),
                    "currentStreakDays" to currentStreakDays.coerceIn(0L, 3650L),
                    "lastCloudSyncEpochMs" to lastCloudSyncEpochMs.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    createMap["email"] = email.take(254)
                }
                docRef.set(createMap).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to displayName.take(120).ifBlank { "JEE Aspirant" },
                    "totalXp" to totalXp.coerceIn(0L, 10_000_000L),
                    "currentLevel" to currentLevel.coerceIn(1L, 1000L),
                    "currentStreakDays" to currentStreakDays.coerceIn(0L, 3650L),
                    "lastCloudSyncEpochMs" to lastCloudSyncEpochMs.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    updateMap["email"] = email.take(254)
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertChapter(userId: String, chapter: CloudChapter): Result<String> {
        val docId = sanitizeFirestoreDocId(
            chapter.id.ifBlank {
                SyncIdentityHelper.deterministicImportedChapterId(chapter.subject, chapter.chapterName, 0)
            }
        )
        val docRef = db.collection("users").document(userId).collection("chapters").document(docId)
        return try {
            val existing = docRef.get().await()
            val allowedMastery = setOf(
                "NOT_ASSESSED",
                "NOT_STARTED",
                "LEARNING",
                "NEEDS_REVISION",
                "PRACTICING",
                "CONFIDENT",
                "STRONG"
            )
            val baseFields = mutableMapOf<String, Any?>(
                "subject" to chapter.subject.take(80).ifBlank { "Physics" },
                "unit" to chapter.unit.take(120),
                "chapterName" to chapter.chapterName.take(160).ifBlank { "Chapter" },
                "totalLectures" to chapter.totalLectures?.coerceIn(0L, 500L),
                "completedLectures" to chapter.completedLectures.coerceIn(0L, 500L),
                "notesDone" to chapter.notesDone,
                "dibyDone" to chapter.dibyDone?.coerceIn(0L, 2000L),
                "dibyTotal" to chapter.dibyTotal?.coerceIn(0L, 2000L),
                "classProblemsDone" to chapter.classProblemsDone?.coerceIn(0L, 5000L),
                "classProblemsTotal" to chapter.classProblemsTotal?.coerceIn(0L, 5000L),
                "dppDone" to chapter.dppDone?.coerceIn(0L, 2000L),
                "dppTotal" to chapter.dppTotal?.coerceIn(0L, 2000L),
                "pyqDone" to chapter.pyqDone?.coerceIn(0L, 5000L),
                "pyqTotal" to chapter.pyqTotal?.coerceIn(0L, 5000L),
                "worksheetsDone" to chapter.worksheetsDone?.coerceIn(0L, 1000L),
                "worksheetsTotal" to chapter.worksheetsTotal?.coerceIn(0L, 1000L),
                "masteryLevel" to chapter.masteryLevel.takeIf { it in allowedMastery }.orEmpty().ifBlank { "NOT_STARTED" },
                "isWeak" to chapter.isWeak,
                "isDeleted" to chapter.isDeleted,
                "clientUpdatedAt" to chapter.clientUpdatedAt.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists()) {
                baseFields["userId"] = userId
                baseFields["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(baseFields).await()
            } else {
                docRef.update(baseFields).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserChapters(userId: String): Result<List<CloudChapter>> {
        val collectionRef = db.collection("users").document(userId).collection("chapters")
        return try {
            val snap = collectionRef.whereEqualTo("userId", userId).get().await()
            Result.success(
                snap.toObjects(
                    CloudChapter::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId/chapters")
            Result.failure(e)
        }
    }

    suspend fun getChapterById(userId: String, chapterId: String): Result<CloudChapter?> {
        val docRef = db.collection("users").document(userId).collection("chapters").document(chapterId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    CloudChapter::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertSchedule(userId: String, schedule: CloudSchedule): Result<String> {
        val docId = sanitizeFirestoreDocId(
            schedule.id.ifBlank { SyncIdentityHelper.scheduleSyncId(schedule.date) }
        )
        val docRef = db.collection("users").document(userId).collection("schedules").document(docId)
        return try {
            val existing = docRef.get().await()
            val baseFields = mutableMapOf<String, Any>(
                "date" to schedule.date.take(10),
                "dayNumber" to schedule.dayNumber.coerceIn(1L, 365L),
                "focusSubjects" to schedule.focusSubjects.take(300),
                "assignedChapterNames" to schedule.assignedChapterNames.take(1000),
                "targetLectures" to schedule.targetLectures.coerceIn(0L, 50L),
                "completedLectures" to schedule.completedLectures.coerceIn(0L, 50L),
                "targetProblems" to schedule.targetProblems.coerceIn(0L, 1000L),
                "completedProblems" to schedule.completedProblems.coerceIn(0L, 1000L),
                "notesReviewed" to schedule.notesReviewed,
                "studyMinutes" to schedule.studyMinutes.coerceIn(0L, 1440L),
                "breakMinutes" to schedule.breakMinutes.coerceIn(0L, 1440L),
                "sleepHours" to schedule.sleepHours.coerceIn(0.0, 24.0),
                "isSundayTest" to schedule.isSundayTest,
                "isDeleted" to schedule.isDeleted,
                "clientUpdatedAt" to schedule.clientUpdatedAt.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists()) {
                baseFields["userId"] = userId
                baseFields["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(baseFields).await()
            } else {
                docRef.update(baseFields).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserSchedules(userId: String): Result<List<CloudSchedule>> {
        val collectionRef = db.collection("users").document(userId).collection("schedules")
        return try {
            val snap = collectionRef.whereEqualTo("userId", userId).get().await()
            Result.success(
                snap.toObjects(
                    CloudSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId/schedules")
            Result.failure(e)
        }
    }

    suspend fun upsertMistake(userId: String, mistake: CloudMistake): Result<String> {
        val docId = sanitizeFirestoreDocId(
            mistake.id.ifBlank { SyncIdentityHelper.newUuid("mk") }
        )
        val docRef = db.collection("users").document(userId).collection("mistakes").document(docId)
        return try {
            val existing = docRef.get().await()
            val baseFields = mutableMapOf<String, Any>(
                "subject" to mistake.subject.take(80).ifBlank { "Physics" },
                "chapterName" to mistake.chapterName.take(160).ifBlank { "General" },
                "testName" to mistake.testName.take(160),
                "questionSnippet" to mistake.questionSnippet.take(2000).ifBlank { "Mistake entry" },
                "errorType" to mistake.errorType.take(80).ifBlank { "Conceptual" },
                "conceptMissed" to mistake.conceptMissed.take(1000),
                "correctTakeaway" to mistake.correctTakeaway.take(1000),
                "lessonLearned" to mistake.lessonLearned.take(1000),
                "solutionNotes" to mistake.solutionNotes.take(2000),
                "reviewCount" to mistake.reviewCount.coerceIn(0L, 1000L),
                "isResolved" to mistake.isResolved,
                "isDeleted" to mistake.isDeleted,
                "clientUpdatedAt" to mistake.clientUpdatedAt.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists()) {
                baseFields["userId"] = userId
                baseFields["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(baseFields).await()
            } else {
                docRef.update(baseFields).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserMistakes(userId: String): Result<List<CloudMistake>> {
        val collectionRef = db.collection("users").document(userId).collection("mistakes")
        return try {
            val snap = collectionRef.whereEqualTo("userId", userId).get().await()
            Result.success(
                snap.toObjects(
                    CloudMistake::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId/mistakes")
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Full Two-Way Sync Between Local Room v5 Database and Cloud Firestore
    // -------------------------------------------------------------------------

    suspend fun syncRoomWithFirestore(
        roomDb: AppDatabase,
        userId: String,
        displayName: String,
        email: String?,
        totalXp: Int,
        currentLevel: Int,
        currentStreakDays: Int
    ): Result<CloudSyncSummary> {
        return try {
            // 1. Update UserProfile summary
            upsertUserProfile(
                userId = userId,
                displayName = displayName.ifBlank { "JEE Aspirant" },
                email = email,
                totalXp = totalXp.toLong(),
                currentLevel = currentLevel.toLong(),
                currentStreakDays = currentStreakDays.toLong()
            ).getOrThrow()

            var pushedCh = 0
            var pushedSched = 0
            var pushedMist = 0
            var pulledCh = 0
            var pulledSched = 0
            var pulledMist = 0

            // 2. Sync Chapters (preserving nullable JEE totals!)
            val localChapters = roomDb.chapterDao().getAllChaptersIncludingDeletedSync()
            val remoteChapters = getUserChapters(userId).getOrThrow().associateBy { it.id }
            val localBySyncId = localChapters.associateBy { sanitizeFirestoreDocId(it.effectiveSyncId) }

            for ((syncId, local) in localBySyncId) {
                val remote = remoteChapters[syncId]
                if (remote == null || local.lastUpdated >= remote.clientUpdatedAt) {
                    if (remote == null || local.lastUpdated > remote.clientUpdatedAt) {
                        upsertChapter(userId, local.toCloudChapter(userId, syncId)).getOrThrow()
                        pushedCh++
                    }
                } else {
                    val merged = remote.toRoomChapter(existing = local)
                    roomDb.chapterDao().insertChapter(merged)
                    pulledCh++
                }
            }
            for ((syncId, remote) in remoteChapters) {
                if (!localBySyncId.containsKey(syncId) && !remote.isDeleted) {
                    roomDb.chapterDao().insertChapter(remote.toRoomChapter(existing = null))
                    pulledCh++
                }
            }

            // 3. Sync 80-Day Schedules
            val localSchedules = roomDb.dailyScheduleDao().getAllDaysSync()
            val remoteSchedules = getUserSchedules(userId).getOrThrow().associateBy { it.id }
            val localSchedBySyncId = localSchedules.associateBy { sanitizeFirestoreDocId(it.effectiveSyncId) }

            for ((syncId, local) in localSchedBySyncId) {
                val remote = remoteSchedules[syncId]
                if (remote == null || local.updatedAt >= remote.clientUpdatedAt) {
                    if (remote == null || local.updatedAt > remote.clientUpdatedAt) {
                        upsertSchedule(userId, local.toCloudSchedule(userId, syncId)).getOrThrow()
                        pushedSched++
                    }
                } else {
                    val merged = remote.toRoomSchedule(existing = local)
                    roomDb.dailyScheduleDao().insertOrUpdate(merged)
                    pulledSched++
                }
            }
            for ((syncId, remote) in remoteSchedules) {
                if (!localSchedBySyncId.containsKey(syncId) && !remote.isDeleted) {
                    roomDb.dailyScheduleDao().insertOrUpdate(remote.toRoomSchedule(existing = null))
                    pulledSched++
                }
            }

            // 4. Sync Mistake Notebook
            val localMistakes = roomDb.mistakeEntryDao().getAllMistakesIncludingDeletedSync()
            val remoteMistakes = getUserMistakes(userId).getOrThrow().associateBy { it.id }
            val localMistBySyncId = localMistakes.associateBy { sanitizeFirestoreDocId(it.effectiveSyncId) }

            for ((syncId, local) in localMistBySyncId) {
                val remote = remoteMistakes[syncId]
                if (remote == null || local.updatedAt >= remote.clientUpdatedAt) {
                    if (remote == null || local.updatedAt > remote.clientUpdatedAt) {
                        upsertMistake(userId, local.toCloudMistake(userId, syncId)).getOrThrow()
                        pushedMist++
                    }
                } else {
                    val merged = remote.toRoomMistake(existing = local)
                    roomDb.mistakeEntryDao().insertMistake(merged)
                    pulledMist++
                }
            }
            for ((syncId, remote) in remoteMistakes) {
                if (!localMistBySyncId.containsKey(syncId) && !remote.isDeleted) {
                    roomDb.mistakeEntryDao().insertMistake(remote.toRoomMistake(existing = null))
                    pulledMist++
                }
            }

            Result.success(
                CloudSyncSummary(
                    pushedChapters = pushedCh,
                    pushedSchedules = pushedSched,
                    pushedMistakes = pushedMist,
                    pulledChapters = pulledCh,
                    pulledSchedules = pulledSched,
                    pulledMistakes = pulledMist
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeFirestoreDocId(raw: String): String {
            val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            return cleaned.take(120).ifBlank { "doc_${System.currentTimeMillis()}" }
        }

        fun resolveStringResource(context: Context, name: String): String? {
            val packagesToCheck = listOfNotNull(
                context.packageName.takeIf { it.isNotBlank() },
                "com.aistudio.jeestudyos.xqmv",
                "com.example"
            ).distinct()
            for (pkg in packagesToCheck) {
                val resId = context.resources.getIdentifier(name, "string", pkg)
                if (resId != 0) {
                    val value = runCatching { context.getString(resId) }.getOrNull()?.trim()
                    if (!value.isNullOrEmpty()) {
                        return value
                    }
                }
            }
            return null
        }

        @Synchronized
        fun ensureFirebaseInitialized(context: Context): Boolean {
            val appContext = context.applicationContext ?: context
            return try {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    return true
                }
                // 1. Standard initialization from Google Services plugin resources
                val defaultApp = FirebaseApp.initializeApp(appContext)
                if (defaultApp != null || FirebaseApp.getApps(appContext).isNotEmpty()) {
                    Log.i("FirestoreCloudSync", "Default FirebaseApp initialized via Google Services resources.")
                    return true
                }
                // 2. Fallback resource lookup across applicationId and namespace
                val appId = resolveStringResource(appContext, "google_app_id")
                val apiKey = resolveStringResource(appContext, "google_api_key")
                val projectId = resolveStringResource(appContext, "project_id")
                if (!appId.isNullOrBlank() && !apiKey.isNullOrBlank() && !projectId.isNullOrBlank()) {
                    val optionsBuilder = FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                    resolveStringResource(appContext, "google_storage_bucket")?.let {
                        if (it.isNotBlank()) optionsBuilder.setStorageBucket(it)
                    }
                    resolveStringResource(appContext, "gcm_defaultSenderId")?.let {
                        if (it.isNotBlank()) optionsBuilder.setGcmSenderId(it)
                    }
                    FirebaseApp.initializeApp(appContext, optionsBuilder.build())
                    Log.i("FirestoreCloudSync", "Default FirebaseApp initialized via explicit resource lookup [projectId=$projectId].")
                    return FirebaseApp.getApps(appContext).isNotEmpty()
                }
                Log.w(
                    "FirestoreCloudSync",
                    "Firebase configuration resources (google_app_id) not found in APK. " +
                    "Ensure app/google-services.json is present during build."
                )
                false
            } catch (e: Exception) {
                Log.e("FirestoreCloudSync", "Failed to initialize FirebaseApp [${e.javaClass.simpleName}]: ${e.message}")
                FirebaseApp.getApps(appContext).isNotEmpty()
            }
        }

        fun resolveDefaultWebClientId(context: Context): String? {
            return resolveStringResource(context, "default_web_client_id")
        }

        fun resolveFirestoreDatabaseId(context: Context): String {
            return resolveStringResource(context, "firestore_database_id")
                ?: "ai-studio-android-jeestudy-3a3e6b5c-3cf7-434b-8b3e-c727bb1c9b5a"
        }

        fun resolveFirestoreDatabase(context: Context): FirebaseFirestore {
            ensureFirebaseInitialized(context)
            return FirebaseFirestore.getInstance(resolveFirestoreDatabaseId(context))
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun ChapterEntity.toCloudChapter(userId: String, docId: String): CloudChapter {
    return CloudChapter(
        id = docId,
        userId = userId,
        subject = subject,
        unit = status,
        chapterName = chapterName,
        totalLectures = totalLectures?.toLong(),
        completedLectures = completedLectures.toLong(),
        notesDone = notesDone,
        dibyDone = dibyDone?.toLong(),
        dibyTotal = dibyTotal?.toLong(),
        classProblemsDone = classProblemsDone?.toLong(),
        classProblemsTotal = classProblemsTotal?.toLong(),
        dppDone = dppDone?.toLong(),
        dppTotal = dppTotal?.toLong(),
        pyqDone = pyqDone?.toLong(),
        pyqTotal = pyqTotal?.toLong(),
        worksheetsDone = worksheetsDone?.toLong(),
        worksheetsTotal = worksheetsTotal?.toLong(),
        masteryLevel = masteryLevel.ifBlank { "NOT_ASSESSED" },
        isWeak = isWeak,
        isDeleted = isDeleted,
        clientUpdatedAt = if (lastUpdated > 0L) lastUpdated else System.currentTimeMillis()
    )
}

private fun CloudChapter.toRoomChapter(existing: ChapterEntity?): ChapterEntity {
    return ChapterEntity(
        id = existing?.id ?: 0L,
        subject = subject,
        chapterName = chapterName,
        orderIndex = existing?.orderIndex ?: 0,
        totalLectures = totalLectures?.toInt(), // Preserves null for Organic Chemistry / unassessed totals!
        completedLectures = completedLectures.toInt(),
        notesDone = notesDone,
        dibyDone = dibyDone?.toInt(),
        dibyTotal = dibyTotal?.toInt(),
        classProblemsDone = classProblemsDone?.toInt(),
        classProblemsTotal = classProblemsTotal?.toInt(),
        dppDone = dppDone?.toInt(),
        dppTotal = dppTotal?.toInt(),
        pyqDone = pyqDone?.toInt(),
        pyqTotal = pyqTotal?.toInt(),
        worksheetsDone = worksheetsDone?.toInt(),
        worksheetsTotal = worksheetsTotal?.toInt(),
        status = unit.ifBlank { existing?.status ?: "NOT_ASSESSED" },
        masteryLevel = masteryLevel.ifBlank { existing?.masteryLevel ?: "NOT_ASSESSED" },
        isWeak = isWeak,
        lastStudiedDate = existing?.lastStudiedDate ?: "",
        revisionStage = existing?.revisionStage ?: 0,
        nextRevisionDate = existing?.nextRevisionDate ?: "",
        syncId = id,
        lastUpdated = clientUpdatedAt,
        lastSyncedAt = System.currentTimeMillis(),
        syncState = SyncRecordState.SYNCED.dbKey,
        isDeleted = isDeleted
    )
}

private fun DailyScheduleEntity.toCloudSchedule(userId: String, docId: String): CloudSchedule {
    return CloudSchedule(
        id = docId,
        userId = userId,
        date = date,
        dayNumber = dayNumber.toLong(),
        focusSubjects = notes,
        assignedChapterNames = assignedChapterNames,
        targetLectures = targetLectures.toLong(),
        completedLectures = completedLectures.toLong(),
        targetProblems = targetProblems.toLong(),
        completedProblems = completedProblems.toLong(),
        notesReviewed = notesReviewed,
        studyMinutes = studyMinutes.toLong(),
        breakMinutes = breakMinutes.toLong(),
        sleepHours = sleepHours.toDouble(),
        isSundayTest = isSunday,
        isDeleted = isDeleted,
        clientUpdatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
    )
}

private fun CloudSchedule.toRoomSchedule(existing: DailyScheduleEntity?): DailyScheduleEntity {
    return DailyScheduleEntity(
        date = date,
        dayNumber = dayNumber.toInt(),
        targetLectures = targetLectures.toInt(),
        completedLectures = completedLectures.toInt(),
        targetProblems = targetProblems.toInt(),
        completedProblems = completedProblems.toInt(),
        notesReviewed = notesReviewed,
        dibySolved = existing?.dibySolved ?: 0,
        pyqSolved = existing?.pyqSolved ?: 0,
        studyMinutes = studyMinutes.toInt(),
        breakMinutes = breakMinutes.toInt(),
        sleepHours = sleepHours.toFloat(),
        isSunday = isSundayTest,
        isRestDay = existing?.isRestDay ?: false,
        assignedChapterNames = assignedChapterNames,
        notes = focusSubjects,
        isCompleted = existing?.isCompleted ?: false,
        syncId = id,
        updatedAt = clientUpdatedAt,
        lastSyncedAt = System.currentTimeMillis(),
        syncState = SyncRecordState.SYNCED.dbKey,
        isDeleted = isDeleted
    )
}

private fun MistakeEntryEntity.toCloudMistake(userId: String, docId: String): CloudMistake {
    return CloudMistake(
        id = docId,
        userId = userId,
        subject = subject,
        chapterName = chapterName,
        testName = testName,
        questionSnippet = questionSnippet,
        errorType = errorType,
        conceptMissed = conceptMissed,
        correctTakeaway = correctTakeaway,
        lessonLearned = lessonLearned,
        solutionNotes = solutionNotes,
        reviewCount = reviewCount.toLong(),
        isResolved = isResolved,
        isDeleted = isDeleted,
        clientUpdatedAt = if (updatedAt > 0L) updatedAt else timestamp
    )
}

private fun CloudMistake.toRoomMistake(existing: MistakeEntryEntity?): MistakeEntryEntity {
    return MistakeEntryEntity(
        id = existing?.id ?: 0L,
        subject = subject,
        chapterName = chapterName,
        testName = testName,
        questionSnippet = questionSnippet,
        errorType = errorType,
        conceptMissed = conceptMissed,
        correctTakeaway = correctTakeaway,
        lessonLearned = lessonLearned,
        solutionNotes = solutionNotes,
        timestamp = existing?.timestamp ?: clientUpdatedAt,
        reviewCount = reviewCount.toInt(),
        isResolved = isResolved,
        syncId = id,
        updatedAt = clientUpdatedAt,
        lastSyncedAt = System.currentTimeMillis(),
        syncState = SyncRecordState.SYNCED.dbKey,
        isDeleted = isDeleted
    )
}
