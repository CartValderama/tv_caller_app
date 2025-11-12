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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.viewmodel.CallViewModel
import com.example.tv_caller_app.viewmodel.CallViewModelFactory

/**
 * InCallActivity - UI for active/ongoing calls.
 *
 * Features:
 * - Shows contact information
 * - Call timer display
 * - Mute/unmute microphone
 * - Speaker on/off toggle
 * - Hang up button
 * - D-pad navigation for TV
 *
 * Intent extras:
 * - EXTRA_CONTACT_ID: String - Contact user ID
 * - EXTRA_CONTACT_NAME: String - Contact display name
 * - EXTRA_CALL_ID: String - Unique call ID
 * - EXTRA_IS_INCOMING: Boolean - Whether this is an incoming call
 */
class InCallActivity : FragmentActivity() {

    private val TAG = "InCallActivity"

    // UI Components
    private lateinit var txtContactName: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var txtCallTimer: TextView
    private lateinit var txtMuteStatus: TextView
    private lateinit var txtSpeakerStatus: TextView
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnHangUp: Button

    // ViewModel
    private lateinit var callViewModel: CallViewModel

    // Call data
    private var contactId: String? = null
    private var contactName: String? = null
    private var callId: String? = null
    private var isIncoming: Boolean = false

    // Audio manager
    private lateinit var audioManager: AudioManager

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_IS_INCOMING = "is_incoming"

        /**
         * Create intent to launch InCallActivity.
         */
        fun createIntent(
            context: Context,
            contactId: String,
            contactName: String,
            callId: String,
            isIncoming: Boolean = false
        ): Intent {
            return Intent(context, InCallActivity::class.java).apply {
                putExtra(EXTRA_CONTACT_ID, contactId)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window
        setupWindow()

        setContentView(R.layout.activity_in_call)

        // Initialize CallViewModel
        val sessionManager = SessionManager.getInstance(this)
        val factory = CallViewModelFactory(applicationContext, sessionManager)
        callViewModel = ViewModelProvider(this, factory)[CallViewModel::class.java]

        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Extract intent data
        extractIntentData()

        // Initialize UI
        initializeViews()
        updateUI()

        // Setup button listeners
        setupButtonListeners()

        // Observe ViewModel state
        observeViewModel()

        // Configure audio mode
        configureAudioMode()

        Log.d(TAG, "InCallActivity created - call with: $contactName")
    }

    /**
     * Configure window to keep screen on.
     */
    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Extract call data from intent.
     */
    private fun extractIntentData() {
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        Log.d(TAG, "Call with: $contactName (ID: $contactId, Incoming: $isIncoming)")
    }

    /**
     * Initialize UI components.
     */
    private fun initializeViews() {
        txtContactName = findViewById(R.id.txt_contact_name)
        txtCallStatus = findViewById(R.id.txt_call_status)
        txtCallTimer = findViewById(R.id.txt_call_timer)
        txtMuteStatus = findViewById(R.id.txt_mute_status)
        txtSpeakerStatus = findViewById(R.id.txt_speaker_status)
        btnMute = findViewById(R.id.btn_mute)
        btnSpeaker = findViewById(R.id.btn_speaker)
        btnHangUp = findViewById(R.id.btn_hang_up)

        // Request focus on hang up button for D-pad navigation
        btnHangUp.requestFocus()
    }

    /**
     * Update UI with call information.
     */
    private fun updateUI() {
        txtContactName.text = contactName ?: "Unknown Contact"
        txtCallStatus.text = "Connected"
        txtCallTimer.text = "00:00"

        // Button states will be updated via ViewModel observers
    }

    /**
     * Setup button click listeners.
     */
    private fun setupButtonListeners() {
        btnMute.setOnClickListener {
            Log.d(TAG, "Mute button clicked")
            callViewModel.toggleMute()
        }

        btnSpeaker.setOnClickListener {
            Log.d(TAG, "Speaker button clicked")
            callViewModel.toggleSpeaker()
        }

        btnHangUp.setOnClickListener {
            Log.d(TAG, "Hang up button clicked")
            callViewModel.endCall()
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
                is CallViewModel.CallState.Ended -> {
                    // Call ended
                    Log.i(TAG, "Call ended: ${state.reason}")
                    txtCallStatus.text = "Call ended"
                    finish()
                }
                is CallViewModel.CallState.Failed -> {
                    // Call failed
                    Log.e(TAG, "Call failed: ${state.error}")
                    txtCallStatus.text = "Call failed"
                    finish()
                }
                else -> {
                    // Other states - just log
                    Log.d(TAG, "State in InCallActivity: $state")
                }
            }
        }

        // Observe call duration (timer)
        callViewModel.callDuration.observe(this) { duration ->
            val minutes = duration / 60
            val seconds = duration % 60
            txtCallTimer.text = String.format("%02d:%02d", minutes, seconds)
        }

        // Observe mute state
        callViewModel.isMuted.observe(this) { isMuted ->
            updateMuteButton(isMuted)
        }

        // Observe speaker state
        callViewModel.isSpeakerOn.observe(this) { isSpeakerOn ->
            updateSpeakerButton(isSpeakerOn)
        }

        // Observe errors
        callViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Log.e(TAG, "Error: $it")
                txtCallStatus.text = "Error: $it"
            }
        }

        // Observe microphone mode
        callViewModel.microphoneMode.observe(this) { mode ->
            Log.d(TAG, "Microphone mode: $mode")
            if (mode == com.example.tv_caller_app.calling.webrtc.WebRTCManager.MicrophoneMode.RECEIVE_ONLY) {
                // Disable mute button in receive-only mode
                btnMute.isEnabled = false
                btnMute.alpha = 0.5f
            }
        }
    }

    /**
     * Configure audio mode for call.
     */
    private fun configureAudioMode() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Audio mode set to MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio mode", e)
        }
    }

    /**
     * Update mute button appearance.
     */
    private fun updateMuteButton(isMuted: Boolean) {
        if (isMuted) {
            btnMute.text = "Unmute"
            btnMute.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
            txtMuteStatus.visibility = android.view.View.VISIBLE
        } else {
            btnMute.text = "Mute"
            btnMute.setBackgroundColor(getColor(android.R.color.darker_gray))
            txtMuteStatus.visibility = android.view.View.GONE
        }
    }

    /**
     * Update speaker button appearance.
     */
    private fun updateSpeakerButton(isSpeakerOn: Boolean) {
        if (isSpeakerOn) {
            btnSpeaker.text = "Speaker Off"
            btnSpeaker.setBackgroundColor(getColor(android.R.color.holo_blue_dark))
            txtSpeakerStatus.visibility = android.view.View.VISIBLE
        } else {
            btnSpeaker.text = "Speaker"
            btnSpeaker.setBackgroundColor(getColor(android.R.color.darker_gray))
            txtSpeakerStatus.visibility = android.view.View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Reset audio mode
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup audio", e)
        }

        Log.d(TAG, "InCallActivity destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back button ends the call
        callViewModel.endCall()
    }
}
