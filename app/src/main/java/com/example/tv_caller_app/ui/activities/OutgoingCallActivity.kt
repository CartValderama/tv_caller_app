package com.example.tv_caller_app.ui.activities

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.tv_caller_app.R
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.viewmodel.CallViewModel

/**
 * OutgoingCallActivity - UI for outgoing calls (ringing state).
 *
 * Features:
 * - Shows contact information
 * - Displays "Calling..." status
 * - Cancel button to hang up before answer
 * - Timeout handling (no answer after X seconds)
 * - Transition to InCallActivity when answered
 *
 * Intent extras:
 * - EXTRA_CONTACT_ID: String - Contact user ID
 * - EXTRA_CONTACT_NAME: String - Contact display name
 * - EXTRA_CONTACT_PHONE: String (optional) - Contact phone number
 * - EXTRA_CALL_ID: String - Unique call ID
 */
class OutgoingCallActivity : FragmentActivity() {

    private val TAG = "OutgoingCallActivity"

    // UI Components
    private lateinit var txtContactName: TextView
    private lateinit var txtContactPhone: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var btnHangUp: Button

    // ViewModel
    private lateinit var callViewModel: CallViewModel

    // Call data
    private var contactId: String? = null
    private var contactName: String? = null
    private var contactPhone: String? = null
    private var callId: String? = null

    // Timeout handling
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val callTimeoutMs = 60000L // 60 seconds timeout

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_CONTACT_PHONE = "contact_phone"
        const val EXTRA_CALL_ID = "call_id"

        /**
         * Create intent to launch OutgoingCallActivity.
         */
        fun createIntent(
            context: Context,
            contactId: String,
            contactName: String,
            callId: String,
            contactPhone: String? = null
        ): Intent {
            return Intent(context, OutgoingCallActivity::class.java).apply {
                putExtra(EXTRA_CONTACT_ID, contactId)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CONTACT_PHONE, contactPhone)
                putExtra(EXTRA_CALL_ID, callId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window
        setupWindow()

        setContentView(R.layout.activity_outgoing_call)

        // Get the global CallViewModel from application (don't create a new one!)
        val app = application as TVCallerApplication
        val globalCallViewModel = app.getCallViewModel()

        if (globalCallViewModel == null) {
            Log.e(TAG, "Global CallViewModel not initialized!")
            Toast.makeText(this, "Call service not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        callViewModel = globalCallViewModel

        // Extract intent data
        extractIntentData()

        // Initialize UI
        initializeViews()
        updateUI()

        // Setup button listeners
        setupButtonListeners()

        // Observe ViewModel state
        observeViewModel()

        // Start ringback tone (dial tone)
        startRingbackTone()

        // Start timeout timer
        startTimeoutTimer()

        Log.d(TAG, "OutgoingCallActivity created for contact: $contactName")
    }

    /**
     * Configure window to keep screen on.
     */
    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Extract contact data from intent.
     */
    private fun extractIntentData() {
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE)
        callId = intent.getStringExtra(EXTRA_CALL_ID)

        Log.d(TAG, "Calling: $contactName (ID: $contactId)")
    }

    /**
     * Initialize UI components.
     */
    private fun initializeViews() {
        txtContactName = findViewById(R.id.txt_contact_name)
        txtContactPhone = findViewById(R.id.txt_contact_phone)
        txtCallStatus = findViewById(R.id.txt_call_status)
        btnHangUp = findViewById(R.id.btn_hang_up)

        // Request focus on hang up button for D-pad navigation
        btnHangUp.requestFocus()
    }

    /**
     * Update UI with contact information.
     */
    private fun updateUI() {
        txtContactName.text = contactName ?: "Unknown Contact"

        if (!contactPhone.isNullOrEmpty()) {
            txtContactPhone.text = contactPhone
            txtContactPhone.visibility = android.view.View.VISIBLE
        }

        txtCallStatus.text = "Ringing..."
    }

    /**
     * Setup button click listeners.
     */
    private fun setupButtonListeners() {
        btnHangUp.setOnClickListener {
            Log.d(TAG, "Hang up button clicked")
            cancelCall()
        }
    }

    /**
     * Observe CallViewModel state changes.
     */
    private fun observeViewModel() {
        // Observe call state
        callViewModel.callState.observe(this) { state ->
            Log.d(TAG, "Call state changed: $state")

            when (state) {
                is CallViewModel.CallState.Connected -> {
                    // Call answered - transition to InCallActivity
                    Log.i(TAG, "Call answered - transitioning to InCallActivity")

                    // Cancel timeout
                    timeoutHandler.removeCallbacksAndMessages(null)

                    // Stop ringback tone
                    stopRingbackTone()

                    val intent = InCallActivity.createIntent(
                        context = this,
                        contactId = state.remoteUserId,
                        contactName = state.remoteUserName,
                        callId = callId ?: "",
                        isIncoming = false
                    )
                    startActivity(intent)
                    finish()
                }
                is CallViewModel.CallState.Ended -> {
                    // Call ended or rejected
                    Log.i(TAG, "Call ended: ${state.reason}")
                    txtCallStatus.text = when (state.reason) {
                        "rejected" -> "Call rejected"
                        "user_hangup" -> "Call cancelled"
                        else -> "Call ended"
                    }
                    timeoutHandler.postDelayed({ finish() }, 2000)
                }
                is CallViewModel.CallState.Failed -> {
                    // Call failed
                    Log.e(TAG, "Call failed: ${state.error}")
                    txtCallStatus.text = "Call failed"
                    timeoutHandler.postDelayed({ finish() }, 2000)
                }
                else -> {
                    // Other states - just log
                    Log.d(TAG, "Unhandled state in OutgoingCallActivity: $state")
                }
            }
        }

        // Observe errors
        callViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Log.e(TAG, "Error: $it")
                txtCallStatus.text = "Error: $it"
            }
        }
    }

    /**
     * Cancel the outgoing call.
     */
    private fun cancelCall() {
        Log.i(TAG, "Canceling call to: $contactName")

        // Stop ringback tone
        stopRingbackTone()

        // Cancel timeout
        timeoutHandler.removeCallbacksAndMessages(null)

        // End call via ViewModel
        callViewModel.endCall()

        // Finish this activity
        finish()
    }

    /**
     * Start ringback tone (dial tone).
     */
    private fun startRingbackTone() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // TODO: Play ringback tone audio
            // For now, just log
            Log.d(TAG, "Ringback tone started (placeholder)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringback tone", e)
        }
    }

    /**
     * Stop ringback tone.
     */
    private fun stopRingbackTone() {
        try {
            // TODO: Stop ringback tone audio
            Log.d(TAG, "Ringback tone stopped (placeholder)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringback tone", e)
        }
    }

    /**
     * Start timeout timer.
     * If call not answered within timeout, end call.
     */
    private fun startTimeoutTimer() {
        timeoutHandler.postDelayed({
            Log.w(TAG, "Call timeout - no answer after ${callTimeoutMs / 1000} seconds")
            onCallTimeout()
        }, callTimeoutMs)
    }

    /**
     * Handle call timeout (no answer).
     */
    private fun onCallTimeout() {
        txtCallStatus.text = "No answer"

        // Stop ringback tone
        stopRingbackTone()

        // End call via ViewModel - this sends cancellation to callee
        callViewModel.endCall()

        // TODO: Log missed call

        // Auto-finish after 2 seconds
        timeoutHandler.postDelayed({
            finish()
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup
        stopRingbackTone()
        timeoutHandler.removeCallbacksAndMessages(null)

        Log.d(TAG, "OutgoingCallActivity destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back button cancels the call
        cancelCall()
    }
}
