package com.example.tv_caller_app.network

import com.example.tv_caller_app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Singleton object to manage Supabase client instance.
 * Configured with Postgrest for database operations and Auth for authentication.
 *
 * Credentials are loaded from BuildConfig (local.properties) to keep them secure.
 */
object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Auth)
    }
}
