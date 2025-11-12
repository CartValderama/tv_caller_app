package com.example.tv_caller_app.calling.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.model.ProfilePresenceUpdate
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Repository for managing online presence.
 * Handles heartbeat, online status, and WebRTC availability.
 *
 * Singleton pattern to ensure single instance.
 */
class PresenceRepository private constructor(
    private val sessionManager: SessionManager
) {
    private val TAG = "PresenceRepository"
    private val supabase = SupabaseClient.client
    private val scope = CoroutineScope(Dispatchers.IO)

    // Heartbeat job
    private var heartbeatJob: Job? = null
    private val HEARTBEAT_INTERVAL_MS = 30000L // 30 seconds

    companion object {
        @Volatile
        private var instance: PresenceRepository? = null

        fun getInstance(sessionManager: SessionManager): PresenceRepository {
            return instance ?: synchronized(this) {
                instance ?: PresenceRepository(sessionManager).also { instance = it }
            }
        }
    }

    /**
     * Set user online.
     * Starts heartbeat to maintain online status.
     *
     * @param deviceType Type of device (tv, phone, tablet)
     */
    suspend fun setOnline(deviceType: String = "tv"): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.i(TAG, "Setting user online: $userId")

            // Update profile
            val update = ProfilePresenceUpdate(
                isOnline = true,
                lastSeen = Instant.now().toString(),
                webrtcStatus = "available",
                deviceType = deviceType
            )

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            // Start heartbeat
            startHeartbeat()

            Log.i(TAG, "User set online successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user online", e)
            Result.failure(e)
        }
    }

    /**
     * Set user offline.
     * Stops heartbeat.
     */
    suspend fun setOffline(): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.i(TAG, "Setting user offline: $userId")

            // Stop heartbeat
            stopHeartbeat()

            // Update profile
            val update = ProfilePresenceUpdate(
                isOnline = false,
                lastSeen = Instant.now().toString(),
                webrtcStatus = "offline"
            )

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            Log.i(TAG, "User set offline successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user offline", e)
            Result.failure(e)
        }
    }

    /**
     * Update WebRTC status.
     *
     * @param status available, in_call, busy, offline
     */
    suspend fun updateWebRTCStatus(status: String): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.d(TAG, "Updating WebRTC status: $status")

            val update = buildJsonObject {
                put("webrtc_status", status)
                put("last_seen", Instant.now().toString())
            }

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WebRTC status", e)
            Result.failure(e)
        }
    }

    /**
     * Get online users.
     *
     * @return List of online profiles
     */
    suspend fun getOnlineUsers(): Result<List<Profile>> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.d(TAG, "Fetching online users...")

            val profiles = supabase.from("profiles")
                .select(columns = Columns.list("*")) {
                    filter {
                        eq("is_online", true)
                        neq("id", userId) // Exclude self
                    }
                }
                .decodeList<Profile>()

            Log.d(TAG, "Found ${profiles.size} online users")
            Result.success(profiles)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch online users", e)
            Result.failure(e)
        }
    }

    /**
     * Get user presence by ID.
     *
     * @param userId User ID to check
     * @return Profile with presence info
     */
    suspend fun getUserPresence(userId: String): Result<Profile> {
        return try {
            Log.d(TAG, "Fetching presence for user: $userId")

            val profile = supabase.from("profiles")
                .select(columns = Columns.list("*")) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<Profile>()

            Result.success(profile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user presence", e)
            Result.failure(e)
        }
    }

    /**
     * Start heartbeat to maintain online status.
     */
    private fun startHeartbeat() {
        stopHeartbeat() // Stop any existing heartbeat

        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    delay(HEARTBEAT_INTERVAL_MS)

                    val userId = sessionManager.getUserId() ?: break

                    // Update last_seen
                    val update = buildJsonObject {
                        put("last_seen", Instant.now().toString())
                    }

                    supabase.from("profiles")
                        .update(update) {
                            filter {
                                eq("id", userId)
                            }
                        }

                    Log.d(TAG, "Heartbeat sent")

                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
            }
        }

        Log.i(TAG, "Heartbeat started")
    }

    /**
     * Stop heartbeat.
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "Heartbeat stopped")
    }

    /**
     * Cleanup on logout.
     */
    suspend fun cleanup() {
        setOffline()
        instance = null
    }
}
