package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.ContactInsert
import com.example.tv_caller_app.model.ContactUpdate
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from

/**
 * Repository class for handling contact data operations.
 * Implements in-memory caching to reduce network calls.
 * Singleton pattern ensures single cache instance across app.
 * Filters all data by authenticated user ID.
 */
class ContactRepository private constructor(
    private val sessionManager: SessionManager
) {

    private val supabase = SupabaseClient.client
    private val TAG = "ContactRepository"

    // In-memory cache
    private var cachedContacts: List<Contact>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    companion object {
        @Volatile
        private var instance: ContactRepository? = null

        fun getInstance(sessionManager: SessionManager): ContactRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactRepository(sessionManager).also { instance = it }
            }
        }
    }

    /**
     * Get the current authenticated user's ID.
     * @throws IllegalStateException if user is not logged in
     */
    private fun getUserId(): String {
        return sessionManager.getUserId()
            ?: throw IllegalStateException("User not logged in")
    }

    /**
     * Check if cache is valid (not expired).
     */
    private fun isCacheValid(): Boolean {
        return cachedContacts != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    /**
     * Invalidate the cache manually.
     * Call this when you add/edit/delete contacts.
     */
    fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedContacts = null
        cacheTimestamp = 0
    }

    /**
     * Fetch all contacts for the authenticated user.
     * Uses in-memory cache if available and valid.
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of contacts
     */
    suspend fun getAllContacts(forceRefresh: Boolean = false): List<Contact> {
        // Return cached data if valid and not forcing refresh
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached contacts (${cachedContacts!!.size} contacts)")
            return cachedContacts!!
        }

        // Fetch from Supabase
        return try {
            val userId = getUserId()
            Log.d(TAG, "Fetching all contacts for user: ${userId.take(8)}...")

            val contacts = supabase.from("contacts")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Contact>()

            // Update cache
            cachedContacts = contacts
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "Successfully fetched and cached ${contacts.size} contacts")
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all contacts: ${e.message}", e)
            e.printStackTrace()

            // Return cached data if available, even if expired
            cachedContacts?.let {
                Log.w(TAG, "Returning stale cached data due to network error")
                return it
            }

            emptyList()
        }
    }

    /**
     * Fetch a single contact by ID.
     * First checks cache, then falls back to network.
     * @param contactId Contact ID to fetch
     * @return Contact if found, null otherwise
     */
    suspend fun getContactById(contactId: String): Contact? {
        return try {
            Log.d(TAG, "Getting contact by ID: $contactId")

            // First check cache
            cachedContacts?.let { contacts ->
                val cachedContact = contacts.find { it.id == contactId }
                if (cachedContact != null && isCacheValid()) {
                    Log.d(TAG, "Returning cached contact: ${cachedContact.name}")
                    return cachedContact
                }
            }

            // Fetch from Supabase if not in cache
            Log.d(TAG, "Fetching contact from Supabase...")
            val contacts = supabase.from("contacts")
                .select {
                    filter {
                        eq("id", contactId)
                    }
                }
                .decodeList<Contact>()

            val contact = contacts.firstOrNull()

            if (contact != null) {
                Log.d(TAG, "Successfully fetched contact: ${contact.name}")
            } else {
                Log.w(TAG, "Contact not found with ID: $contactId")
            }

            contact
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contact by ID: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch favorite contacts.
     * Uses cached contacts if available.
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of favorite contacts
     */
    suspend fun getFavoriteContacts(forceRefresh: Boolean = false): List<Contact> {
        return try {
            Log.d(TAG, "Getting favorite contacts...")

            // Get all contacts (from cache or network)
            val contacts = getAllContacts(forceRefresh)
            val favorites = contacts.filter { it.isFavorite }

            Log.d(TAG, "Successfully got ${favorites.size} favorite contacts")
            favorites
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching favorite contacts: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Create a new contact.
     * @param name Contact name
     * @param phoneNumber Contact phone number
     * @param email Contact email (optional)
     * @param address Contact address (optional)
     * @param notes Contact notes (optional)
     * @param isFavorite Whether contact is a favorite
     * @return True if successful, false otherwise
     */
    suspend fun createContact(
        name: String,
        phoneNumber: String,
        email: String? = null,
        address: String? = null,
        notes: String? = null,
        isFavorite: Boolean = false
    ): Boolean {
        return try {
            val userId = getUserId()
            Log.d(TAG, "Creating new contact: $name for user: ${userId.take(8)}...")

            // Create ContactInsert object with authenticated user ID
            val newContact = ContactInsert(
                userId = userId,
                name = name,
                phoneNumber = phoneNumber,
                email = email,
                address = address,
                notes = notes,
                isFavorite = isFavorite
            )

            Log.d(TAG, "Insert data: $newContact")

            supabase.from("contacts").insert(newContact)

            Log.d(TAG, "Successfully created contact: $name")

            // Invalidate cache to fetch fresh data
            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Update an existing contact.
     * @param contactId Contact ID
     * @param name Updated contact name
     * @param phoneNumber Updated phone number
     * @param email Updated email (optional)
     * @param address Updated address (optional)
     * @param notes Updated notes (optional)
     * @param isFavorite Updated favorite status
     * @return True if successful, false otherwise
     */
    suspend fun updateContact(
        contactId: String,
        name: String,
        phoneNumber: String,
        email: String? = null,
        address: String? = null,
        notes: String? = null,
        isFavorite: Boolean = false
    ): Boolean {
        return try {
            Log.d(TAG, "Updating contact: $contactId")

            // Create ContactUpdate object with proper serialization
            val updates = ContactUpdate(
                name = name,
                phoneNumber = phoneNumber,
                email = email,
                address = address,
                notes = notes,
                isFavorite = isFavorite
            )

            Log.d(TAG, "Update data: $updates")

            supabase.from("contacts")
                .update(updates) {
                    filter {
                        eq("id", contactId)
                    }
                }

            Log.d(TAG, "Successfully updated contact: $contactId")

            // Invalidate cache to fetch fresh data
            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete a contact.
     * @param contactId Contact ID to delete
     * @return True if successful, false otherwise
     */
    suspend fun deleteContact(contactId: String): Boolean {
        return try {
            Log.d(TAG, "Deleting contact: $contactId")

            supabase.from("contacts")
                .delete {
                    filter {
                        eq("id", contactId)
                    }
                }

            Log.d(TAG, "Successfully deleted contact: $contactId")

            // Invalidate cache to fetch fresh data
            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
}
