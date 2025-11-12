package com.example.tv_caller_app.calling.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.tv_caller_app.calling.audio.AudioDeviceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * WebRTC Manager - Core peer connection manager.
 * Handles all WebRTC operations for voice calling.
 *
 * This is the most technically challenging part of the implementation.
 * It manages:
 * - Peer connection lifecycle
 * - Audio track creation and management (with microphone detection)
 * - SDP offer/answer creation
 * - ICE candidate handling
 * - Connection state management
 *
 * Architecture: Video-ready but Phase 1 is audio only.
 *
 * HYBRID MODE (Option C):
 * - Detects microphone availability (built-in, USB, Bluetooth)
 * - Enables 2-way audio if microphone is present
 * - Falls back to receive-only mode if no microphone
 * - Supports TV scenarios with external microphones
 */
class WebRTCManager(private val context: Context) {

    private val TAG = "WebRTCManager"

    // WebRTC components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Audio management
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Microphone detection
    private val audioDeviceDetector = AudioDeviceDetector(context)

    // State
    private var isInitialized = false
    private var isMuted = false
    private var isSpeakerOn = false

    // Microphone mode
    enum class MicrophoneMode {
        TWO_WAY,        // Microphone available, can send and receive audio
        RECEIVE_ONLY    // No microphone, can only receive audio
    }

    private var currentMicrophoneMode = MicrophoneMode.RECEIVE_ONLY

    // Connection state
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        object Failed : ConnectionState()
        object Closed : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Callbacks for signaling
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onConnectionStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onRemoteStream: ((MediaStream) -> Unit)? = null

    // Callbacks for microphone status
    var onMicrophoneModeChanged: ((MicrophoneMode, String) -> Unit)? = null

    /**
     * Initialize WebRTC.
     * Must be called once before any other operations.
     * Detects microphone availability and sets mode accordingly.
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "WebRTC already initialized")
            return
        }

        Log.i(TAG, "Initializing WebRTC...")

        try {
            // Check microphone availability first
            val micStatus = audioDeviceDetector.checkMicrophoneAvailability()
            currentMicrophoneMode = if (micStatus.isAvailable && micStatus.hasPermission) {
                Log.i(TAG, "Microphone available: ${micStatus.deviceName} (${micStatus.deviceType})")
                MicrophoneMode.TWO_WAY
            } else {
                Log.w(TAG, "Microphone not available: ${micStatus.message}")
                MicrophoneMode.RECEIVE_ONLY
            }

            // Notify callback
            onMicrophoneModeChanged?.invoke(currentMicrophoneMode, micStatus.message)

            // Initialize PeerConnectionFactory
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions()

            PeerConnectionFactory.initialize(initOptions)

            // Build PeerConnectionFactory
            val options = PeerConnectionFactory.Options()

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()

            isInitialized = true
            Log.i(TAG, "WebRTC initialized successfully in ${currentMicrophoneMode} mode")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
            throw e
        }
    }

    /**
     * Re-check microphone availability and update mode.
     * Call this when user connects external microphone.
     *
     * @return Updated MicrophoneMode
     */
    fun recheckMicrophoneAvailability(): MicrophoneMode {
        val micStatus = audioDeviceDetector.checkMicrophoneAvailability()
        val newMode = if (micStatus.isAvailable && micStatus.hasPermission) {
            MicrophoneMode.TWO_WAY
        } else {
            MicrophoneMode.RECEIVE_ONLY
        }

        if (newMode != currentMicrophoneMode) {
            Log.i(TAG, "Microphone mode changed: $currentMicrophoneMode -> $newMode")
            currentMicrophoneMode = newMode
            onMicrophoneModeChanged?.invoke(currentMicrophoneMode, micStatus.message)
        }

        return currentMicrophoneMode
    }

    /**
     * Get current microphone mode.
     */
    fun getMicrophoneMode(): MicrophoneMode = currentMicrophoneMode

