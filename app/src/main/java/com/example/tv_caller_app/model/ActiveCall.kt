package com.example.tv_caller_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ActiveCall data class represents a currently active or recent call.
 * Corresponds to the 'active_calls' table in Supabase.
 * Used for tracking call state and enabling recovery from network issues.
 */
@Serializable
data class ActiveCall(
    val id: String,

    @SerialName("caller_id")
    val callerId: String,

    @SerialName("callee_id")
    val calleeId: String,

    @SerialName("call_status")
    val callStatus: String,  // initiating, ringing, connected, ended, missed, rejected, failed

    @SerialName("media_type")
    val mediaType: String = "audio",  // audio, video

    @SerialName("started_at")
    val startedAt: String? = null,

    @SerialName("connected_at")
    val connectedAt: String? = null,

    @SerialName("ended_at")
    val endedAt: String? = null,

    @SerialName("duration_seconds")
    val durationSeconds: Int = 0,

    @SerialName("ice_connection_state")
    val iceConnectionState: String? = null,

    @SerialName("connection_quality")
    val connectionQuality: String? = "unknown",  // excellent, good, fair, poor, unknown

    @SerialName("end_reason")
    val endReason: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Data class for inserting new active calls.
 * Used when initiating a call.
 */
@Serializable
data class ActiveCallInsert(
    @SerialName("caller_id")
    val callerId: String,

    @SerialName("callee_id")
    val calleeId: String,

    @SerialName("call_status")
    val callStatus: String = "initiating",

    @SerialName("media_type")
    val mediaType: String = "audio"
)

/**
 * Data class for updating active call status.
 * Used to update call state during the call lifecycle.
 */
@Serializable
data class ActiveCallUpdate(
    @SerialName("call_status")
    val callStatus: String? = null,

    @SerialName("connected_at")
    val connectedAt: String? = null,

    @SerialName("ended_at")
    val endedAt: String? = null,

    @SerialName("duration_seconds")
    val durationSeconds: Int? = null,

    @SerialName("ice_connection_state")
    val iceConnectionState: String? = null,

    @SerialName("connection_quality")
    val connectionQuality: String? = null,

    @SerialName("end_reason")
    val endReason: String? = null
)
