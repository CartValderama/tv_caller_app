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
import com.example.tv_caller_app.calling.webrtc.WebRTCManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Run WebRTC test
        testWebRTC()
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

    /**
     * Test WebRTC initialization and functionality.
     * This verifies Day 3-4 Part 1 implementation.
     */
    private fun testWebRTC() {
        lifecycleScope.launch {
            try {
                Log.d("WEBRTC_TEST", "========================================")
                Log.d("WEBRTC_TEST", "Starting WebRTC Test (Day 3-4 Part 1)")
                Log.d("WEBRTC_TEST", "========================================")

                // CRITICAL: WebRTC PeerConnectionFactory.initialize() must be called on main thread
                // But all other operations should run on background thread
                Log.d("WEBRTC_TEST", "Test 1: Initializing WebRTC...")
                val webrtc = WebRTCManager(applicationContext)

                // Initialize on main thread first
                Log.d("WEBRTC_TEST", "Initializing PeerConnectionFactory on main thread...")
                webrtc.initialize()

                // Run other WebRTC operations on background thread
                val mode = withContext(Dispatchers.Default) {
                    Log.d("WEBRTC_TEST", "✅ WebRTC initialized successfully")

                    // Test 2: Check microphone mode and status
                    Log.d("WEBRTC_TEST", "Test 2: Checking microphone status...")
                    val currentMode = webrtc.getMicrophoneMode()
                    val status = webrtc.getMicrophoneStatus()

                    // List ALL detected audio devices
                    val detector = com.example.tv_caller_app.calling.audio.AudioDeviceDetector(applicationContext)
                    val allMics = detector.getAvailableMicrophones()
                    Log.d("WEBRTC_TEST", "🔍 ALL DETECTED AUDIO INPUT DEVICES:")
                    if (allMics.isEmpty()) {
                        Log.d("WEBRTC_TEST", "   ⚠️ No audio input devices detected by Android!")
                    } else {
                        allMics.forEachIndexed { index, mic ->
                            Log.d("WEBRTC_TEST", "   ${index + 1}. $mic")
                        }
                    }

                    Log.d("WEBRTC_TEST", "📱 Microphone Mode: $currentMode")
                    Log.d("WEBRTC_TEST", "🎤 Mic Available: ${status.isAvailable}")
                    Log.d("WEBRTC_TEST", "🎤 Has Permission: ${status.hasPermission}")
                    Log.d("WEBRTC_TEST", "🎤 Device Type: ${status.deviceType}")
                    Log.d("WEBRTC_TEST", "🎤 Device Name: ${status.deviceName}")
                    Log.d("WEBRTC_TEST", "📝 Status Message: ${status.message}")

                    // Test 3: Create SDP offer
                    Log.d("WEBRTC_TEST", "Test 3: Creating SDP offer...")
                    val offer = webrtc.createOffer()
                    Log.d("WEBRTC_TEST", "✅ Offer created successfully")
                    Log.d("WEBRTC_TEST", "📄 Offer (first 150 chars): ${offer.take(150)}...")

                    // Test 4: Verify SDP format
                    Log.d("WEBRTC_TEST", "Test 4: Validating SDP format...")
                    val isValidSDP = offer.startsWith("v=0")
                    if (isValidSDP) {
                        Log.d("WEBRTC_TEST", "✅ Valid SDP format confirmed")
                    } else {
                        Log.e("WEBRTC_TEST", "❌ Invalid SDP format")
                    }

                    // Test 5: Check connection state
                    Log.d("WEBRTC_TEST", "Test 5: Checking connection state...")
                    val connectionState = webrtc.connectionState.value
                    Log.d("WEBRTC_TEST", "🔌 Connection State: $connectionState")

                    // Cleanup
                    Log.d("WEBRTC_TEST", "Cleaning up WebRTC resources...")
                    webrtc.cleanup()
                    Log.d("WEBRTC_TEST", "✅ Cleanup completed successfully")

                    Log.d("WEBRTC_TEST", "========================================")
                    Log.d("WEBRTC_TEST", "✅ ALL TESTS PASSED!")
                    Log.d("WEBRTC_TEST", "========================================")

                    // Return mode for display in toast
                    currentMode
                } // End withContext(Dispatchers.Default)

                // Show success toast on main thread
                val modeText = when (mode) {
                    WebRTCManager.MicrophoneMode.TWO_WAY -> "TWO_WAY (can send & receive)"
                    WebRTCManager.MicrophoneMode.RECEIVE_ONLY -> "RECEIVE_ONLY (no mic)"
                }
                Toast.makeText(
                    this@MainActivity,
                    "✅ WebRTC Test PASSED!\nMode: $modeText",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Log.e("WEBRTC_TEST", "========================================")
                Log.e("WEBRTC_TEST", "❌ WebRTC Test FAILED", e)
                Log.e("WEBRTC_TEST", "Error: ${e.message}")
                Log.e("WEBRTC_TEST", "Stack trace:")
                e.printStackTrace()
                Log.e("WEBRTC_TEST", "========================================")

                Toast.makeText(
                    this@MainActivity,
                    "❌ WebRTC Test Failed!\n${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}