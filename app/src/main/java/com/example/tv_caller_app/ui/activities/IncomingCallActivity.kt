package com.example.tv_caller_app.ui.activities

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.tv_caller_app.R

/**
 * IncomingCallActivity - Full-screen UI for incoming calls.
 *
 * Features:
 * - Shows caller information
 * - Plays ringtone
 * - Wake lock to turn on screen
 * - D-pad navigation for TV
 * - Answer/Reject buttons
 *
 * Intent extras:
 * - EXTRA_CALLER_ID: String - Caller user ID
 * - EXTRA_CALLER_NAME: String - Caller display name
 * - EXTRA_CALLER_PHONE: String (optional) - Caller phone number
 * - EXTRA_CALL_ID: String - Unique call ID
 */
class IncomingCallActivity : FragmentActivity() {

    private val TAG = "IncomingCallActivity"

    // UI Components
    private lateinit var txtCallerName: TextView
    private lateinit var txtCallerPhone: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var btnAnswerCall: Button
    private lateinit var btnRejectCall: Button

    // Call data
    private var callerId: String? = null
    private var callerName: String? = null
    private var callerPhone: String? = null
    private var callId: String? = null

    // Ringtone and wake lock
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_PHONE = "caller_phone"
        const val EXTRA_CALL_ID = "call_id"

        /**
         * Create intent to launch IncomingCallActivity.
         */
        fun createIntent(
            context: Context,
            callerId: String,
            callerName: String,
            callId: String,
            callerPhone: String? = null
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_ID, callerId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_PHONE, callerPhone)
                putExtra(EXTRA_CALL_ID, callId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for incoming call
        setupWindow()

        setContentView(R.layout.activity_incoming_call)

        // Extract intent data
        extractIntentData()

        // Initialize UI
        initializeViews()
        updateUI()

        // Setup button listeners
        setupButtonListeners()

        // Start ringtone and wake lock
        startRingtone()
        acquireWakeLock()

        Log.d(TAG, "IncomingCallActivity created for caller: $callerName")
    }

    /**
     * Configure window to show over lock screen and turn on screen.
     */
    private fun setupWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    /**
     * Extract caller data from intent.
     */
    private fun extractIntentData() {
        callerId = intent.getStringExtra(EXTRA_CALLER_ID)
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        callerPhone = intent.getStringExtra(EXTRA_CALLER_PHONE)
        callId = intent.getStringExtra(EXTRA_CALL_ID)

        Log.d(TAG, "Incoming call from: $callerName (ID: $callerId)")
    }

    /**
     * Initialize UI components.
     */
    private fun initializeViews() {
        txtCallerName = findViewById(R.id.txt_caller_name)
        txtCallerPhone = findViewById(R.id.txt_caller_phone)
        txtCallStatus = findViewById(R.id.txt_call_status)
        btnAnswerCall = findViewById(R.id.btn_answer_call)
        btnRejectCall = findViewById(R.id.btn_reject_call)

        // Request focus on answer button for D-pad navigation
        btnAnswerCall.requestFocus()
    }

    /**
     * Update UI with caller information.
     */
    private fun updateUI() {
        txtCallerName.text = callerName ?: "Unknown Caller"

        if (!callerPhone.isNullOrEmpty()) {
            txtCallerPhone.text = callerPhone
            txtCallerPhone.visibility = android.view.View.VISIBLE
        }

        txtCallStatus.text = "Ringing..."
    }

    /**
     * Setup button click listeners.
     */
    private fun setupButtonListeners() {
        btnAnswerCall.setOnClickListener {
            Log.d(TAG, "Answer button clicked")
            answerCall()
        }

        btnRejectCall.setOnClickListener {
            Log.d(TAG, "Reject button clicked")
            rejectCall()
        }
    }

    /**
     * Answer the incoming call.
     */
    private fun answerCall() {
        Log.i(TAG, "Answering call from: $callerName")

        // Stop ringtone
        stopRingtone()

        // TODO: Notify CallViewModel/CallService to answer call
        // For now, just transition to InCallActivity

        // Start InCallActivity
        val intent = InCallActivity.createIntent(
            context = this,
            contactId = callerId ?: "",
            contactName = callerName ?: "Unknown",
            callId = callId ?: "",
            isIncoming = true
        )
        startActivity(intent)

        // Finish this activity
        finish()
    }

    /**
     * Reject the incoming call.
     */
    private fun rejectCall() {
        Log.i(TAG, "Rejecting call from: $callerName")

        // Stop ringtone
        stopRingtone()

        // TODO: Notify CallViewModel/CallService to reject call

        // Finish this activity
        finish()
    }

    /**
     * Start ringtone.
     */
    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "Ringtone started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    /**
     * Stop ringtone.
     */
    private fun stopRingtone() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
                Log.d(TAG, "Ringtone stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringtone", e)
        }
    }

    /**
     * Acquire wake lock to keep screen on.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "TVCaller:IncomingCallWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup
        stopRingtone()
        releaseWakeLock()

        Log.d(TAG, "IncomingCallActivity destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back button from dismissing incoming call
        // User must explicitly answer or reject
        Log.d(TAG, "Back button pressed - ignoring (must answer or reject)")
    }
}
