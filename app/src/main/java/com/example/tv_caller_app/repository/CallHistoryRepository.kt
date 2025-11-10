package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.CallHistory
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from

/**
 * Repository class for handling call history data operations.
 * Implements in-memory caching to reduce network calls.
 * Singleton pattern ensures single cache instance across app.
 * Filters all data by authenticated user ID.
 */
class CallHistoryRepository private constructor(
    private val sessionManager: SessionManager
) {

    private val supabase = SupabaseClient.client
    private val TAG = "CallHistoryRepository"

    // In-memory cache
    private var cachedCallHistory: List<CallHistory>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    companion object {
        @Volatile
        private var instance: CallHistoryRepository? = null

        fun getInstance(sessionManager: SessionManager): CallHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: CallHistoryRepository(sessionManager).also { instance = it }
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
        return cachedCallHistory != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    /**
     * Invalidate the cache manually.
     * Call this when new calls are made or call history is modified.
     */
    fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedCallHistory = null
        cacheTimestamp = 0
    }

    /**
     * Fetch all call history records for authenticated user, ordered by timestamp (most recent first).
     * Uses in-memory cache if available and valid.
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of call history records
     */
    suspend fun getAllCallHistory(forceRefresh: Boolean = false): List<CallHistory> {
        // Return cached data if valid and not forcing refresh
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached call history (${cachedCallHistory!!.size} records)")
            return cachedCallHistory!!
        }

        // Fetch from Supabase
        return try {
            val userId = getUserId()
            Log.d(TAG, "Fetching all call history for user: ${userId.take(8)}...")

            val callHistory = supabase.from("call_history")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<CallHistory>()
                .sortedByDescending { it.callTimestamp }

            // Update cache
            cachedCallHistory = callHistory
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "Successfully fetched and cached ${callHistory.size} call records")
            callHistory
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history: ${e.message}", e)
            e.printStackTrace()

            // Return cached data if available, even if expired
            cachedCallHistory?.let {
                Log.w(TAG, "Returning stale cached data due to network error")
                return it
            }

            emptyList()
        }
    }

    /**
     * Fetch recent call history with a limit.
     * Uses cached data if available.
     * @param limit Maximum number of records to return
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of recent call history records
     */
    suspend fun getRecentCallHistory(limit: Int = 10, forceRefresh: Boolean = false): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting recent $limit call history records...")
            val allCalls = getAllCallHistory(forceRefresh)
            val recentCalls = allCalls.take(limit)
            Log.d(TAG, "Successfully got ${recentCalls.size} recent call records")
            recentCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent call history: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch call history filtered by call type.
     * Uses cached data if available.
     * @param callType Type of call: "incoming", "outgoing", or "missed"
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of call history records of specified type
     */
    suspend fun getCallHistoryByType(callType: String, forceRefresh: Boolean = false): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting call history of type '$callType'...")
            val allCalls = getAllCallHistory(forceRefresh)
            val filteredCalls = allCalls.filter { it.callType.lowercase() == callType.lowercase() }
            Log.d(TAG, "Successfully got ${filteredCalls.size} '$callType' call records")
            filteredCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history by type: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch call history for a specific contact.
     * Uses cached data if available.
     * @param contactId ID of the contact
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of call history records for the contact
     */
    suspend fun getCallHistoryForContact(contactId: String, forceRefresh: Boolean = false): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting call history for contact $contactId...")
            val allCalls = getAllCallHistory(forceRefresh)
            val contactCalls = allCalls.filter { it.contactId == contactId }
            Log.d(TAG, "Successfully got ${contactCalls.size} call records for contact")
            contactCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history for contact: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get call statistics summary.
     * Uses cached data if available.
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return Map with call type counts
     */
    suspend fun getCallStatistics(forceRefresh: Boolean = false): Map<String, Int> {
        return try {
            Log.d(TAG, "Calculating call statistics...")
            val allCalls = getAllCallHistory(forceRefresh)
            val stats = mapOf(
                "total" to allCalls.size,
                "incoming" to allCalls.count { it.callType.lowercase() == "incoming" },
                "outgoing" to allCalls.count { it.callType.lowercase() == "outgoing" },
                "missed" to allCalls.count { it.callType.lowercase() == "missed" }
            )
            Log.d(TAG, "Call statistics: $stats")
            stats
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating call statistics: ${e.message}", e)
            emptyMap()
        }
    }
}
