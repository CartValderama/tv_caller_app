package com.example.tv_caller_app.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SessionManager handles secure storage and retrieval of user session data.
 * Uses EncryptedSharedPreferences to store tokens and user information securely.
 * Singleton pattern ensures single instance across the app.
 */
class SessionManager private constructor(context: Context) {

    private val encryptedPrefs: SharedPreferences
    private val TAG = "SessionManager"

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }

        // SharedPreferences keys
        private const val PREF_NAME = "auth_session_encrypted"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EMAIL = "email"
        private const val KEY_EMAIL_CONFIRMED = "email_confirmed"
        private const val KEY_SESSION_TIMESTAMP = "session_timestamp"
    }

    init {
        try {
            // Create or retrieve MasterKey for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // Create EncryptedSharedPreferences
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            Log.d(TAG, "SessionManager initialized with encrypted storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
            throw RuntimeException("Failed to initialize secure storage", e)
        }
    }

    /**
     * Save user session data securely.
     * @param userId User's unique ID from Supabase
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @param email User's email address
     * @param emailConfirmed Whether email has been verified
     */
    fun saveSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
        email: String,
        emailConfirmed: Boolean
    ) {
        try {
            encryptedPrefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putString(KEY_EMAIL, email)
                putBoolean(KEY_EMAIL_CONFIRMED, emailConfirmed)
                putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Session saved for user: $userId (email: $email)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session", e)
        }
    }

    /**
     * Get the current user's ID.
     * @return User ID or null if not logged in
     */
    fun getUserId(): String? {
        return try {
            encryptedPrefs.getString(KEY_USER_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user ID", e)
            null
        }
    }

    /**
     * Get the current access token.
     * @return Access token or null if not logged in
     */
    fun getAccessToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving access token", e)
            null
        }
    }

    /**
     * Get the current refresh token.
     * @return Refresh token or null if not logged in
     */
    fun getRefreshToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving refresh token", e)
            null
        }
    }

    /**
     * Get the current user's email.
     * @return Email address or null if not logged in
     */
    fun getEmail(): String? {
        return try {
            encryptedPrefs.getString(KEY_EMAIL, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving email", e)
            null
        }
    }

    /**
     * Check if user's email has been confirmed.
     * @return True if email is confirmed, false otherwise
     */
    fun isEmailConfirmed(): Boolean {
        return try {
            encryptedPrefs.getBoolean(KEY_EMAIL_CONFIRMED, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking email confirmation", e)
            false
        }
    }

    /**
     * Get the timestamp when the session was created.
     * @return Timestamp in milliseconds or 0 if not found
     */
    fun getSessionTimestamp(): Long {
        return try {
            encryptedPrefs.getLong(KEY_SESSION_TIMESTAMP, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving session timestamp", e)
            0L
        }
    }

    /**
     * Check if user is logged in.
     * @return True if user has a valid session, false otherwise
     */
    fun isLoggedIn(): Boolean {
        val userId = getUserId()
        val accessToken = getAccessToken()
        val isLoggedIn = !userId.isNullOrBlank() && !accessToken.isNullOrBlank()

        Log.d(TAG, "isLoggedIn check: $isLoggedIn (userId: ${userId?.take(8)}...)")
        return isLoggedIn
    }

    /**
     * Update email confirmation status.
     * @param confirmed Whether email is now confirmed
     */
    fun updateEmailConfirmed(confirmed: Boolean) {
        try {
            encryptedPrefs.edit().apply {
                putBoolean(KEY_EMAIL_CONFIRMED, confirmed)
                apply()
            }
            Log.d(TAG, "Email confirmation status updated: $confirmed")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating email confirmation", e)
        }
    }

    /**
     * Clear all session data (logout).
     * Removes all stored credentials and user information.
     */
    fun clearSession() {
        try {
            val userId = getUserId()
            encryptedPrefs.edit().clear().apply()
            Log.d(TAG, "Session cleared for user: ${userId?.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session", e)
        }
    }

    /**
     * Check if session exists and is relatively fresh.
     * @param maxAgeHours Maximum age of session in hours (default 24)
     * @return True if session exists and is not too old
     */
    fun hasRecentSession(maxAgeHours: Int = 24): Boolean {
        if (!isLoggedIn()) return false

        val timestamp = getSessionTimestamp()
        if (timestamp == 0L) return false

        val ageMillis = System.currentTimeMillis() - timestamp
        val ageHours = ageMillis / (1000 * 60 * 60)

        return ageHours < maxAgeHours
    }

    /**
     * Get all session data for debugging (DO NOT log in production).
     * @return Map of session data with sensitive info redacted
     */
    fun getSessionInfo(): Map<String, String> {
        return mapOf(
            "userId" to (getUserId()?.take(8) ?: "null") + "...",
            "email" to (getEmail() ?: "null"),
            "emailConfirmed" to isEmailConfirmed().toString(),
            "hasAccessToken" to (getAccessToken() != null).toString(),
            "hasRefreshToken" to (getRefreshToken() != null).toString(),
            "sessionAge" to "${(System.currentTimeMillis() - getSessionTimestamp()) / 1000 / 60} minutes"
        )
    }
}
