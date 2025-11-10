package com.example.tv_caller_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CallHistory data class represents a call log entry.
 * Corresponds to the 'call_history' table in Supabase.
 */
@Serializable
data class CallHistory(
    @SerialName("id")
    val id: String,

    @SerialName("user_id")
    val userId: String,

    @SerialName("contact_id")
    val contactId: String? = null,

    @SerialName("phone_number")
    val phoneNumber: String,

    @SerialName("contact_name")
    val contactName: String? = null,

    @SerialName("call_type")
    val callType: String, // Store as string from database

    @SerialName("call_duration")
    val callDuration: Int = 0,

    @SerialName("call_timestamp")
    val callTimestamp: String? = null,

    @SerialName("notes")
    val notes: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)
