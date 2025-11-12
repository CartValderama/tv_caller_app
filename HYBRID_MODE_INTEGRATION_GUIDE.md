# Hybrid Mode Integration Guide

## Overview

The WebRTC implementation now supports **Hybrid Mode (Option C)**, which automatically adapts to TV hardware:

- ✅ **TWO_WAY Mode**: Full 2-way audio when microphone is available
- ✅ **RECEIVE_ONLY Mode**: Listen-only mode when no microphone is available
- ✅ **Dynamic Detection**: Automatically detects USB, Bluetooth, and built-in microphones
- ✅ **Graceful Fallback**: No crashes if microphone is missing

---

## Files Created

### 1. **AudioDeviceDetector.kt**
- Detects available microphones (built-in, USB, Bluetooth)
- Provides detailed microphone status
- Monitors device connection/disconnection

### 2. **AudioPermissionHelper.kt**
- Handles RECORD_AUDIO permission requests
- Provides user-friendly error messages
- Includes setup instructions for external microphones

### 3. **MicrophoneStatusHandler.kt**
- UI integration helper
- Shows dialogs and toasts for user feedback
- Provides setup help for USB/Bluetooth mics

### 4. **WebRTCManager.kt** (Modified)
- Now includes microphone detection
- Creates audio tracks only when microphone is available
- Falls back to receive-only mode gracefully
- Provides `onMicrophoneModeChanged` callback

### 5. **AndroidManifest.xml** (Updated)
- Added RECORD_AUDIO permission
- Added MODIFY_AUDIO_SETTINGS permission
- Added Bluetooth permissions (Android 12+)
- Marked microphone, Bluetooth, and USB as optional features

---

## Integration Example

### In Your Activity/Fragment:

```kotlin
import com.example.tv_caller_app.calling.webrtc.WebRTCManager
import com.example.tv_caller_app.calling.audio.MicrophoneStatusHandler
import com.example.tv_caller_app.calling.audio.AudioPermissionHelper

class CallActivity : AppCompatActivity() {

    private lateinit var webrtcManager: WebRTCManager
    private lateinit var micStatusHandler: MicrophoneStatusHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Initialize managers
        webrtcManager = WebRTCManager(applicationContext)
        micStatusHandler = MicrophoneStatusHandler(this)

        // Setup microphone mode change callback
        webrtcManager.onMicrophoneModeChanged = { mode, message ->
            runOnUiThread {
                micStatusHandler.handleModeChange(mode, message)
                updateUIForMode(mode)
            }
        }

        // Check permission and initialize
        initializeWithPermissionCheck()
    }

    private fun initializeWithPermissionCheck() {
        micStatusHandler.checkAndRequestPermission(
            onGranted = {
                // Permission granted, initialize WebRTC
                webrtcManager.initialize()

                // Show current status
                val status = webrtcManager.getMicrophoneStatus()
                if (!status.isAvailable) {
                    micStatusHandler.showMicrophoneStatusDialog(status)
                }
            },
            onDenied = {
                // Permission denied, initialize in receive-only mode
                webrtcManager.initialize()
                Toast.makeText(this, "Running in receive-only mode", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun updateUIForMode(mode: WebRTCManager.MicrophoneMode) {
        when (mode) {
            WebRTCManager.MicrophoneMode.TWO_WAY -> {
                // Enable mute button
                muteButton.isEnabled = true
                micStatusIcon.text = "🎤"
                micStatusText.text = "2-Way Audio"
            }
            WebRTCManager.MicrophoneMode.RECEIVE_ONLY -> {
                // Disable mute button
                muteButton.isEnabled = false
                micStatusIcon.text = "🔇"
                micStatusText.text = "Receive Only"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        micStatusHandler.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onGranted = {
                webrtcManager.initialize()
            },
            onDenied = {
                webrtcManager.initialize()
            }
        )
    }

    // When user connects external microphone
    fun onMicrophoneConnected() {
        // Re-check microphone availability
        val newMode = webrtcManager.recheckMicrophoneAvailability()

        if (newMode == WebRTCManager.MicrophoneMode.TWO_WAY) {
            Toast.makeText(this, "Microphone detected! 2-way audio enabled", Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## Testing Scenarios

### Scenario 1: TV with No Microphone (Most Common)
**Expected Behavior:**
1. App starts in RECEIVE_ONLY mode
2. User sees message: "No microphone detected"
3. User can still receive calls and hear audio
4. Mute button is disabled
5. Instructions shown for connecting external mic

**Test:**
```kotlin
val status = webrtcManager.getMicrophoneStatus()
assertEquals(false, status.isAvailable)
assertEquals(MicrophoneMode.RECEIVE_ONLY, webrtcManager.getMicrophoneMode())
```

### Scenario 2: TV with USB Microphone
**Expected Behavior:**
1. User plugs in USB microphone
2. App detects it automatically (may need to call `recheckMicrophoneAvailability()`)
3. Mode switches to TWO_WAY
4. User sees message: "USB microphone connected and ready"
5. Mute button is enabled

**Test:**
```kotlin
// After USB mic is connected
val status = webrtcManager.getMicrophoneStatus()
assertEquals(true, status.isAvailable)
assertEquals(MicrophoneType.USB, status.deviceType)
assertEquals(MicrophoneMode.TWO_WAY, webrtcManager.getMicrophoneMode())
```

### Scenario 3: TV with Bluetooth Headset
**Expected Behavior:**
1. User pairs Bluetooth headset
2. App detects it
3. Mode switches to TWO_WAY
4. User sees message: "Bluetooth microphone connected and ready"

**Test:**
```kotlin
val status = webrtcManager.getMicrophoneStatus()
assertEquals(MicrophoneType.BLUETOOTH, status.deviceType)
```

### Scenario 4: Permission Denied
**Expected Behavior:**
1. User denies RECORD_AUDIO permission
2. App continues in RECEIVE_ONLY mode
3. User sees message about limited functionality
4. Option to grant permission later in settings

**Test:**
```kotlin
// When permission is denied
assertEquals(false, status.hasPermission)
assertEquals(MicrophoneMode.RECEIVE_ONLY, webrtcManager.getMicrophoneMode())
```

---

## UI Recommendations

### 1. **Microphone Status Indicator**
Show current mode prominently:

```xml
<TextView
    android:id="@+id/micStatusIcon"
    android:text="🎤"
    android:textSize="24sp" />

