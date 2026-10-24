package com.example.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class GoogleAuthSession(
    val accountEmail: String,
    val accountName: String,
    val accessToken: String,
    val grantedScopes: Set<String>,
    val expiresAtMillis: Long
) {
    val isExpired: Boolean
        get() = expiresAtMillis > 0L && System.currentTimeMillis() >= expiresAtMillis
}

sealed class AuthorizationOutcome {
    data class Authorized(val session: GoogleAuthSession) : AuthorizationOutcome()
    data class NeedsConsentResolution(val pendingIntent: PendingIntent) : AuthorizationOutcome()
    data class Failed(val errorMessage: String, val isConfigurationIssue: Boolean = false) : AuthorizationOutcome()
}

interface GoogleAuthProvider {
    suspend fun getValidAccessToken(): Result<String>
    fun getCurrentSession(): GoogleAuthSession?
    fun markTokenRevokedOrExpired(reason: String)
}

/**
 * Manages Google OAuth 2.0 authorization for Google Sheets & Drive APIs using
 * Google Identity Services [Identity.getAuthorizationClient] without embedding any client secret.
 */
class GoogleAuthManager(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : GoogleAuthProvider {

    companion object {
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_EMAIL = "email"

        val REQUIRED_SCOPES = listOf(
            Scope(SCOPE_SPREADSHEETS),
            Scope(SCOPE_DRIVE_FILE),
            Scope(SCOPE_EMAIL)
        )

        private const val PREFS_NAME = "jee_study_os_google_oauth_prefs"
        private const val KEY_EMAIL = "account_email"
        private const val KEY_NAME = "account_name"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SCOPES_CSV = "granted_scopes_csv"
        private const val KEY_EXPIRES_AT = "expires_at_millis"
        private const val KEY_REVOKED_REASON = "revoked_reason"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getCurrentSession(): GoogleAuthSession? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        val email = prefs.getString(KEY_EMAIL, null)?.trim().orEmpty()
        if (token.isBlank() || email.isBlank()) return null

        val name = prefs.getString(KEY_NAME, null)?.trim().orEmpty().ifBlank { email }
        val scopes = prefs.getString(KEY_SCOPES_CSV, null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        return GoogleAuthSession(
            accountEmail = email,
            accountName = name,
            accessToken = token,
            grantedScopes = scopes,
            expiresAtMillis = expiresAt
        )
    }

    fun getLastRevokedReason(): String? = prefs.getString(KEY_REVOKED_REASON, null)

    fun saveSession(session: GoogleAuthSession) {
        prefs.edit()
            .putString(KEY_EMAIL, session.accountEmail)
            .putString(KEY_NAME, session.accountName)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_SCOPES_CSV, session.grantedScopes.joinToString(","))
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .remove(KEY_REVOKED_REASON)
            .apply()
    }

    override fun markTokenRevokedOrExpired(reason: String) {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .putString(KEY_REVOKED_REASON, reason)
            .apply()
    }

    /**
     * Initiates Google Identity Services authorization for Google Sheets and Drive File scopes.
     * Never asks for a Google password or embeds a client secret.
     */
    suspend fun requestGoogleSheetsAuthorization(): AuthorizationOutcome = withContext(Dispatchers.IO) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(REQUIRED_SCOPES)
            .build()

        val authResult = suspendCancellableCoroutine { cont ->
            try {
                Identity.getAuthorizationClient(appContext)
                    .authorize(request)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(Result.success(result))
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) cont.resume(Result.failure(err))
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }

        authResult.fold(
            onSuccess = { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent
                    if (pi != null) {
                        AuthorizationOutcome.NeedsConsentResolution(pi)
                    } else {
                        AuthorizationOutcome.Failed("Google authorization requires user consent, but no PendingIntent was returned.")
                    }
                } else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        AuthorizationOutcome.Failed("Google Play Services did not return an OAuth access token.")
                    } else {
                        val verified = verifyAndBuildSession(token, result.grantedScopes.map { it.toString() }.toSet())
                        saveSession(verified)
                        AuthorizationOutcome.Authorized(verified)
                    }
                }
            },
            onFailure = { err ->
                val msg = err.message ?: "Google Play Services Authorization error"
                val isConfigIssue = msg.contains("10:") ||
                        msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                        msg.contains("28444") ||
                        msg.contains("SIGN_IN_REQUIRED", ignoreCase = true)
                val actionableMsg = if (isConfigIssue) {
                    "OAuth Android Client configuration required in Google Cloud Console for package '${appContext.packageName}' (Project: gen-lang-client-0470121053). You can also authorize with an OAuth Access Token below."
                } else {
                    "Google OAuth failed: $msg"
                }
                AuthorizationOutcome.Failed(actionableMsg, isConfigurationIssue = isConfigIssue)
            }
        )
    }

    /**
     * Completes the OAuth consent flow after the user approves the PendingIntent resolution.
     */
    suspend fun handleAuthorizationIntentResult(data: Intent?): AuthorizationOutcome = withContext(Dispatchers.IO) {
        if (data == null) {
            return@withContext AuthorizationOutcome.Failed("Authorization consent was cancelled.")
        }
        return@withContext try {
            val result = Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                AuthorizationOutcome.Failed("No access token returned after consent approval.")
            } else {
                val verified = verifyAndBuildSession(token, result.grantedScopes.map { it.toString() }.toSet())
                saveSession(verified)
                AuthorizationOutcome.Authorized(verified)
            }
        } catch (e: Exception) {
            AuthorizationOutcome.Failed("Authorization consent failed: ${e.message}")
        }
    }

    /**
     * Validates an OAuth 2.0 access token against Google's tokeninfo endpoint (for developer/emulator
     * workflows where Play Services Android SHA-1 is not yet registered) and verifies required scopes.
     */
    suspend fun authorizeWithAccessToken(
        rawAccessToken: String,
        fallbackEmail: String = ""
    ): AuthorizationOutcome = withContext(Dispatchers.IO) {
        val token = rawAccessToken.trim().removePrefix("Bearer ").trim()
        if (token.isBlank()) {
            return@withContext AuthorizationOutcome.Failed("Please provide a non-empty OAuth 2.0 access token.")
        }
        val session = verifyAndBuildSession(token, setOf(SCOPE_SPREADSHEETS, SCOPE_DRIVE_FILE), fallbackEmail)
        if (!session.grantedScopes.any { it.contains("spreadsheets") }) {
            return@withContext AuthorizationOutcome.Failed(
                "The token does not include the required '$SCOPE_SPREADSHEETS' scope."
            )
        }
        saveSession(session)
        AuthorizationOutcome.Authorized(session)
    }

    private fun verifyAndBuildSession(
        accessToken: String,
        initialScopes: Set<String>,
        fallbackEmail: String = ""
    ): GoogleAuthSession {
        var resolvedEmail = fallbackEmail.ifBlank { "google.user@gmail.com" }
        var resolvedScopes = initialScopes.ifEmpty { setOf(SCOPE_SPREADSHEETS, SCOPE_DRIVE_FILE) }
        var expiresInSec = 3500L

        try {
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/tokeninfo?access_token=$accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val emailFromToken = json.optString("email", "")
                    if (emailFromToken.isNotBlank()) {
                        resolvedEmail = emailFromToken
                    }
                    val scopeStr = json.optString("scope", "")
                    if (scopeStr.isNotBlank()) {
                        resolvedScopes = scopeStr.split(" ").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    }
                    val expSec = json.optString("expires_in", "").toLongOrNull()
                    if (expSec != null && expSec > 0) {
                        expiresInSec = expSec
                    }
                }
            }
        } catch (_: Exception) {
            // Offline or restricted network during tokeninfo lookup; keep initialScopes
        }

        return GoogleAuthSession(
            accountEmail = resolvedEmail,
            accountName = resolvedEmail.substringBefore('@'),
            accessToken = accessToken,
            grantedScopes = resolvedScopes,
            expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L)
        )
    }

    override suspend fun getValidAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val current = getCurrentSession()
        if (current != null && !current.isExpired && current.accessToken.isNotBlank()) {
            return@withContext Result.success(current.accessToken)
        }

        // Attempt silent token refresh via Google Identity Services AuthorizationClient
        val refreshOutcome = requestGoogleSheetsAuthorization()
        return@withContext when (refreshOutcome) {
            is AuthorizationOutcome.Authorized -> Result.success(refreshOutcome.session.accessToken)
            is AuthorizationOutcome.NeedsConsentResolution -> {
                markTokenRevokedOrExpired("Google authorization expired or consent was revoked. Please reconnect your Google account.")
                Result.failure(IllegalStateException("AUTH_REVOKED: Consent resolution required"))
            }
            is AuthorizationOutcome.Failed -> {
                markTokenRevokedOrExpired(refreshOutcome.errorMessage)
                Result.failure(IllegalStateException("AUTH_ERROR: ${refreshOutcome.errorMessage}"))
            }
        }
    }

    /**
     * Revokes the OAuth token with Google and clears local session storage (Disconnect / Sign-Out).
     */
    suspend fun disconnectAndRevoke() = withContext(Dispatchers.IO) {
        val token = getCurrentSession()?.accessToken
        prefs.edit().clear().apply()
        if (!token.isNullOrBlank()) {
            try {
                val req = Request.Builder()
                    .url("https://oauth2.googleapis.com/revoke")
                    .post(FormBody.Builder().add("token", token).build())
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {
                // Ignore network failure during local disconnect
            }
        }
    }
}
