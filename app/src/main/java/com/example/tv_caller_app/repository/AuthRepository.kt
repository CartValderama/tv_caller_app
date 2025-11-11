package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.ProfileInsert
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Repository for handling authentication operations.
 * Manages sign up, sign in, sign out, and session management with Supabase Auth.
 * Singleton pattern ensures single instance across app.
 */
class AuthRepository private constructor(
    private val sessionManager: SessionManager
) {

    private val supabase = SupabaseClient.client
    private val TAG = "AuthRepository"

    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(sessionManager: SessionManager): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(sessionManager).also { instance = it }
            }
        }
    }

    /**
     * Sign up a new user with email and password.
     * Automatically sends verification email.
     * User cannot log in until email is verified.
     * Profile will be created on first login after verification.
     *
     * @param email User's email address
     * @param password User's password
     * @param username User's username (stored for later profile creation)
     * @param phoneNumber User's phone number (stored for later profile creation)
     * @return Result with success message on success, Exception on failure
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String? = null,
        phoneNumber: String? = null
    ): Result<String> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Attempting sign up for email: $email, username: $username, phoneNumber: $phoneNumber")

                // Sign up user with Supabase Auth and store username and phoneNumber in user metadata
                val authResponse = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password

                    // Store username and phoneNumber in user metadata so we can retrieve it on first login
                    if (username != null || phoneNumber != null) {
                        data = buildJsonObject {
                            if (username != null) put("username", username)
                            if (phoneNumber != null) put("phone_number", phoneNumber)
                        }
                    }
                }

                val userId = authResponse?.id
                if (userId != null) {
                    Log.d(TAG, "User created with ID: $userId, username stored in metadata")
                    // Note: Profile will be created on first login after email verification
                }

                Log.d(TAG, "Sign up successful, verification email sent to: $email")
                Result.success("Verification email sent to $email")
            } catch (e: Exception) {
                Log.e(TAG, "Sign up failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    /**
     * Create a profile entry for a new user.
     * Called on first login after email verification.
     *
     * @param userId User's unique ID from Supabase Auth
     * @param email User's email address
     * @param username User's username (optional, defaults to username from email)
     * @param phoneNumber User's phone number (optional)
     */
    private suspend fun createProfile(
        userId: String,
        email: String,
        username: String? = null,
        phoneNumber: String? = null
    ) {
        try {
            // Use provided username or extract from email (part before @)
            val finalUsername = username ?: email.substringBefore("@")

            val profileInsert = ProfileInsert(
                id = userId,
                username = finalUsername,
                email = email,
                phoneNumber = phoneNumber
            )

            supabase.from("profiles").insert(profileInsert)

            Log.d(TAG, "Profile created successfully for user: $userId (username: $finalUsername, email: $email, phoneNumber: $phoneNumber)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create profile: ${e.message}", e)
            throw e
        }
    }

    /**
     * Sign in with email and password.
     * Only works if email has been verified.
     * Creates profile on first login if it doesn't exist.
     * Saves session automatically on success.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result with UserSession on success, Exception on failure
     */
    suspend fun signIn(email: String, password: String): Result<UserSession> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Attempting sign in for email: $email")

                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Get current session after sign in
                val session = supabase.auth.currentSessionOrNull()
                    ?: throw Exception("Authentication failed - no session created")

                // Get current user info
                val user = supabase.auth.currentUserOrNull()
                    ?: throw Exception("Authentication failed - no user info")

                // Check if email is confirmed
                val emailConfirmed = user.confirmedAt != null

                if (!emailConfirmed) {
                    // Sign out immediately if email not confirmed
                    supabase.auth.signOut()
                    throw Exception("Please verify your email before signing in. Check your inbox for the verification link.")
                }

                val userId = session.user?.id ?: throw Exception("No user ID in session")

                // Check if profile exists, create if not (first login after verification)
                try {
                    ensureProfileExists(userId, user.email ?: email)
                } catch (profileError: Exception) {
                    Log.w(TAG, "Profile creation/check failed: ${profileError.message}")
                    // Don't fail login if profile operations fail
                }

                // Save session to SessionManager
                sessionManager.saveSession(
                    userId = userId,
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken ?: "",
                    email = user.email ?: email,
                    emailConfirmed = emailConfirmed
                )

                Log.d(TAG, "Sign in successful for user: $userId")
                Result.success(session)
            } catch (e: Exception) {
                Log.e(TAG, "Sign in failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    /**
     * Ensure profile exists for user, create if it doesn't.
     * Called on first login after email verification.
     * Retrieves username and phoneNumber from user metadata if available.
     *
     * @param userId User's unique ID
     * @param email User's email address
     */
    private suspend fun ensureProfileExists(userId: String, email: String) {
        try {
            // Check if profile already exists
            val existingProfile = supabase.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileInsert>()

            if (existingProfile == null) {
                // Profile doesn't exist, create it
                Log.d(TAG, "Profile not found for user $userId, creating...")

                // Get username and phoneNumber from user metadata (stored during registration)
                val user = supabase.auth.currentUserOrNull()

                // Debug: Log all metadata
                Log.d(TAG, "User metadata: ${user?.userMetadata}")
                Log.d(TAG, "Raw metadata keys: ${user?.userMetadata?.keys}")

                // Extract username from JsonElement properly
                val username = try {
                    user?.userMetadata?.get("username")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting username from metadata: ${e.message}")
                    null
                }

                // Extract phoneNumber from JsonElement properly
                val phoneNumber = try {
                    user?.userMetadata?.get("phone_number")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting phone_number from metadata: ${e.message}")
                    null
                }

                Log.d(TAG, "Retrieved username from metadata: '$username'")
                Log.d(TAG, "Retrieved phoneNumber from metadata: '$phoneNumber'")

                if (username == null) {
                    Log.w(TAG, "username is null! Using username from email as fallback")
                }

                createProfile(userId, email, username, phoneNumber)
            } else {
                Log.d(TAG, "Profile already exists for user $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring profile exists: ${e.message}", e)
            throw e
        }
    }

    /**
     * Sign out current user.
     * Clears session from both Supabase and SessionManager.
     *
     * @return Result with Unit on success, Exception on failure
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
            Log.d(TAG, "Signing out user: $userId")

            // Sign out from Supabase
            supabase.auth.signOut()

            // Clear local session
            sessionManager.clearSession()

            Log.d(TAG, "Sign out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed: ${e.message}", e)
            // Clear session anyway on error
            sessionManager.clearSession()
            Result.failure(e)
        }
    }

    /**
     * Get currently authenticated user from Supabase.
     *
     * @return User info or null if not authenticated
     */
    suspend fun getCurrentUser(): io.github.jan.supabase.gotrue.user.UserInfo? {
        return try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}", e)
            null
        }
    }

    /**
     * Refresh current session if expired.
     * Updates SessionManager with new tokens.
     *
     * @return Result with Unit on success, Exception on failure
     */
    suspend fun refreshSession(): Result<Unit> {
        return try {
            Log.d(TAG, "Refreshing session...")

            supabase.auth.refreshCurrentSession()

            // Get the refreshed session
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("No session found after refresh")

            val user = supabase.auth.currentUserOrNull()
                ?: throw Exception("No user found after refresh")

            // Update session in SessionManager
            sessionManager.saveSession(
                userId = user.id,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken ?: "",
                email = user.email ?: sessionManager.getEmail() ?: "",
                emailConfirmed = user.confirmedAt != null
            )

            Log.d(TAG, "Session refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Session refresh failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resend verification email to user.
     *
     * @param email User's email address
     * @return Result with Unit on success, Exception on failure
     */
    suspend fun resendVerificationEmail(email: String): Result<Unit> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Resending verification email to: $email")

                supabase.auth.resendEmail(
                    type = OtpType.Email.SIGNUP,
                    email = email
                )

                Log.d(TAG, "Verification email resent successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resend verification email: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    /**
     * Request password reset email.
     *
     * @param email User's email address
     * @return Result with Unit on success, Exception on failure
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Requesting password reset for: $email")

                supabase.auth.resetPasswordForEmail(email)

                Log.d(TAG, "Password reset email sent successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Password reset failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    /**
     * Check if user session is valid and not expired.
     * On app startup, we trust SessionManager's stored tokens.
     * Supabase session will be validated on first actual API call.
     *
     * @return True if session is valid, false otherwise
     */
    suspend fun isSessionValid(): Boolean {
        return try {
            // Check if SessionManager has valid tokens
            val isLoggedIn = sessionManager.isLoggedIn()

            if (!isLoggedIn) {
                Log.d(TAG, "No stored session found")
                return false
            }

            // We have tokens - trust them for now
            // They'll be validated on first API call (contacts, quick dial, etc.)
            Log.d(TAG, "Valid stored session found")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session validity: ${e.message}", e)
            false
        }
    }

    /**
     * Map Supabase exceptions to user-friendly error messages.
     *
     * @param e Exception from Supabase
     * @return Exception with user-friendly message
     */
    private fun mapAuthException(e: Exception): Exception {
        val message = when {
            // Authentication errors
            e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                "Invalid email or password"
            e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                "Please verify your email before signing in. Check your inbox."
            e.message?.contains("User already registered", ignoreCase = true) == true ->
                "An account with this email already exists. Try logging in instead."
            e.message?.contains("User not found", ignoreCase = true) == true ->
                "No account found with this email"
            e.message?.contains("Invalid email", ignoreCase = true) == true ->
                "Please enter a valid email address"

            // Email verification errors
            e.message?.contains("Email link is invalid", ignoreCase = true) == true ->
                "Verification link is invalid or expired. Request a new one."
            e.message?.contains("OTP expired", ignoreCase = true) == true ->
                "Verification code expired. Request a new one."

            // Password errors
            e.message?.contains("Password", ignoreCase = true) == true &&
            e.message?.contains("weak", ignoreCase = true) == true ->
                "Password is too weak. Use at least 8 characters with uppercase, lowercase, number, and special character."
            e.message?.contains("Password should be at least", ignoreCase = true) == true ->
                "Password must be at least 8 characters long"

            // Network errors
            e.message?.contains("network", ignoreCase = true) == true ||
            e.message?.contains("timeout", ignoreCase = true) == true ->
                "Network error. Please check your connection and try again."
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                "Cannot reach server. Check your internet connection."

            // Session errors
            e.message?.contains("refresh_token_not_found", ignoreCase = true) == true ->
                "Session expired. Please log in again."
            e.message?.contains("invalid_grant", ignoreCase = true) == true ->
                "Session expired. Please log in again."
            e.message?.contains("JWT", ignoreCase = true) == true ||
            e.message?.contains("token", ignoreCase = true) == true ->
                "Session expired. Please log in again."

            // Rate limiting
            e.message?.contains("rate limit", ignoreCase = true) == true ||
            e.message?.contains("too many requests", ignoreCase = true) == true ->
                "Too many attempts. Please wait a moment and try again."

            // Email already taken
            e.message?.contains("already been taken", ignoreCase = true) == true ->
                "This email is already registered. Try logging in instead."

            // Generic fallback
            else -> {
                Log.w(TAG, "Unmapped auth error: ${e.message}")
                "Authentication error. Please try again."
            }
        }

        return Exception(message)
    }

    /**
     * Retry logic for network operations with exponential backoff.
     * Only retries on network errors, not on authentication failures.
     *
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelay Initial delay in milliseconds before first retry
     * @param maxDelay Maximum delay in milliseconds between retries
     * @param factor Multiplier for exponential backoff
     * @param block The operation to retry
     * @return Result from the operation
     */
    private suspend fun <T> retryOnNetworkError(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 5000L,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay

        repeat(maxRetries) { attempt ->
            val result = block()

            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull()
            val isNetworkError = error?.message?.contains("network", ignoreCase = true) == true ||
                    error?.message?.contains("timeout", ignoreCase = true) == true ||
                    error?.message?.contains("Unable to resolve host", ignoreCase = true) == true

            if (isNetworkError) {
                if (attempt < maxRetries - 1) {
                    Log.d(TAG, "Network error on attempt ${attempt + 1}, retrying after ${currentDelay}ms")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    Log.w(TAG, "Network error after $maxRetries attempts, giving up")
                }
            } else {
                // Non-network error, don't retry
                Log.d(TAG, "Non-network error, not retrying: ${error?.message}")
                return result
            }
        }

        return Result.failure(Exception("Network error after $maxRetries attempts. Please check your connection."))
    }
}
