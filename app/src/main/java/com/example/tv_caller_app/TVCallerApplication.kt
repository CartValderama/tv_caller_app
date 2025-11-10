package com.example.tv_caller_app

import android.app.Application
import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.auth.SessionRefreshManager
import com.example.tv_caller_app.repository.AuthRepository

/**
 * Application class for TV Caller App.
 * Initializes app-wide singletons and services on startup.
 */
class TVCallerApplication : Application() {

    private val TAG = "TVCallerApplication"
    private var sessionRefreshManager: SessionRefreshManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TVCallerApplication starting...")

        // Initialize SessionManager singleton
        initializeSessionManager()

        // Initialize SessionRefreshManager for automatic session refresh
        initializeSessionRefresh()

        Log.d(TAG, "TVCallerApplication initialized successfully")
    }

    /**
     * Public method to start session refresh.
     * Call this after successful login to start periodic refresh immediately.
     */
    fun startSessionRefresh() {
        try {
            if (sessionRefreshManager == null) {
                val sessionManager = SessionManager.getInstance(this)
                val authRepository = AuthRepository.getInstance(sessionManager)
                sessionRefreshManager = SessionRefreshManager(authRepository, sessionManager)
            }
            sessionRefreshManager?.startPeriodicRefresh()
            Log.d(TAG, "SessionRefreshManager started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SessionRefreshManager", e)
        }
    }

    /**
     * Public method to stop session refresh.
     * Call this after logout to stop periodic refresh.
     */
    fun stopSessionRefresh() {
        try {
            sessionRefreshManager?.stop()
            Log.d(TAG, "SessionRefreshManager stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop SessionRefreshManager", e)
        }
    }

    /**
     * Initialize SessionManager for secure session storage.
     */
    private fun initializeSessionManager() {
        try {
            SessionManager.getInstance(this)
            Log.d(TAG, "SessionManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SessionManager", e)
            // App cannot function without secure storage
            throw RuntimeException("Failed to initialize secure storage", e)
        }
    }

    /**
     * Initialize SessionRefreshManager for automatic session refresh.
     * Starts periodic refresh if user is already logged in.
     */
    private fun initializeSessionRefresh() {
        try {
            val sessionManager = SessionManager.getInstance(this)

            // Only start periodic refresh if user is already logged in
            if (sessionManager.isLoggedIn()) {
                startSessionRefresh()
                Log.d(TAG, "SessionRefreshManager started (user logged in)")
            } else {
                Log.d(TAG, "SessionRefreshManager not started (no active session)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SessionRefreshManager", e)
            // Don't crash app if session refresh fails to start
            // Auth will still work, just won't auto-refresh
        }
    }
}
