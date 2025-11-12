package com.example.tv_caller_app.calling.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Detects available audio input devices (microphones).
 * Handles TV scenarios where built-in mic may not exist.
 *
 * Supports:
 * - Built-in microphone (rare on TVs)
 * - USB microphones
 * - Bluetooth headsets/microphones
 */
class AudioDeviceDetector(private val context: Context) {

    private val TAG = "AudioDeviceDetector"
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Microphone status result.
     */
    data class MicrophoneStatus(
        val isAvailable: Boolean,
        val hasPermission: Boolean,
        val deviceType: MicrophoneType,
        val deviceName: String?,
        val message: String
    )

    /**
     * Types of microphone devices.
     */
    enum class MicrophoneType {
        NONE,           // No microphone detected
        BUILT_IN,       // Built-in device microphone
        USB,            // USB microphone
        BLUETOOTH,      // Bluetooth headset/mic
        WIRED_HEADSET   // Wired headset with mic
    }

    /**
     * Check if microphone is available and ready to use.
     *
     * @return MicrophoneStatus with detailed information
     */
    fun checkMicrophoneAvailability(): MicrophoneStatus {
        // First check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return MicrophoneStatus(
                isAvailable = false,
                hasPermission = false,
                deviceType = MicrophoneType.NONE,
                deviceName = null,
                message = "Microphone permission required. Please grant permission in settings."
            )
        }

        // Check for audio input devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            // Priority order: Bluetooth > USB > Wired Headset > Built-in
            val bluetoothMic = devices.firstOrNull { isBluetoothMic(it) }
            if (bluetoothMic != null) {
                Log.i(TAG, "Bluetooth microphone detected: ${bluetoothMic.productName}")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.BLUETOOTH,
                    deviceName = bluetoothMic.productName?.toString() ?: "Bluetooth Microphone",
                    message = "Bluetooth microphone connected and ready"
                )
            }

            val usbMic = devices.firstOrNull { isUSBMic(it) }
            if (usbMic != null) {
                Log.i(TAG, "USB microphone detected: ${usbMic.productName}")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.USB,
                    deviceName = usbMic.productName?.toString() ?: "USB Microphone",
                    message = "USB microphone connected and ready"
                )
            }

            val wiredHeadset = devices.firstOrNull { isWiredHeadsetMic(it) }
            if (wiredHeadset != null) {
                Log.i(TAG, "Wired headset microphone detected")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.WIRED_HEADSET,
                    deviceName = "Wired Headset",
                    message = "Wired headset microphone connected and ready"
                )
            }

            val builtInMic = devices.firstOrNull { isBuiltInMic(it) }
            if (builtInMic != null) {
                Log.i(TAG, "Built-in microphone detected")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.BUILT_IN,
                    deviceName = "Built-in Microphone",
                    message = "Built-in microphone ready"
                )
            }

            // No microphone found
            Log.w(TAG, "No microphone detected on device")
            return MicrophoneStatus(
                isAvailable = false,
                hasPermission = true,
                deviceType = MicrophoneType.NONE,
                deviceName = null,
                message = "No microphone detected. Please connect a USB or Bluetooth microphone to make calls."
            )
        } else {
            // Fallback for older Android versions - assume mic exists if permission granted
            Log.i(TAG, "Running on Android < M, assuming microphone available")
            return MicrophoneStatus(
                isAvailable = true,
                hasPermission = true,
                deviceType = MicrophoneType.BUILT_IN,
                deviceName = "Microphone",
                message = "Microphone ready"
            )
        }
    }

    /**
     * Check if Bluetooth microphone is connected.
     */
    fun isBluetoothMicConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any { isBluetoothMic(it) }
        }
        return false
    }

    /**
     * Check if USB microphone is connected.
     */
    fun isUSBMicConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any { isUSBMic(it) }
        }
        return false
    }

    /**
     * Get list of all available microphone devices.
     */
    fun getAvailableMicrophones(): List<String> {
        val micList = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            devices.forEach { device ->
                if (device.isSink) return@forEach // Skip output devices

                val deviceName = when {
                    isBluetoothMic(device) -> "Bluetooth: ${device.productName}"
                    isUSBMic(device) -> "USB: ${device.productName}"
                    isWiredHeadsetMic(device) -> "Wired Headset"
                    isBuiltInMic(device) -> "Built-in Microphone"
                    else -> "Unknown: ${device.productName} (type=${device.type})"
                }

                micList.add(deviceName)
            }
        }

        return micList
    }

    /**
     * Register callback for audio device connections/disconnections.
     *
     * NOTE: This feature is currently disabled due to API compatibility issues.
     * For now, use recheckMicrophoneAvailability() manually to detect new devices.
     *
     * TODO: Re-enable in future version with proper API 23+ handling
     */
    fun registerAudioDeviceCallback(callback: MicrophoneDeviceCallback) {
        Log.w(TAG, "Audio device callback feature is currently disabled")
        Log.i(TAG, "Use recheckMicrophoneAvailability() to manually check for new devices")

        // Feature temporarily disabled - see TODO above
        // Will be re-enabled in a future update
    }

    /**
     * Check if device is Bluetooth microphone.
     */
    private fun isBluetoothMic(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                   device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        return false
    }

    /**
     * Check if device is USB microphone.
     */
    private fun isUSBMic(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                   device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                   device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        return false
    }

    /**
     * Check if device is wired headset microphone.
     */
    private fun isWiredHeadsetMic(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        return false
    }

    /**
     * Check if device is built-in microphone.
     */
    private fun isBuiltInMic(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        }
        return false
    }

    /**
     * Callback interface for audio device changes.
     */
    interface MicrophoneDeviceCallback {
        fun onMicrophoneConnected(type: MicrophoneType, deviceName: String?)
        fun onMicrophoneDisconnected(type: MicrophoneType)
    }
}