<TextView
    android:id="@+id/micStatusText"
    android:text="2-Way Audio" />
```

### 2. **Mute Button State**
Disable mute button in RECEIVE_ONLY mode:

```kotlin
muteButton.isEnabled = (mode == MicrophoneMode.TWO_WAY)
```

### 3. **Help Button**
Provide easy access to microphone setup:

```kotlin
helpButton.setOnClickListener {
    micStatusHandler.showMicrophoneSetupHelpDialog()
}
```

### 4. **Status Messages**
Show clear feedback:
- "Microphone ready - 2-way calling enabled" (green)
- "No microphone - Receive-only mode" (yellow/warning)
- "Connect USB or Bluetooth mic for 2-way calling" (info)

---

## API Reference

### WebRTCManager

#### Methods:
```kotlin
// Initialize WebRTC (auto-detects microphone)
fun initialize()

// Re-check microphone availability
fun recheckMicrophoneAvailability(): MicrophoneMode

// Get current mode
fun getMicrophoneMode(): MicrophoneMode

// Get detailed status
fun getMicrophoneStatus(): AudioDeviceDetector.MicrophoneStatus

// Mute/unmute (only works in TWO_WAY mode)
fun mute()
fun unmute()
fun isMuted(): Boolean
```

#### Callbacks:
```kotlin
// Called when microphone mode changes
webrtcManager.onMicrophoneModeChanged = { mode, message ->
    // Handle mode change
}
```

### AudioDeviceDetector

```kotlin
// Check microphone availability
fun checkMicrophoneAvailability(): MicrophoneStatus

// Check specific device types
fun isBluetoothMicConnected(): Boolean
fun isUSBMicConnected(): Boolean

// Get list of available mics
fun getAvailableMicrophones(): List<String>

// Register for device connection callbacks
fun registerAudioDeviceCallback(callback: AudioDeviceCallback)
```

### MicrophoneStatusHandler

```kotlin
// Check and request permission
fun checkAndRequestPermission(onGranted: () -> Unit, onDenied: () -> Unit)

// Handle permission result
fun handlePermissionResult(requestCode, permissions, grantResults, onGranted, onDenied)

// Handle mode changes with UI feedback
fun handleModeChange(mode: MicrophoneMode, message: String)

// Show status dialog
fun showMicrophoneStatusDialog(micStatus: MicrophoneStatus)

// Show setup help
fun showMicrophoneSetupHelpDialog()
```

---

## Common Issues & Solutions

### Issue 1: Microphone not detected after connecting USB
**Solution:** Call `webrtcManager.recheckMicrophoneAvailability()` after device connection.

### Issue 2: Permission permanently denied
**Solution:** Guide user to app settings using `AudioPermissionHelper.getPermanentlyDeniedMessage()`

### Issue 3: Mute button not working
**Solution:** Check if mode is TWO_WAY before attempting to mute:
```kotlin
if (webrtcManager.getMicrophoneMode() == MicrophoneMode.TWO_WAY) {
    webrtcManager.mute()
}
```

### Issue 4: Bluetooth mic connects but not detected
**Solution:** Ensure Bluetooth permissions are granted (Android 12+):
```kotlin
if (AudioPermissionHelper.needsBluetoothPermission()) {
    // Request Bluetooth permissions
}
```

---

## Next Steps

1. ✅ Build the project to ensure all files compile
2. ✅ Test on TV emulator without microphone (should default to RECEIVE_ONLY)
3. ✅ Test with USB microphone connected
4. ✅ Test with Bluetooth headset paired
5. ✅ Test permission denial flow
6. ✅ Test microphone hotswap (connect/disconnect during call)

---

## Summary

The hybrid mode implementation provides:

- **Flexibility**: Works on TVs with or without microphones
- **User-Friendly**: Clear feedback and setup instructions
- **Robust**: No crashes, graceful fallbacks
- **Future-Proof**: Ready for video calls (Phase 2)
- **Hardware Support**: USB, Bluetooth, and built-in mics

The TV can now both initiate and receive calls, adapting automatically to available hardware!
