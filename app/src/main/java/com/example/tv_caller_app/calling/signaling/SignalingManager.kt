package com.example.tv_caller_app.calling.signaling

import android.util.Log
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Manages WebRTC signaling via Supabase Realtime.
 *
 * Architecture:
 * - Each user subscribes to their own channel: "call:[userId]"
 * - To call User B, send message to "call:[userBId]"
 * - User B receives on their subscribed channel
 * - ICE candidates exchanged on both channels
 *
 * This class handles:
 * 1. Channel subscription/unsubscription
 * 2. Sending/receiving signaling messages
 * 3. Event emission for UI observation
 *
 * Usage:
 * ```
 * val signaling = SignalingManager(currentUserId)
 * signaling.initialize()
 *
 * // Observe events
 * signaling.events.collect { event ->
 *     when (event) {
 *         is SignalingEvent.IncomingCall -> // Handle incoming call
 *         is SignalingEvent.CallAnswered -> // Handle answer
 *         is SignalingEvent.NewIceCandidate -> // Add ICE candidate
 *     }
 * }
 *
 * // Send offer
 * signaling.sendCallOffer(targetUserId, callerName, callerUsername, sdpOffer)
 * ```
 *
 * @param currentUserId The ID of the current user (from SessionManager)
 */
class SignalingManager(
    private val currentUserId: String
) {
    private val TAG = "SignalingManager"
    private val supabase = SupabaseClient.client
    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for signaling operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Shared signaling channel for WebRTC (all users subscribe to this)
    private var signalingChannel: RealtimeChannel? = null

    // Events flow for UI/ViewModel observation
    private val _events = MutableSharedFlow<SignalingEvent>(replay = 0, extraBufferCapacity = 10)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    // Track initialization state
    private var isInitialized = false

    /**
     * Initialize signaling - subscribe to my call channel.
     * Call this when user logs in or app starts.
     *
     * This sets up the channel to receive incoming call notifications.
     */
    suspend fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "SignalingManager already initialized")
            return
        }

        try {
            Log.d(TAG, "Initializing SignalingManager for user: $currentUserId")

            // Create and subscribe to SHARED signaling channel
            signalingChannel = supabase.channel("webrtc-signaling")

            // Listen for incoming messages
            signalingChannel?.let { channel ->
                // Broadcast flow for all message types
                channel.broadcastFlow<JsonObject>(event = "message")
                    .onEach { messageJson ->
                        handleIncomingMessage(messageJson)
                    }
                    .launchIn(scope)

                // Subscribe to channel
                channel.subscribe()

                Log.i(TAG, "✅ Subscribed to shared signaling channel for user: $currentUserId")
                _events.emit(SignalingEvent.Connected)
            }

            isInitialized = true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SignalingManager", e)
            _events.emit(SignalingEvent.Error("Failed to initialize signaling", e))
        }
    }

    /**
     * Send call offer to another user.
     *
     * @param targetUserId ID of user to call
     * @param callerName Name to display to callee
     * @param callerUsername Username to display
     * @param offer SDP offer from WebRTC
     * @param mediaType "audio" or "video"
     */
    suspend fun sendCallOffer(
        targetUserId: String,
        callerName: String,
        callerUsername: String,
        offer: String,
        mediaType: String = "audio"
    ) {
        try {
            Log.d(TAG, "Sending call offer to: $targetUserId")

            // Create offer message
            val message = SignalingMessage.CallOffer(
                callerId = currentUserId,
                callerName = callerName,
                callerUsername = callerUsername,
                sdp = offer,
                mediaType = mediaType
            )

            val payload = buildJsonObject {
                put("targetUserId", targetUserId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            Log.d(TAG, "📤 Broadcasting offer to shared channel - Target: $targetUserId - Payload: $payload")

            // Broadcast to SHARED channel (not individual channel)
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "✅ Call offer broadcast completed")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call offer", e)
            _events.emit(SignalingEvent.Error("Failed to send call offer", e))
        }
    }

    /**
     * Send call answer to caller.
     *
     * @param callerId ID of caller
     * @param calleeName Name of answerer
     * @param answer SDP answer from WebRTC
     */
    suspend fun sendCallAnswer(
        callerId: String,
        calleeName: String,
        answer: String
    ) {
        try {
            Log.d(TAG, "Sending call answer to: $callerId")

            // Create answer message
            val message = SignalingMessage.CallAnswer(
                calleeId = currentUserId,
                calleeName = calleeName,
                sdp = answer
            )

            val payload = buildJsonObject {
                put("targetUserId", callerId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            // Broadcast to SHARED channel
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call answer sent to: $callerId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call answer", e)
            _events.emit(SignalingEvent.Error("Failed to send call answer", e))
        }
    }

    /**
     * Send ICE candidate to other person.
     * Called multiple times as candidates are discovered.
     *
     * @param targetUserId ID of other person
     * @param candidate ICE candidate string
     * @param sdpMid SDP media ID
     * @param sdpMLineIndex SDP line index
     */
    suspend fun sendIceCandidate(
        targetUserId: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?
    ) {
        try {
            // Create ICE message
            val message = SignalingMessage.IceCandidate(
                candidate = candidate,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex
            )

            val payload = buildJsonObject {
                put("targetUserId", targetUserId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            // Broadcast to SHARED channel
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.d(TAG, "ICE candidate sent to: $targetUserId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ICE candidate", e)
        }
    }

    /**
     * Reject incoming call.
     *
     * @param callerId ID of caller
     * @param reason Rejection reason
     */
    suspend fun rejectCall(callerId: String, reason: String = "user_declined") {
        try {
            Log.d(TAG, "Rejecting call from: $callerId")

            // Create rejection message
            val message = SignalingMessage.CallRejected(reason = reason)

            val payload = buildJsonObject {
                put("targetUserId", callerId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            // Broadcast to SHARED channel
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call rejection sent to: $callerId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call", e)
        }
    }

    /**
     * End call.
     *
     * @param otherUserId ID of other person
     * @param reason Why call ended
     * @param duration Call duration in seconds
     */
    suspend fun endCall(otherUserId: String, reason: String, duration: Long = 0) {
        try {
            Log.d(TAG, "Ending call with: $otherUserId")

            // Create end message
            val message = SignalingMessage.CallEnded(
                reason = reason,
                duration = duration
            )

            val payload = buildJsonObject {
                put("targetUserId", otherUserId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            // Broadcast to SHARED channel
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call end sent to: $otherUserId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
        }
    }

    /**
     * Send in-call action (mute, unmute, etc.)
     *
     * @param targetUserId ID of other person
     * @param action Action to send
     */
    suspend fun sendAction(targetUserId: String, action: SignalingMessage.Action) {
        try {
            // Create action message
            val message = SignalingMessage.CallAction(action = action)

            val payload = buildJsonObject {
                put("targetUserId", targetUserId)  // Include target for filtering
                put("payload", json.encodeToString(message))
            }

            // Broadcast to SHARED channel
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.d(TAG, "Action sent: $action to $targetUserId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send action", e)
        }
    }

    /**
     * Handle incoming message from Supabase Realtime.
     * Parses the JSON and emits appropriate SignalingEvent.
     */
    private fun handleIncomingMessage(messageJsonObject: JsonObject) {
        scope.launch {
            try {
                Log.d(TAG, "📨 Raw broadcast received: $messageJsonObject")

                // Check if message is for current user
                val targetUserId = messageJsonObject["targetUserId"]?.jsonPrimitive?.contentOrNull

                if (targetUserId != null && targetUserId != currentUserId) {
                    Log.d(TAG, "⏭️ Message not for me (target: $targetUserId, me: $currentUserId) - ignoring")
                    return@launch
                }

                // Extract the payload string from the JsonObject
                val messageJson = messageJsonObject["payload"]?.jsonPrimitive?.contentOrNull ?: ""

                Log.d(TAG, "📨 Payload extracted for me: $messageJson")

                // Parse message type by checking for unique fields
                when {
                    "CallOffer" in messageJson || "callerId" in messageJson -> {
                        val msg = json.decodeFromString<SignalingMessage.CallOffer>(messageJson)
                        _events.emit(
                            SignalingEvent.IncomingCall(
                                callerId = msg.callerId,
                                callerName = msg.callerName,
                                callerUsername = msg.callerUsername,
                                offer = msg.sdp,
                                mediaType = msg.mediaType
                            )
                        )
                    }

                    "CallAnswer" in messageJson || "calleeId" in messageJson -> {
                        val msg = json.decodeFromString<SignalingMessage.CallAnswer>(messageJson)
                        _events.emit(SignalingEvent.CallAnswered(answer = msg.sdp))
                    }

                    "IceCandidate" in messageJson || ("candidate" in messageJson && "sdpMid" in messageJson) -> {
                        val msg = json.decodeFromString<SignalingMessage.IceCandidate>(messageJson)
                        _events.emit(
                            SignalingEvent.NewIceCandidate(
                                candidate = msg.candidate,
                                sdpMid = msg.sdpMid,
                                sdpMLineIndex = msg.sdpMLineIndex
                            )
                        )
                    }

                    "CallRejected" in messageJson || ("timestamp" in messageJson && "duration" !in messageJson && "callerId" !in messageJson) -> {
                        val msg = json.decodeFromString<SignalingMessage.CallRejected>(messageJson)
                        _events.emit(SignalingEvent.CallRejected(reason = msg.reason))
                    }

                    "CallEnded" in messageJson || ("reason" in messageJson && "duration" in messageJson) -> {
                        val msg = json.decodeFromString<SignalingMessage.CallEnded>(messageJson)
                        _events.emit(SignalingEvent.CallEnded(reason = msg.reason, duration = msg.duration))
                    }

                    "CallAction" in messageJson -> {
                        val msg = json.decodeFromString<SignalingMessage.CallAction>(messageJson)
                        _events.emit(SignalingEvent.RemoteAction(action = msg.action))
                    }

                    else -> {
                        Log.w(TAG, "Unknown message type: $messageJson")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle incoming message", e)
                _events.emit(SignalingEvent.Error("Failed to parse signaling message", e))
            }
        }
    }

    /**
     * Cleanup - unsubscribe from channels.
     * Call when user logs out or app closes.
     */
    suspend fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up SignalingManager")

            signalingChannel?.unsubscribe()
            signalingChannel = null
            isInitialized = false

            _events.emit(SignalingEvent.Disconnected)

            // Cancel coroutine scope to prevent memory leaks
            scope.cancel()

            Log.i(TAG, "SignalingManager cleaned up")

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
