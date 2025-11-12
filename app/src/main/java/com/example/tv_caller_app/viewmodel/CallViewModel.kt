package com.example.tv_caller_app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.calling.repository.PresenceRepository
import com.example.tv_caller_app.calling.signaling.SignalingEvent
import com.example.tv_caller_app.calling.signaling.SignalingManager
import com.example.tv_caller_app.calling.signaling.SignalingMessage
import com.example.tv_caller_app.calling.webrtc.WebRTCManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * CallViewModel - Central state management for calling functionality.
 *
 * Responsibilities:
 * - Coordinate SignalingManager, WebRTCManager, and PresenceRepository
 * - Manage complete call flow (initiate → ring → answer → connected → ended)
 * - Expose call state and events to UI via LiveData
 * - Handle WebRTC lifecycle
 *
 * Call States:
 * - IDLE: No active call
 * - INITIATING: Starting outgoing call
 * - RINGING_OUTGOING: Waiting for callee to answer
 * - RINGING_INCOMING: Receiving incoming call
 * - CONNECTING: WebRTC connection being established
 * - CONNECTED: Active call in progress
 * - ENDING: Call termination in progress
 * - ENDED: Call terminated
 *
 * Usage:
 * ```
 * val callViewModel = ViewModelProvider(this, CallViewModelFactory(context, sessionManager))[CallViewModel::class.java]
 * callViewModel.initialize()
 *
 * // Observe call state
 * callViewModel.callState.observe(this) { state ->
 *     when (state) {
 *         is CallState.RingingIncoming -> // Show incoming call UI
 *         is CallState.Connected -> // Show in-call UI
 *     }
 * }
 *
 * // Make a call
 * callViewModel.initiateCall(contactId, contactName)
 * ```
 */
