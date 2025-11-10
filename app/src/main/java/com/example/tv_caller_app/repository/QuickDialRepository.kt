package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.QuickDialEntry

/**
 * Repository for managing Quick Dial functionality.
 * Analyzes call history to determine top contacts and manages quick dial entries.
 * Implements in-memory caching to reduce network calls.
 * Singleton pattern ensures single cache instance across app.
 * Filters all data by authenticated user ID.
 */
class QuickDialRepository private constructor(
    sessionManager: SessionManager
) {

    private val contactRepository = ContactRepository.getInstance(sessionManager)
    private val callHistoryRepository = CallHistoryRepository.getInstance(sessionManager)
    private val TAG = "QuickDialRepository"

    // In-memory cache for quick dial entries
    private var cachedQuickDialEntries: List<QuickDialEntry>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    companion object {
        @Volatile
        private var instance: QuickDialRepository? = null

        fun getInstance(sessionManager: SessionManager): QuickDialRepository {
            return instance ?: synchronized(this) {
                instance ?: QuickDialRepository(sessionManager).also { instance = it }
            }
        }
    }

    /**
     * Check if cache is valid (not expired).
     */
    private fun isCacheValid(): Boolean {
        return cachedQuickDialEntries != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    /**
     * Invalidate the cache manually.
     * Call this when call history changes significantly.
     */
    fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedQuickDialEntries = null
        cacheTimestamp = 0
    }

    /**
     * Get quick dial entries (top 4 contacts based on call history).
     * Each entry includes the contact, position, score, and call count.
     * Uses in-memory cache if available and valid.
     *
     * @param forceRefresh If true, bypass cache and recalculate
     * @return List of QuickDialEntry sorted by score (highest first)
     */
    suspend fun getQuickDialEntries(forceRefresh: Boolean = false): List<QuickDialEntry> {
        // Return cached data if valid and not forcing refresh
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached quick dial entries (${cachedQuickDialEntries!!.size} entries)")
            return cachedQuickDialEntries!!
        }

        return try {
            Log.d(TAG, "Calculating quick dial entries...")

            // Analyze call history to get top contacts with scores
            val topContactsData = analyzeCallHistoryForQuickDial()

            if (topContactsData.isEmpty()) {
                Log.w(TAG, "No call history found, using favorites as fallback")
                val fallbackEntries = getFallbackQuickDialEntries()
                // Cache the fallback entries too
                cachedQuickDialEntries = fallbackEntries
                cacheTimestamp = System.currentTimeMillis()
                return fallbackEntries
            }

            // Fetch all contacts (uses ContactRepository cache)
            val allContacts = contactRepository.getAllContacts()

            // Create QuickDialEntry for each top contact
            val quickDialEntries = topContactsData.mapIndexedNotNull { index, contactData ->
                val contact = allContacts.find { it.id == contactData.contactId }
                if (contact != null) {
                    QuickDialEntry(
                        contact = contact,
                        position = index + 1,
                        score = contactData.score,
                        callCount = contactData.callCount
                    )
                } else {
                    Log.w(TAG, "Contact ${contactData.contactId} not found")
                    null
                }
            }

            // Update cache
            cachedQuickDialEntries = quickDialEntries
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "Successfully created and cached ${quickDialEntries.size} quick dial entries")
            quickDialEntries.forEach {
                Log.d(TAG, "Position ${it.position}: ${it.contact.name} (score: ${it.score}, calls: ${it.callCount})")
            }

            quickDialEntries
        } catch (e: Exception) {
            Log.e(TAG, "Error getting quick dial entries: ${e.message}", e)
            e.printStackTrace()

            // Return cached data if available, even if expired
            cachedQuickDialEntries?.let {
                Log.w(TAG, "Returning stale cached data due to error")
                return it
            }

            emptyList()
        }
    }

    /**
     * Get just the Contact objects for quick dial (for backwards compatibility).
     * @param forceRefresh If true, bypass cache and recalculate
     * @return List of top 4 contacts
     */
    suspend fun getQuickDialContacts(forceRefresh: Boolean = false): List<Contact> {
        return getQuickDialEntries(forceRefresh).map { it.contact }
    }

    /**
     * Analyze call history to determine top contacts with scoring algorithm.
     *
     * Scoring algorithm:
     * - Call frequency: Each call = 1 point
     * - Recency: Recent calls weighted higher (2.0x to 0.5x multiplier)
     * - Call type: Outgoing (1.5x), Incoming (1.2x), Missed (0.5x)
     *
     * @return List of ContactData sorted by score
     */
    private suspend fun analyzeCallHistoryForQuickDial(): List<ContactData> {
        try {
            Log.d(TAG, "Analyzing call history for quick dial...")

            val allCalls = callHistoryRepository.getAllCallHistory()
            val callsWithContacts = allCalls.filter { !it.contactId.isNullOrBlank() }

            if (callsWithContacts.isEmpty()) {
                Log.w(TAG, "No calls with contact IDs found")
                return emptyList()
            }

            // Group calls by contact_id
            val callsByContact = callsWithContacts.groupBy { it.contactId!! }

            // Calculate score for each contact
            val contactScores = callsByContact.map { (contactId, calls) ->
                var score = 0.0

                calls.forEachIndexed { index, call ->
                    // Base score: 1 point per call
                    var callScore = 1.0

                    // Recency multiplier: newer calls weighted higher
                    // Most recent call = 2.0x, gradually decreasing
                    val recencyMultiplier = 2.0 - (index * 0.1).coerceAtMost(1.5)
                    callScore *= recencyMultiplier

                    // Call type weight
                    val typeWeight = when (call.callType.lowercase()) {
                        "outgoing" -> 1.5  // You initiated contact
                        "incoming" -> 1.2  // They contacted you
                        "missed" -> 0.5    // Less important
                        else -> 1.0
                    }
                    callScore *= typeWeight

                    score += callScore
                }

                ContactData(contactId, score, calls.size)
            }

            // Sort by score and take top 4
            val topContacts = contactScores
                .sortedByDescending { it.score }
                .take(QuickDialEntry.MAX_QUICK_DIAL_SLOTS)

            Log.d(TAG, "Top ${topContacts.size} contacts determined")
            return topContacts
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing call history: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Fallback: Get quick dial entries from favorite contacts when no call history exists.
     */
    private suspend fun getFallbackQuickDialEntries(): List<QuickDialEntry> {
        return try {
            val favoriteContacts = contactRepository.getFavoriteContacts()
                .take(QuickDialEntry.MAX_QUICK_DIAL_SLOTS)

            favoriteContacts.mapIndexed { index, contact ->
                QuickDialEntry(
                    contact = contact,
                    position = index + 1,
                    score = 0.0,
                    callCount = 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback entries: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Internal data class for contact scoring.
     */
    private data class ContactData(
        val contactId: String,
        val score: Double,
        val callCount: Int
    )
}
