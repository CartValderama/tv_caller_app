package com.example.tv_caller_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Profile data class represents a user profile entry.
 * Corresponds to the 'profiles' table in Supabase.
 * Linked to auth.users via the id field.
 */
@Serializable
data class Profile(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Data class for inserting new profiles.
 * The id must match the auth.users.id from Supabase Auth.
 */
@Serializable
data class ProfileInsert(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null
)
