package com.example.tv_caller_app.calling.audio

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.tv_caller_app.calling.webrtc.WebRTCManager

/**
 * Handles microphone status UI updates and user interactions.
 * Provides user-friendly feedback about microphone availability and mode changes.
 *
 * Example Usage in Activity/Fragment:
 *
 * ```kotlin
 * val micHandler = MicrophoneStatusHandler(this)
 *
 * // Setup WebRTC with status callbacks
 * webrtcManager.onMicrophoneModeChanged = { mode, message ->
 *     micHandler.handleModeChange(mode, message)
 * }
 *
 * // Check and request permission before initializing
 * micHandler.checkAndRequestPermission(
 *     onGranted = { webrtcManager.initialize() },
 *     onDenied = { /* Handle denied */ }
 * )
 * ```
 */
class MicrophoneStatusHandler(private val activity: Activity) {

    private val TAG = "MicrophoneStatusHandler"
    private val context: Context = activity.applicationContext

    /**
     * Check microphone permission and request if needed.
     *
     * @param onGranted Callback when permission is granted
     * @param onDenied Callback when permission is denied
     * @param showRationale Whether to show explanation dialog
     */
    fun checkAndRequestPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        showRationale: Boolean = true
    ) {
        if (AudioPermissionHelper.hasAudioPermission(context)) {
            Log.d(TAG, "Audio permission already granted")
            onGranted()
            return
        }

        // Show rationale if needed
        if (showRationale && AudioPermissionHelper.shouldShowRationale(activity)) {
            showPermissionRationaleDialog(
                onAccept = {
                    AudioPermissionHelper.requestAudioPermission(activity)
                },
                onDecline = onDenied
            )
        } else {
            // Request permission directly
            AudioPermissionHelper.requestAudioPermission(activity)
        }
    }

    /**
     * Handle permission request result.
     * Call this from Activity's onRequestPermissionsResult().
     *
     * @param requestCode Request code
     * @param permissions Permissions array
     * @param grantResults Grant results array
     * @param onGranted Callback when granted
     * @param onDenied Callback when denied
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        AudioPermissionHelper.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onGranted = {
                showToast("Microphone permission granted")
                onGranted()
            },
            onDenied = {
                if (AudioPermissionHelper.shouldShowRationale(activity)) {
                    showToast("Microphone permission denied. App will run in receive-only mode.")
                    onDenied()
                } else {
                    // Permanently denied
                    showPermanentlyDeniedDialog()
                    onDenied()
                }
            }
        )
    }

    /**
     * Handle microphone mode changes.
     * Shows appropriate UI feedback.
     *
     * @param mode New microphone mode
     * @param message Status message
     */
    fun handleModeChange(mode: WebRTCManager.MicrophoneMode, message: String) {
        Log.i(TAG, "Microphone mode: $mode - $message")

        when (mode) {
            WebRTCManager.MicrophoneMode.TWO_WAY -> {
                showToast("✓ Microphone ready - 2-way calling enabled")
            }
            WebRTCManager.MicrophoneMode.RECEIVE_ONLY -> {
                showToast("⚠ No microphone - Receive-only mode")
            }
        }
    }

    /**
     * Show microphone status details dialog.
     * Useful for showing current setup and instructions.
     *
     * @param micStatus Current microphone status
     */
    fun showMicrophoneStatusDialog(micStatus: AudioDeviceDetector.MicrophoneStatus) {
        val title = if (micStatus.isAvailable) {
            "Microphone Ready"
        } else {
            "No Microphone Detected"
        }

        val message = buildString {
            appendLine(micStatus.message)
            appendLine()

            if (micStatus.isAvailable) {
                appendLine("Device: ${micStatus.deviceName}")
                appendLine("Type: ${micStatus.deviceType}")
                appendLine()
                appendLine("You can make and receive calls.")
            } else {
                appendLine(AudioPermissionHelper.getNoMicrophoneHardwareMessage())
            }
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .apply {
                if (!micStatus.isAvailable) {
                    setNeutralButton("Setup Help") { _, _ ->
                        showMicrophoneSetupHelpDialog()
                    }
                }
            }
            .show()
    }

    /**
     * Show microphone setup help dialog.
     * Provides instructions for connecting external microphones.
     */
    fun showMicrophoneSetupHelpDialog() {
        val options = arrayOf(
            "USB Microphone Setup",
            "Bluetooth Microphone Setup",
            "Permission Settings"
        )

        AlertDialog.Builder(activity)
            .setTitle("Microphone Setup Help")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUSBMicrophoneHelp()
                    1 -> showBluetoothMicrophoneHelp()
                    2 -> showPermissionHelp()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show USB microphone setup instructions.
     */
    private fun showUSBMicrophoneHelp() {
        AlertDialog.Builder(activity)
            .setTitle("USB Microphone Setup")
            .setMessage(AudioPermissionHelper.getUSBMicrophoneInstructions())
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show Bluetooth microphone setup instructions.
     */
    private fun showBluetoothMicrophoneHelp() {
        AlertDialog.Builder(activity)
            .setTitle("Bluetooth Microphone Setup")
            .setMessage(AudioPermissionHelper.getBluetoothPairingInstructions())
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show permission help and settings.
     */
    private fun showPermissionHelp() {
        AlertDialog.Builder(activity)
            .setTitle("Microphone Permission")
            .setMessage(AudioPermissionHelper.getPermissionExplanation(showExtendedHelp = true))
            .setPositiveButton("Request Permission") { _, _ ->
                AudioPermissionHelper.requestAudioPermission(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show permission rationale dialog.
     */
    private fun showPermissionRationaleDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Microphone Permission Needed")
            .setMessage(AudioPermissionHelper.getPermissionExplanation(showExtendedHelp = true))
            .setPositiveButton("Grant Permission") { _, _ -> onAccept() }
            .setNegativeButton("Not Now") { _, _ -> onDecline() }
            .setCancelable(false)
            .show()
    }

    /**
     * Show permanently denied permission dialog.
     */
    private fun showPermanentlyDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(AudioPermissionHelper.getPermanentlyDeniedMessage())
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    /**
     * Show toast message.
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Get user-friendly mode description.
     */
    fun getModeDescription(mode: WebRTCManager.MicrophoneMode): String {
        return when (mode) {
            WebRTCManager.MicrophoneMode.TWO_WAY ->
                "2-Way Audio: You can speak and hear"

            WebRTCManager.MicrophoneMode.RECEIVE_ONLY ->
                "Receive Only: You can hear but not speak"
        }
    }

    /**
     * Get mode icon/emoji for UI display.
     */
    fun getModeIcon(mode: WebRTCManager.MicrophoneMode): String {
        return when (mode) {
            WebRTCManager.MicrophoneMode.TWO_WAY -> "🎤"
            WebRTCManager.MicrophoneMode.RECEIVE_ONLY -> "🔇"
        }
    }

    /**
     * Check microphone and show status if there are issues.
     *
     * @param audioDeviceDetector Audio device detector instance
     * @return true if microphone is ready, false if there are issues
     */
    fun checkAndShowStatusIfNeeded(audioDeviceDetector: AudioDeviceDetector): Boolean {
        val status = audioDeviceDetector.checkMicrophoneAvailability()

        if (!status.hasPermission) {
            showToast("Microphone permission required")
            return false
        }

        if (!status.isAvailable) {
            showToast("No microphone detected. Connect USB or Bluetooth mic.")
            return false
        }

        return true
    }
}
