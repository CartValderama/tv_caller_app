package com.example.tv_caller_app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.AuthRepository
import com.example.tv_caller_app.repository.ContactRepository
import com.example.tv_caller_app.repository.CallHistoryRepository
import com.example.tv_caller_app.repository.QuickDialRepository
import com.example.tv_caller_app.ui.fragments.QuickDialFragment
import com.example.tv_caller_app.viewmodel.AuthViewModel
import com.example.tv_caller_app.viewmodel.AuthViewModelFactory
import kotlinx.coroutines.launch

/**
 * MainActivity - The home screen of TV Caller App.
 * Loads QuickDialFragment as the default screen.
 * Requires user to be logged in - redirects to AuthActivity if not.
 */
class MainActivity : FragmentActivity() {

    private val TAG = "MainActivity"
    private lateinit var btnLogout: TextView
    private lateinit var txtTitle: TextView
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        btnLogout = findViewById(R.id.btn_logout)
        txtTitle = findViewById(R.id.txt_title)

        // Initialize AuthViewModel
        val sessionManager = SessionManager.getInstance(this)
        val authRepository = AuthRepository.getInstance(sessionManager)
        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(authRepository)
        )[AuthViewModel::class.java]

        // Setup logout button
        setupLogoutButton()

        // Check session before loading fragments
        checkSessionAndLoadContent(savedInstanceState)
    }

    /**
     * Check if user has a valid session.
     * If yes, load main content.
     * If no, redirect to AuthActivity.
     */
    private fun checkSessionAndLoadContent(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager.getInstance(this@MainActivity)
                val authRepository = AuthRepository.getInstance(sessionManager)

                val isValid = authRepository.isSessionValid()

                if (isValid) {
                    Log.d(TAG, "Valid session - loading main content")
                    loadMainContent(savedInstanceState)
                } else {
                    Log.d(TAG, "No valid session - redirecting to AuthActivity")
                    redirectToAuth()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session: ${e.message}", e)
                redirectToAuth()
            }
        }
    }

    /**
     * Load the main content (QuickDialFragment).
     */
    private fun loadMainContent(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, QuickDialFragment())
                .commitNow()
        }
    }

    /**
     * Redirect to AuthActivity for login.
     */
    private fun redirectToAuth() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Setup logout button click listener.
     */
    private fun setupLogoutButton() {
        btnLogout.setOnClickListener {
            Log.d(TAG, "Logout button clicked")

            lifecycleScope.launch {
                try {
                    // Clear repository caches
                    val sessionManager = SessionManager.getInstance(this@MainActivity)
                    ContactRepository.getInstance(sessionManager).invalidateCache()
                    CallHistoryRepository.getInstance(sessionManager).invalidateCache()
                    QuickDialRepository.getInstance(sessionManager).invalidateCache()

                    // Stop session refresh manager
                    val app = application as com.example.tv_caller_app.TVCallerApplication
                    app.stopSessionRefresh()

                    // Sign out via AuthRepository
                    val authRepository = AuthRepository.getInstance(sessionManager)
                    val result = authRepository.signOut()

                    if (result.isSuccess) {
                        Log.d(TAG, "Logout successful")
                        Toast.makeText(
                            this@MainActivity,
                            "Logged out successfully",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Redirect to auth screen
                        redirectToAuth()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Logout failed"
                        Log.e(TAG, "Logout failed: $error")
                        Toast.makeText(
                            this@MainActivity,
                            "Logout failed: $error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Logout error: ${e.message}", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Logout error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Show the title and logout button.
     * Called when navigating to main fragments.
     */
    fun showHeader() {
        txtTitle.visibility = View.VISIBLE
        btnLogout.visibility = View.VISIBLE
    }

    /**
     * Hide the title and logout button.
     * Called when navigating to detail/edit screens.
     */
    fun hideHeader() {
        txtTitle.visibility = View.GONE
        btnLogout.visibility = View.GONE
    }
}