    /**
     * Get current microphone status details.
     */
    fun getMicrophoneStatus(): AudioDeviceDetector.MicrophoneStatus {
        return audioDeviceDetector.checkMicrophoneAvailability()
    }

    /**
     * Create local audio track with constraints.
     * Returns null if in RECEIVE_ONLY mode (no microphone available).
     */
    private fun createAudioTrack(): AudioTrack? {
        // Check if we're in receive-only mode
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Skipping audio track creation - RECEIVE_ONLY mode (no microphone)")
            return null
        }

        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            }

            audioSource = peerConnectionFactory?.createAudioSource(constraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack(
                WebRTCConfig.StreamLabels.AUDIO_TRACK_ID,
                audioSource
            )

            Log.d(TAG, "Audio track created successfully")
            return localAudioTrack

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio track", e)
            // Downgrade to RECEIVE_ONLY mode on failure
            currentMicrophoneMode = MicrophoneMode.RECEIVE_ONLY
            onMicrophoneModeChanged?.invoke(
                currentMicrophoneMode,
                "Failed to access microphone. Switched to receive-only mode."
            )
            return null
        }
    }

    /**
     * Create peer connection with observers.
     */
    private fun createPeerConnection(): PeerConnection? {
        try {
            val rtcConfig = WebRTCConfig.createRTCConfiguration()

            val observer = PeerConnectionObserver(
                onIceCandidate = { candidate ->
                    Log.d(TAG, "ICE candidate generated: ${candidate.sdp}")
                    onIceCandidate?.invoke(candidate)
                },
                onConnectionChange = { state ->
                    Log.i(TAG, "ICE connection state: $state")

                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _connectionState.value = ConnectionState.Connected
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _connectionState.value = ConnectionState.Failed
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            _connectionState.value = ConnectionState.Closed
                        }
                        else -> {
                            _connectionState.value = ConnectionState.Connecting
                        }
                    }

                    onConnectionStateChange?.invoke(state)
                },
                onAddStream = { stream ->
                    Log.i(TAG, "Remote stream added")

                    // Extract remote audio track
                    if (stream.audioTracks.isNotEmpty()) {
                        remoteAudioTrack = stream.audioTracks[0]
                        remoteAudioTrack?.setEnabled(true)
                        Log.d(TAG, "Remote audio track enabled")
                    }

                    onRemoteStream?.invoke(stream)
                },
                onRemoveStream = { stream ->
                    Log.i(TAG, "Remote stream removed")
                    remoteAudioTrack = null
                }
            )

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
            Log.d(TAG, "Peer connection created")

            return peerConnection

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create peer connection", e)
            return null
        }
    }

    /**
     * Create SDP offer (caller side).
     * Returns offer SDP string to send via signaling.
     *
     * Works in both TWO_WAY and RECEIVE_ONLY modes.
     * In RECEIVE_ONLY mode, creates offer without local audio track.
     *
     * @return SDP offer string
     */
    suspend fun createOffer(): String = suspendCoroutine { continuation ->
        try {
            if (!isInitialized) {
                throw IllegalStateException("WebRTC not initialized")
            }

            Log.i(TAG, "Creating offer in ${currentMicrophoneMode} mode...")
            _connectionState.value = ConnectionState.Connecting

            // Create peer connection
            val pc = createPeerConnection()
                ?: throw RuntimeException("Failed to create peer connection")

            // Create and add local audio track (if microphone available)
            // NOTE: With Unified Plan SDP semantics, we must use addTrack() not addStream()
            val audioTrack = createAudioTrack()

            if (audioTrack != null) {
                val streamId = WebRTCConfig.StreamLabels.STREAM_ID
                pc.addTrack(audioTrack, listOf(streamId))
                Log.d(TAG, "Local audio track added to peer connection (Unified Plan)")
            } else {
                Log.w(TAG, "No local audio track - RECEIVE_ONLY mode")
            }

            // Create offer
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        Log.d(TAG, "Offer created successfully")

                        // Set local description
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set")
                                continuation.resume(it.description)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Set local description failed: $error")
                                continuation.resumeWith(Result.failure(RuntimeException(error)))
                            }

                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, it)
                    } ?: run {
                        continuation.resumeWith(Result.failure(RuntimeException("SDP is null")))
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failed: $error")
                    continuation.resumeWith(Result.failure(RuntimeException(error)))
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)

        } catch (e: Exception) {
            Log.e(TAG, "Create offer exception", e)
            continuation.resumeWith(Result.failure(e))
        }
    }

    /**
     * Create SDP answer (callee side).
     * Returns answer SDP string to send via signaling.
     *
     * Works in both TWO_WAY and RECEIVE_ONLY modes.
     * In RECEIVE_ONLY mode, creates answer without local audio track.
     *
     * @param offerSdp SDP offer from caller
     * @return SDP answer string
     */
    suspend fun createAnswer(offerSdp: String): String = suspendCoroutine { continuation ->
        try {
            if (!isInitialized) {
                throw IllegalStateException("WebRTC not initialized")
            }

            Log.i(TAG, "Creating answer in ${currentMicrophoneMode} mode...")
            _connectionState.value = ConnectionState.Connecting

            // Create peer connection
            val pc = createPeerConnection()
                ?: throw RuntimeException("Failed to create peer connection")

            // Create and add local audio track (if microphone available)
            // NOTE: With Unified Plan SDP semantics, we must use addTrack() not addStream()
            val audioTrack = createAudioTrack()

            if (audioTrack != null) {
                val streamId = WebRTCConfig.StreamLabels.STREAM_ID
                pc.addTrack(audioTrack, listOf(streamId))
                Log.d(TAG, "Local audio track added to peer connection (Unified Plan)")
            } else {
                Log.w(TAG, "No local audio track - RECEIVE_ONLY mode")
            }

            // Set remote description (offer)
            val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, offerSdp)

            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description (offer) set successfully")

                    // Create answer
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            sdp?.let {
                                Log.d(TAG, "Answer created successfully")

                                // Set local description
                                pc.setLocalDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "Local description (answer) set")
                                        continuation.resume(it.description)
                                    }

                                    override fun onSetFailure(error: String?) {
                                        Log.e(TAG, "Set local description (answer) failed: $error")
                                        continuation.resumeWith(Result.failure(RuntimeException(error)))
                                    }

                                    override fun onCreateSuccess(p0: SessionDescription?) {}
                                    override fun onCreateFailure(p0: String?) {}
                                }, it)
                            } ?: run {
                                continuation.resumeWith(Result.failure(RuntimeException("Answer SDP is null")))
                            }
                        }

                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Create answer failed: $error")
                            continuation.resumeWith(Result.failure(RuntimeException(error)))
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                    }, constraints)
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description (offer) failed: $error")
                    continuation.resumeWith(Result.failure(RuntimeException(error)))
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDescription)

        } catch (e: Exception) {
            Log.e(TAG, "Create answer exception", e)
            continuation.resumeWith(Result.failure(e))
        }
    }

    /**
     * Handle answer from callee (caller side).
     *
     * @param answerSdp SDP answer from callee
     */
    suspend fun handleAnswer(answerSdp: String): Result<Unit> = suspendCoroutine { continuation ->
        try {
            Log.i(TAG, "Handling answer...")

            val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description (answer) set successfully")
                    continuation.resume(Result.success(Unit))
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description (answer) failed: $error")
                    continuation.resume(Result.failure(RuntimeException(error)))
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDescription)

        } catch (e: Exception) {
            Log.e(TAG, "Handle answer exception", e)
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Add ICE candidate received from remote peer.
     *
     * @param candidateSdp ICE candidate SDP string
     * @param sdpMid SDP media ID
     * @param sdpMLineIndex SDP line index
     */
    fun addIceCandidate(candidateSdp: String, sdpMid: String?, sdpMLineIndex: Int?) {
        try {
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidateSdp)

            peerConnection?.addIceCandidate(iceCandidate)
            Log.d(TAG, "ICE candidate added")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add ICE candidate", e)
        }
    }

    /**
     * Mute local audio.
     * Only works in TWO_WAY mode (when microphone is available).
     */
    fun mute() {
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Cannot mute - RECEIVE_ONLY mode (no microphone)")
            return
        }

        localAudioTrack?.setEnabled(false)
        isMuted = true
        Log.i(TAG, "Audio muted")
    }

    /**
     * Unmute local audio.
     * Only works in TWO_WAY mode (when microphone is available).
     */
    fun unmute() {
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Cannot unmute - RECEIVE_ONLY mode (no microphone)")
            return
        }

        localAudioTrack?.setEnabled(true)
        isMuted = false
        Log.i(TAG, "Audio unmuted")
    }

    /**
     * Check if audio is muted.
     * Always returns false in RECEIVE_ONLY mode.
     */
    fun isMuted(): Boolean {
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            return false // No microphone, can't be muted
        }
        return isMuted
    }

    /**
     * Enable speaker (loud speaker mode).
     * Uses modern audio routing for Android S+ and legacy method for older versions.
     */
    fun enableSpeaker() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Modern method for Android 12+ (API 31+)
            val devices = audioManager.availableCommunicationDevices
            val speaker = devices.firstOrNull {
                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }

            if (speaker != null) {
                audioManager.setCommunicationDevice(speaker)
                isSpeakerOn = true
                Log.i(TAG, "Speaker enabled (modern API)")
            } else {
                Log.w(TAG, "Built-in speaker not found")
            }
        } else {
            // Legacy method for Android < 12
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            isSpeakerOn = true
            Log.i(TAG, "Speaker enabled (legacy API)")
        }
    }

    /**
     * Disable speaker (earpiece mode).
     * Uses modern audio routing for Android S+ and legacy method for older versions.
     */
    fun disableSpeaker() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Modern method for Android 12+ (API 31+)
            audioManager.clearCommunicationDevice()
            isSpeakerOn = false
            Log.i(TAG, "Speaker disabled (modern API)")
        } else {
            // Legacy method for Android < 12
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            isSpeakerOn = false
            Log.i(TAG, "Speaker disabled (legacy API)")
        }
    }

    /**
     * Check if speaker is enabled.
     */
    fun isSpeakerEnabled(): Boolean = isSpeakerOn

    /**
     * Get connection quality based on ICE connection state.
     */
    fun getConnectionQuality(): String {
        return when (peerConnection?.iceConnectionState()) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> "excellent"
            PeerConnection.IceConnectionState.CHECKING -> "good"
            PeerConnection.IceConnectionState.DISCONNECTED -> "poor"
            PeerConnection.IceConnectionState.FAILED -> "failed"
            else -> "unknown"
        }
    }

    /**
     * Close peer connection and cleanup resources.
     */
    fun close() {
        Log.i(TAG, "Closing WebRTC connection...")

        try {
            // Disable tracks
            localAudioTrack?.setEnabled(false)
            remoteAudioTrack?.setEnabled(false)

            // Dispose tracks
            localAudioTrack?.dispose()
            remoteAudioTrack?.dispose()

            // Dispose audio source
            audioSource?.dispose()

            // Close peer connection
            peerConnection?.close()
            peerConnection?.dispose()

            // Reset state
            localAudioTrack = null
            remoteAudioTrack = null
            audioSource = null
            peerConnection = null
            isMuted = false
            isSpeakerOn = false

            _connectionState.value = ConnectionState.Closed

            Log.i(TAG, "WebRTC connection closed")

        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTC connection", e)
        }
    }

    /**
     * Cleanup all WebRTC resources.
     * Call when completely done with calling (e.g., logout).
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up WebRTC...")

        close()

        try {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            isInitialized = false

            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()

            Log.i(TAG, "WebRTC cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error during WebRTC cleanup", e)
        }
    }
}
