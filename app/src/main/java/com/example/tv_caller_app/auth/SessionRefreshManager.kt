package com.example.tv_caller_app.auth

import android.util.Log
import com.example.tv_caller_app.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages automatic session refresh in the background.
 * Periodically checks and refreshes the authentication session to prevent expiry.
 */
class SessionRefreshManager(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) {

    private val TAG = "SessionRefreshManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    /**
     * Start periodic session refresh.
     * Checks every 30 minutes if session needs refresh.
     */
    fun startPeriodicRefresh() {
        if (isRunning) {
            Log.d(TAG, "Periodic refresh already running")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting periodic session refresh")

        scope.launch {
            while (isRunning) {
                delay(30 * 60 * 1000L) // Wait 30 minutes

                if (sessionManager.isLoggedIn()) {
                    Log.d(TAG, "Attempting session refresh")
                    authRepository.refreshSession()
                        .onSuccess {
                            Log.d(TAG, "Session refreshed successfully")
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Session refresh failed: ${error.message}")
                            // Could trigger logout or show re-auth dialog here
                            // For now, just log the error and continue
                        }
                } else {
                    Log.d(TAG, "User not logged in, skipping refresh")
                }
            }
        }
    }

    /**
     * Stop the periodic refresh.
     * Should be called when user logs out or app is destroyed.
     */
    fun stop() {
        Log.d(TAG, "Stopping periodic session refresh")
        isRunning = false
        scope.cancel()
    }
}
