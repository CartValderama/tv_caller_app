package com.example.tv_caller_app.calling.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Helper for managing audio recording permissions.
 * Handles permission checks, requests, and provides user-friendly messaging.
 */
object AudioPermissionHelper {

    private const val TAG = "AudioPermissionHelper"
    const val REQUEST_CODE_AUDIO_PERMISSION = 1001

    /**
     * Check if audio recording permission is granted.
     *
     * @param context Application context
     * @return true if permission granted, false otherwise
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if we should show rationale for audio permission.
     *
     * @param activity Current activity
     * @return true if should show rationale
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
    }

    /**
     * Request audio recording permission from Activity.
     *
     * @param activity Current activity
     */
    fun requestAudioPermission(activity: Activity) {
        Log.i(TAG, "Requesting RECORD_AUDIO permission")

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_AUDIO_PERMISSION
        )
    }

    /**
     * Request audio recording permission from Fragment.
     *
     * NOTE: For modern permission handling, use ActivityResultContracts.RequestPermission()
     * This is a helper method for legacy code only.
     *
     * Modern usage example:
     * ```
     * val requestPermissionLauncher = registerForActivityResult(
     *     ActivityResultContracts.RequestPermission()
     * ) { isGranted ->
     *     if (isGranted) {
     *         // Permission granted
     *     } else {
     *         // Permission denied
     *     }
     * }
     * requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
     * ```
     *
     * @param fragment Current fragment
     */
    @Deprecated(
        message = "Use ActivityResultContracts.RequestPermission() instead",
        replaceWith = ReplaceWith(
            "registerForActivityResult(ActivityResultContracts.RequestPermission())",
            "androidx.activity.result.contract.ActivityResultContracts"
        )
    )
    fun requestAudioPermission(fragment: Fragment) {
        Log.i(TAG, "Requesting RECORD_AUDIO permission from fragment (legacy method)")
        Log.w(TAG, "Consider using ActivityResultContracts.RequestPermission() for modern permission handling")

        // Note: This method is kept for backwards compatibility only
        // The deprecation is from Android framework, not our code
        requestAudioPermission(fragment.requireActivity())
    }

    /**
     * Handle permission request result.
     *
     * @param requestCode Request code from permission callback
     * @param permissions Requested permissions
     * @param grantResults Grant results
     * @param onGranted Callback when permission granted
     * @param onDenied Callback when permission denied
     * @param onPermanentlyDenied Callback when permission permanently denied
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: (() -> Unit)? = null
    ) {
        if (requestCode != REQUEST_CODE_AUDIO_PERMISSION) {
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Audio permission granted")
            onGranted()
        } else {
            Log.w(TAG, "Audio permission denied")

            // Check if permanently denied (user selected "Don't ask again")
            if (permissions.isNotEmpty() && permissions[0] == Manifest.permission.RECORD_AUDIO) {
                // If we can't show rationale, it's permanently denied
                // Note: This check needs to be done from Activity context
                if (onPermanentlyDenied != null) {
                    // Assume temporarily denied, caller should check shouldShowRationale
                    onDenied()
                } else {
                    onDenied()
                }
            }
        }
    }

    /**
     * Get user-friendly permission explanation message.
     *
     * @param showExtendedHelp Include extended help for external microphones
     * @return Permission explanation string
     */
    fun getPermissionExplanation(showExtendedHelp: Boolean = false): String {
        return if (showExtendedHelp) {
            """
            To make voice calls, this app needs permission to access your microphone.

            TV Setup:
            • If your TV doesn't have a built-in microphone, you can:
              - Connect a USB microphone
              - Connect a Bluetooth headset/microphone
              - Use a wired headset with microphone

            • Once connected, grant microphone permission to enable 2-way calling

            Without a microphone:
            • You can still receive calls
            • You'll be able to hear the caller
            • But they won't be able to hear you
            """.trimIndent()
        } else {
            "Microphone permission is required to make voice calls. Without it, you can only receive calls (listen-only mode)."
        }
    }

    /**
     * Get message for when permission is permanently denied.
     *
     * @return Instructions for enabling permission manually
     */
    fun getPermanentlyDeniedMessage(): String {
        return """
            Microphone permission was permanently denied.

            To enable voice calls:
            1. Open your TV's Settings
            2. Go to Apps
            3. Find "TV Caller App"
            4. Go to Permissions
            5. Enable "Microphone" permission

            Without microphone permission, you can only receive calls in listen-only mode.
        """.trimIndent()
    }

    /**
     * Get message for when no microphone hardware is detected.
     *
     * @return Instructions for connecting external microphone
     */
    fun getNoMicrophoneHardwareMessage(): String {
        return """
            No microphone detected on your TV.

            To enable 2-way calling, connect one of:
            • USB Microphone (plug into USB port)
            • Bluetooth Headset (pair via Bluetooth settings)
            • Wired Headset with mic (plug into audio jack)

            Current Mode: Receive-only
            • You can receive calls and hear the caller
            • The caller cannot hear you

            Once you connect a microphone, return to this screen to enable 2-way calling.
        """.trimIndent()
    }

    /**
     * Get Bluetooth pairing instructions.
     *
     * @return Instructions for pairing Bluetooth microphone
     */
    fun getBluetoothPairingInstructions(): String {
        return """
            Pairing a Bluetooth Microphone:

            1. Turn on your Bluetooth microphone/headset
            2. Put it in pairing mode (usually hold power button)
            3. On your TV:
               - Open Settings
               - Go to Bluetooth
               - Select "Pair new device"
               - Choose your microphone from the list
            4. Once paired, return to TV Caller app
            5. The app will automatically detect it

            Recommended devices:
            • Bluetooth headsets with built-in mic
            • Bluetooth speakerphones
            • Any Bluetooth audio device with microphone
        """.trimIndent()
    }

    /**
     * Get USB microphone setup instructions.
     *
     * @return Instructions for setting up USB microphone
     */
    fun getUSBMicrophoneInstructions(): String {
        return """
            Setting up a USB Microphone:

            1. Plug the USB microphone into any USB port on your TV
            2. Wait a few seconds for detection
            3. The TV Caller app will automatically recognize it
            4. No additional setup required!

            Compatible devices:
            • USB microphones (any standard USB mic)
            • USB headsets with microphone
            • USB webcams with built-in microphones

            Note: Most Android TVs support USB audio devices automatically.
        """.trimIndent()
    }

    /**
     * Check if Bluetooth permission is needed (Android 12+).
     *
     * @return true if Bluetooth permission is needed
     */
    fun needsBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Get list of Bluetooth permissions needed for Android 12+.
     *
     * @return Array of Bluetooth permissions
     */
    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    /**
     * Check if all Bluetooth permissions are granted (Android 12+).
     *
     * @param context Application context
     * @return true if all Bluetooth permissions granted
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true // Not needed on older Android versions
    }
}
