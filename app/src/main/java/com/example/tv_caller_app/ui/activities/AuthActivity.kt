package com.example.tv_caller_app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.AuthRepository
import com.example.tv_caller_app.ui.fragments.LoginFragment
import kotlinx.coroutines.launch

/**
 * AuthActivity handles user authentication (login and registration).
 * This is the launcher activity. Checks for existing session on startup.
 */
class AuthActivity : FragmentActivity() {

    private val TAG = "AuthActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Check if user is already logged in
        checkExistingSession()
    }

    /**
     * Check if user has a valid session.
     * If yes, redirect to MainActivity.
     * If no, show login screen.
     */
    private fun checkExistingSession() {
        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager.getInstance(this@AuthActivity)
                val authRepository = AuthRepository.getInstance(sessionManager)

                // Check if session is valid
                val isValid = authRepository.isSessionValid()

                if (isValid) {
                    Log.d(TAG, "Valid session found, redirecting to MainActivity")
                    navigateToMainActivity()
                } else {
                    Log.d(TAG, "No valid session, showing login screen")
                    showLoginScreen()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session: ${e.message}", e)
                // On error, show login screen
                showLoginScreen()
            }
        }
    }

    /**
     * Show login screen.
     */
    private fun showLoginScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.auth_fragment_container, LoginFragment())
            .commitNow()
    }

    /**
     * Navigate to MainActivity and clear auth activity from stack.
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
