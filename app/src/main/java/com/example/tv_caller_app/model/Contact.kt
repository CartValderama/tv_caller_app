package com.example.tv_caller_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contact data class represents a contact entry.
 * Corresponds to the 'contacts' table in Supabase.
 */
@Serializable
data class Contact(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val name: String,
    @SerialName("phone_number")
    val phoneNumber: String,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("is_favorite")
    val isFavorite: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Data class for inserting new contacts.
 * Excludes auto-generated fields (id, created_at, updated_at).
 */
@Serializable
data class ContactInsert(
    @SerialName("user_id")
    val userId: String,
    val name: String,
    @SerialName("phone_number")
    val phoneNumber: String,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("is_favorite")
    val isFavorite: Boolean = false
)

/**
 * Data class for updating existing contacts.
 * Excludes id and auto-generated timestamp fields.
 */
@Serializable
data class ContactUpdate(
    val name: String,
    @SerialName("phone_number")
    val phoneNumber: String,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("is_favorite")
    val isFavorite: Boolean = false
)
