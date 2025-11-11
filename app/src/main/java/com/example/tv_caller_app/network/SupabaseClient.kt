package com.example.tv_caller_app.network

import com.example.tv_caller_app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Singleton object to manage Supabase client instance.
 * Configured with Postgrest, Auth, and Realtime.
 *
 * Credentials are loaded from BuildConfig (local.properties) to keep them secure.
 */
object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        // Use OkHttp engine for WebSocket support (required for Realtime)
        httpEngine = OkHttp.create()

        install(Postgrest)
        install(Auth)
        install(Realtime)  // For WebRTC signaling via WebSockets
    }
}
