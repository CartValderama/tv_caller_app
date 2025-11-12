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
import com.example.tv_caller_app.R

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

    // Call data
    private var contactId: String? = null
    private var contactName: String? = null
    private var callId: String? = null
    private var isIncoming: Boolean = false

    // Call state
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false
    private var callStartTime: Long = 0
    private var callDurationSeconds: Int = 0

    // Timer handling
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateCallTimer()
            timerHandler.postDelayed(this, 1000) // Update every second
        }
    }

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

        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Extract intent data
        extractIntentData()

        // Initialize UI
        initializeViews()
        updateUI()

        // Setup button listeners
        setupButtonListeners()

        // Start call timer
        startCallTimer()

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

        // Update button states
        updateMuteButton()
        updateSpeakerButton()
    }

    /**
     * Setup button click listeners.
     */
    private fun setupButtonListeners() {
        btnMute.setOnClickListener {
            Log.d(TAG, "Mute button clicked")
            toggleMute()
        }

        btnSpeaker.setOnClickListener {
            Log.d(TAG, "Speaker button clicked")
            toggleSpeaker()
        }

        btnHangUp.setOnClickListener {
            Log.d(TAG, "Hang up button clicked")
            endCall()
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
     * Start call timer.
     */
    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)
        Log.d(TAG, "Call timer started")
    }

    /**
     * Update call timer display.
     */
    private fun updateCallTimer() {
        val elapsedMs = System.currentTimeMillis() - callStartTime
        callDurationSeconds = (elapsedMs / 1000).toInt()

        val minutes = callDurationSeconds / 60
        val seconds = callDurationSeconds % 60

        txtCallTimer.text = String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Toggle mute state.
     */
    private fun toggleMute() {
        isMuted = !isMuted
        updateMuteButton()

        // TODO: Notify WebRTCManager to mute/unmute audio track

        if (isMuted) {
            Log.i(TAG, "Microphone muted")
        } else {
            Log.i(TAG, "Microphone unmuted")
        }
    }

    /**
     * Update mute button appearance.
     */
    private fun updateMuteButton() {
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
     * Toggle speaker state.
     */
    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        updateSpeakerButton()

        try {
            audioManager.isSpeakerphoneOn = isSpeakerOn

            if (isSpeakerOn) {
                Log.i(TAG, "Speaker turned on")
            } else {
                Log.i(TAG, "Speaker turned off")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle speaker", e)
        }
    }

    /**
     * Update speaker button appearance.
     */
    private fun updateSpeakerButton() {
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

    /**
     * End the call and finish activity.
     */
    private fun endCall() {
        Log.i(TAG, "Ending call with: $contactName")

        // Update status
        txtCallStatus.text = "Call ended"

        // Stop timer
        timerHandler.removeCallbacks(timerRunnable)

        // TODO: Notify CallViewModel/CallService to end call
        // TODO: Save call to history

        // Reset audio mode
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset audio mode", e)
        }

        // Finish activity
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup
        timerHandler.removeCallbacks(timerRunnable)

        // Reset audio mode
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup audio", e)
        }

        Log.d(TAG, "InCallActivity destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back button ends the call
        endCall()
    }
}