class CallViewModel(
    private val context: Context,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val TAG = "CallViewModel"

    // Components
    private var signalingManager: SignalingManager? = null
    private var webRTCManager: WebRTCManager? = null
    private var presenceRepository: PresenceRepository? = null

    // Current user info
    private val currentUserId = sessionManager.getUserId() ?: ""
    private val currentUserName = "User" // TODO: Get username from profile or session

    // Call state
    sealed class CallState {
        object Idle : CallState()
        data class Initiating(val contactId: String, val contactName: String) : CallState()
        data class RingingOutgoing(val contactId: String, val contactName: String) : CallState()
        data class RingingIncoming(val callerId: String, val callerName: String, val callerUsername: String) : CallState()
        object Connecting : CallState()
        data class Connected(val remoteUserId: String, val remoteUserName: String, val isIncoming: Boolean) : CallState()
        data class Ending(val reason: String) : CallState()
        data class Ended(val reason: String, val duration: Long = 0) : CallState()
        data class Failed(val error: String) : CallState()
    }

    private val _callState = MutableLiveData<CallState>(CallState.Idle)
    val callState: LiveData<CallState> = _callState

    // Current call data
    private var currentCallId: String? = null
    private var remoteUserId: String? = null
    private var remoteUserName: String? = null
    private var isIncomingCall = false
    private var callStartTime: Long = 0
    private var pendingIceCandidates = mutableListOf<IceCandidate>()
    private var pendingOfferSdp: String? = null

    // Microphone status
    private val _microphoneMode = MutableLiveData<WebRTCManager.MicrophoneMode>()
    val microphoneMode: LiveData<WebRTCManager.MicrophoneMode> = _microphoneMode

    private val _microphoneMessage = MutableLiveData<String>()
    val microphoneMessage: LiveData<String> = _microphoneMessage

    // Call timer
    private var callTimerJob: Job? = null
    private val _callDuration = MutableLiveData<Long>(0L)
    val callDuration: LiveData<Long> = _callDuration

    // Mute and speaker state
    private val _isMuted = MutableLiveData<Boolean>(false)
    val isMuted: LiveData<Boolean> = _isMuted

    private val _isSpeakerOn = MutableLiveData<Boolean>(false)
    val isSpeakerOn: LiveData<Boolean> = _isSpeakerOn

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Connection quality
    private val _connectionState = MutableLiveData<PeerConnection.IceConnectionState>()
    val connectionState: LiveData<PeerConnection.IceConnectionState> = _connectionState

    /**
     * Initialize CallViewModel.
     * Sets up SignalingManager, WebRTCManager, and begins observing signaling events.
     */
    fun initialize() {
        if (currentUserId.isEmpty()) {
            Log.e(TAG, "Cannot initialize CallViewModel - no user ID")
            _errorMessage.value = "User not logged in"
            return
        }

        Log.d(TAG, "Initializing CallViewModel for user: $currentUserId")

        try {
            // Initialize WebRTC
            webRTCManager = WebRTCManager(context).apply {
                initialize()

                // Setup WebRTC callbacks
                onIceCandidate = { candidate ->
                    handleLocalIceCandidate(candidate)
                }

                onConnectionStateChange = { state ->
                    handleConnectionStateChange(state)
                }

                onMicrophoneModeChanged = { mode, message ->
                    _microphoneMode.postValue(mode)
                    _microphoneMessage.postValue(message)
                }
            }

            // Initialize SignalingManager
            signalingManager = SignalingManager(currentUserId)

            // Initialize PresenceRepository
            presenceRepository = PresenceRepository.getInstance(sessionManager)

            // Subscribe to signaling events
            viewModelScope.launch {
                signalingManager?.initialize()
                observeSignalingEvents()
            }

            Log.i(TAG, "CallViewModel initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CallViewModel", e)
            _errorMessage.value = "Failed to initialize calling: ${e.message}"
        }
    }

    /**
     * Observe signaling events from SignalingManager.
     */
    private suspend fun observeSignalingEvents() {
        signalingManager?.events?.collect { event ->
            Log.d(TAG, "Received signaling event: $event")

            when (event) {
                is SignalingEvent.IncomingCall -> handleIncomingCall(event)
                is SignalingEvent.CallAnswered -> handleCallAnswered(event)
                is SignalingEvent.NewIceCandidate -> handleRemoteIceCandidate(event)
                is SignalingEvent.CallRejected -> handleCallRejected(event)
                is SignalingEvent.CallEnded -> handleCallEnded(event)
                is SignalingEvent.RemoteAction -> handleRemoteAction(event)
                is SignalingEvent.Error -> handleSignalingError(event)
                is SignalingEvent.Connected -> Log.d(TAG, "Signaling connected")
                is SignalingEvent.Disconnected -> Log.d(TAG, "Signaling disconnected")
            }
        }
    }

    /**
     * Initiate an outgoing call to a contact.
     *
     * @param contactId The user ID of the contact to call
     * @param contactName The display name of the contact
     */
    fun initiateCall(contactId: String, contactName: String) {
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Cannot initiate call - already in call state: ${_callState.value}")
            _errorMessage.value = "Already in a call"
            return
        }

        Log.i(TAG, "Initiating call to: $contactName (ID: $contactId)")

        _callState.value = CallState.Initiating(contactId, contactName)
        remoteUserId = contactId
        remoteUserName = contactName
        isIncomingCall = false
        currentCallId = generateCallId()

        viewModelScope.launch {
            try {
                // Update presence to in_call
                presenceRepository?.updateWebRTCStatus("in_call")

                // Create WebRTC offer (peer connection created internally)
                val offer = webRTCManager?.createOffer()

                if (offer != null) {
                    // Send offer via signaling
                    signalingManager?.sendCallOffer(
                        targetUserId = contactId,
                        callerName = currentUserName,
                        callerUsername = currentUserName,
                        offer = offer
                    )

                    _callState.value = CallState.RingingOutgoing(contactId, contactName)
                    Log.i(TAG, "Call offer sent to $contactName")

                } else {
                    throw Exception("Failed to create WebRTC offer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate call", e)
                _callState.value = CallState.Failed("Failed to initiate call: ${e.message}")
                cleanup()
            }
        }
    }

    /**
     * Answer an incoming call.
     */
    fun answerCall() {
        val currentState = _callState.value
        if (currentState !is CallState.RingingIncoming) {
            Log.w(TAG, "Cannot answer - not in ringing incoming state")
            return
        }

        Log.i(TAG, "Answering call from: ${currentState.callerName}")

        _callState.value = CallState.Connecting

        viewModelScope.launch {
            try {
                // Update presence to in_call
                presenceRepository?.updateWebRTCStatus("in_call")

                // Get the offer SDP from the ringing state
                // For now, we'll need to store the offer SDP when handling incoming call
                // TODO: Store offer SDP in a member variable during handleIncomingCall
                val offerSdp = pendingOfferSdp ?: throw Exception("No offer SDP available")

                // Create answer (peer connection and remote description set internally)
                val answer = webRTCManager?.createAnswer(offerSdp)

                if (answer != null) {
                    // Send answer via signaling
                    signalingManager?.sendCallAnswer(
                        callerId = currentState.callerId,
                        calleeName = currentUserName,
                        answer = answer
                    )

                    // Process any pending ICE candidates
                    pendingIceCandidates.forEach { candidate ->
                        webRTCManager?.addIceCandidate(
                            candidateSdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                    pendingIceCandidates.clear()

                    _callState.value = CallState.Connected(
                        remoteUserId = currentState.callerId,
                        remoteUserName = currentState.callerName,
                        isIncoming = true
                    )

                    startCallTimer()
                    Log.i(TAG, "Call answered successfully")

                } else {
                    throw Exception("Failed to create WebRTC answer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call", e)
                _callState.value = CallState.Failed("Failed to answer call: ${e.message}")
                cleanup()
            }
        }
    }

    /**
     * Reject an incoming call.
     */
    fun rejectCall() {
        val currentState = _callState.value
        if (currentState !is CallState.RingingIncoming) {
            Log.w(TAG, "Cannot reject - not in ringing incoming state")
            return
        }

        Log.i(TAG, "Rejecting call from: ${currentState.callerName}")

        viewModelScope.launch {
            try {
                signalingManager?.rejectCall(
                    callerId = currentState.callerId,
                    reason = "user_declined"
                )

                _callState.value = CallState.Ended("rejected", 0)
                cleanup()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject call", e)
                cleanup()
            }
        }
    }

    /**
     * End an active call.
     */
    fun endCall() {
        val currentState = _callState.value

        Log.i(TAG, "Ending call - current state: $currentState")

        _callState.value = CallState.Ending("user_hangup")

        viewModelScope.launch {
            try {
                val duration = if (callStartTime > 0) {
                    (System.currentTimeMillis() - callStartTime) / 1000
                } else {
                    0L
                }

                // Send end signal to remote
                remoteUserId?.let { remoteId ->
                    signalingManager?.endCall(
                        otherUserId = remoteId,
                        reason = "user_hangup",
                        duration = duration
                    )
                }

                _callState.value = CallState.Ended("user_hangup", duration)

                // TODO: Log to call_history table

                cleanup()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call gracefully", e)
                cleanup()
            }
        }
    }

    /**
     * Toggle mute state.
     */
    fun toggleMute() {
        val currentlyMuted = _isMuted.value ?: false
        if (currentlyMuted) {
            webRTCManager?.unmute()
            _isMuted.value = false
            Log.d(TAG, "Microphone unmuted")
        } else {
            webRTCManager?.mute()
            _isMuted.value = true
            Log.d(TAG, "Microphone muted")
        }

        // TODO: Send mute action to remote via signaling
    }

    /**
     * Toggle speaker state.
     */
    fun toggleSpeaker() {
        val currentlySpeaker = _isSpeakerOn.value ?: false
        if (currentlySpeaker) {
            webRTCManager?.disableSpeaker()
            _isSpeakerOn.value = false
            Log.d(TAG, "Speaker disabled")
        } else {
            webRTCManager?.enableSpeaker()
            _isSpeakerOn.value = true
            Log.d(TAG, "Speaker enabled")
        }
    }

    // ===== Private Helper Methods =====

    /**
     * Handle incoming call event from signaling.
     */
    private fun handleIncomingCall(event: SignalingEvent.IncomingCall) {
        Log.i(TAG, "Incoming call from: ${event.callerName} (${event.callerId})")

        // Check if already in a call
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Rejecting incoming call - already in call")
            viewModelScope.launch {
                signalingManager?.rejectCall(event.callerId, "busy")
            }
            return
        }

        // Store call data
        remoteUserId = event.callerId
        remoteUserName = event.callerName
        isIncomingCall = true
        currentCallId = generateCallId()
        pendingOfferSdp = event.offer  // Store offer SDP for later use in answerCall()

        // Transition to ringing state (peer connection will be created in answerCall())
        _callState.value = CallState.RingingIncoming(
            event.callerId,
            event.callerName,
            event.callerUsername
        )
    }

    /**
     * Handle call answered event from signaling.
     */
    private fun handleCallAnswered(event: SignalingEvent.CallAnswered) {
        Log.i(TAG, "Call answered by remote peer")

        viewModelScope.launch {
            try {
                // Set remote answer description
                val result = webRTCManager?.handleAnswer(event.answer)

                if (result?.isSuccess == true) {
                    // Process any pending ICE candidates
                    pendingIceCandidates.forEach { candidate ->
                        webRTCManager?.addIceCandidate(
                            candidateSdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                    pendingIceCandidates.clear()

                    _callState.value = CallState.Connected(
                        remoteUserId = remoteUserId ?: "",
                        remoteUserName = remoteUserName ?: "Unknown",
                        isIncoming = false
                    )

                    startCallTimer()
                } else {
                    throw Exception("Failed to set remote answer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle call answered", e)
                _callState.value = CallState.Failed("Failed to connect: ${e.message}")
                cleanup()
            }
        }
    }

    /**
     * Handle remote ICE candidate.
     */
    private fun handleRemoteIceCandidate(event: SignalingEvent.NewIceCandidate) {
        Log.d(TAG, "Received remote ICE candidate")

        val candidate = IceCandidate(event.sdpMid, event.sdpMLineIndex ?: 0, event.candidate)

        // If peer connection is in connected state, add immediately
        // Otherwise, queue for later
        if (_callState.value is CallState.Connected) {
            webRTCManager?.addIceCandidate(
                candidateSdp = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        } else {
            Log.d(TAG, "Queueing ICE candidate until call is connected")
            pendingIceCandidates.add(candidate)
        }
    }

    /**
     * Handle local ICE candidate generation.
     */
    private fun handleLocalIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "Generated local ICE candidate")

        viewModelScope.launch {
            remoteUserId?.let { remoteId ->
                signalingManager?.sendIceCandidate(
                    targetUserId = remoteId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }
        }
    }

    /**
     * Handle connection state changes.
     */
    private fun handleConnectionStateChange(state: PeerConnection.IceConnectionState) {
        Log.d(TAG, "ICE connection state changed: $state")
        _connectionState.postValue(state)

        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                Log.i(TAG, "WebRTC connection established")
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                Log.w(TAG, "WebRTC connection disconnected")
            }
            PeerConnection.IceConnectionState.FAILED -> {
                Log.e(TAG, "WebRTC connection failed")
                _callState.value = CallState.Failed("Connection failed")
                cleanup()
            }
            PeerConnection.IceConnectionState.CLOSED -> {
                Log.i(TAG, "WebRTC connection closed")
            }
            else -> {}
        }
    }

    /**
     * Handle call rejected event.
     */
    private fun handleCallRejected(event: SignalingEvent.CallRejected) {
        Log.i(TAG, "Call rejected: ${event.reason}")
        _callState.value = CallState.Ended("rejected", 0)
        cleanup()
    }

    /**
     * Handle call ended event.
     */
    private fun handleCallEnded(event: SignalingEvent.CallEnded) {
        Log.i(TAG, "Call ended: ${event.reason}")
        _callState.value = CallState.Ended(event.reason, event.duration)
        cleanup()
    }

    /**
     * Handle remote action (mute, unmute, etc.)
     */
    private fun handleRemoteAction(event: SignalingEvent.RemoteAction) {
        Log.d(TAG, "Remote action: ${event.action}")
        // TODO: Handle remote mute/unmute indicators
    }

    /**
     * Handle signaling errors.
     */
    private fun handleSignalingError(event: SignalingEvent.Error) {
        Log.e(TAG, "Signaling error: ${event.message}", event.exception)
        _errorMessage.value = event.message
    }

    /**
     * Start call duration timer.
     */
    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        _callDuration.value = 0L

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val duration = (System.currentTimeMillis() - callStartTime) / 1000
                _callDuration.postValue(duration)
            }
        }
    }

    /**
     * Stop call timer.
     */
    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
    }

    /**
     * Generate unique call ID.
     */
    private fun generateCallId(): String {
        return "call_${currentUserId}_${System.currentTimeMillis()}"
    }

    /**
     * Cleanup call resources.
     */
    private fun cleanup() {
        Log.d(TAG, "Cleaning up call resources")

        stopCallTimer()

        webRTCManager?.close()

        viewModelScope.launch {
            presenceRepository?.updateWebRTCStatus("available")
        }

        // Reset state
        remoteUserId = null
        remoteUserName = null
        currentCallId = null
        isIncomingCall = false
        callStartTime = 0
        pendingIceCandidates.clear()
        pendingOfferSdp = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        _callDuration.value = 0L

        // Return to idle after a delay
        viewModelScope.launch {
            delay(2000)
            if (_callState.value is CallState.Ended || _callState.value is CallState.Failed) {
                _callState.value = CallState.Idle
            }
        }
    }

    /**
     * Cleanup on ViewModel destruction.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "CallViewModel cleared")

        stopCallTimer()
        webRTCManager?.cleanup()
        viewModelScope.launch {
            signalingManager?.cleanup()
        }
    }
}
