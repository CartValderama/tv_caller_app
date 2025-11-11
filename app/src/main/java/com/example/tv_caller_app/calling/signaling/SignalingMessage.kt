package com.example.tv_caller_app.calling.signaling

import kotlinx.serialization.Serializable

/**
 * Sealed class representing all signaling messages exchanged via Supabase Realtime.
 *
 * How signaling works:
 * 1. Each user subscribes to channel: "call:[userId]"
 * 2. To send message to User B, broadcast to "call:[userId_B]"
 * 3. User B receives message on their subscribed channel
 *
 * Message flow example:
 * - User A wants to call User B
 * - A sends CallOffer to channel "call:user_b_id"
 * - B receives CallOffer on their subscribed channel
 * - B sends CallAnswer to channel "call:user_a_id"
 * - Both exchange ICE candidates on each other's channels
 */
sealed class SignalingMessage {

    /**
     * Call initiation - sent to callee's channel.
     * Contains SDP offer and caller information.
     */
    @Serializable
    data class CallOffer(
        val callerId: String,
        val callerName: String,
        val callerUsername: String,
        val sdp: String,              // Session Description Protocol
        val mediaType: String = "audio",  // audio or video
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * Call answer - sent to caller's channel.
     * Contains SDP answer from callee.
     */
    @Serializable
    data class CallAnswer(
        val calleeId: String,
        val calleeName: String,
        val sdp: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * ICE candidate - connection path discovery.
     * Sent multiple times as candidates are discovered by WebRTC.
     */
    @Serializable
    data class IceCandidate(
        val candidate: String,        // "candidate:... IP:PORT ..."
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * Call rejection - callee declines the call.
     */
    @Serializable
    data class CallRejected(
        val reason: String = "user_declined",
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * Call ended - either party hangs up.
     */
    @Serializable
    data class CallEnded(
        val reason: String,
        val duration: Long = 0,
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * In-call actions (mute, unmute, hold, etc.)
     */
    @Serializable
    data class CallAction(
        val action: Action,
        val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * Actions that can be performed during a call.
     */
    enum class Action {
        MUTE,
        UNMUTE,
        HOLD,
        RESUME,
        TOGGLE_SPEAKER,
        TOGGLE_VIDEO  // For future video calls (Phase 2)
    }
}

/**
 * Events emitted by SignalingManager for UI/ViewModel to observe.
 * These are parsed from incoming SignalingMessages.
 */
sealed class SignalingEvent {
    /**
     * Incoming call received.
     */
    data class IncomingCall(
        val callerId: String,
        val callerName: String,
        val callerUsername: String,
        val offer: String,
        val mediaType: String
    ) : SignalingEvent()

    /**
     * Call was answered by callee.
     */
    data class CallAnswered(val answer: String) : SignalingEvent()

    /**
     * New ICE candidate received from remote peer.
     */
    data class NewIceCandidate(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?
    ) : SignalingEvent()

    /**
     * Call was rejected by callee.
     */
    data class CallRejected(val reason: String) : SignalingEvent()

    /**
     * Call ended by either party.
     */
    data class CallEnded(val reason: String, val duration: Long) : SignalingEvent()

    /**
     * Remote user performed an action (mute, unmute, etc.)
     */
    data class RemoteAction(val action: SignalingMessage.Action) : SignalingEvent()

    /**
     * Error occurred during signaling.
     */
    data class Error(val message: String, val exception: Throwable? = null) : SignalingEvent()

    /**
     * Successfully connected to Realtime.
     */
    data object Connected : SignalingEvent()

    /**
     * Disconnected from Realtime.
     */
    data object Disconnected : SignalingEvent()
}
