package com.BreadIQ.myapp.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds/holds the single app-wide [SupabaseClient], installing the Auth
 * and Postgrest plugins per PORTING_PLAN.md step 2. There's no iOS
 * equivalent of this specific file — the iOS port deliberately avoids the
 * official Supabase Swift SDK (see `SupabaseAuthService.swift`'s own doc
 * comment) and talks to GoTrue via hand-rolled `URLSession` calls instead,
 * so it has no single client object to construct. This port uses the
 * official Kotlin Multiplatform client per this plan's own direction, so
 * this file is the composition root that would otherwise be scattered
 * across `SupabaseConfig`/`SupabaseAuthService` on iOS.
 *
 * A simple double-checked-locking singleton rather than a DI framework —
 * this app has no DI setup yet, and one client for the process lifetime
 * is all that's needed.
 */
object SupabaseClientProvider {
    @Volatile
    private var instance: SupabaseClient? = null

    fun getInstance(context: Context): SupabaseClient =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.url,
        supabaseKey = SupabaseConfig.anonKey,
    ) {
        install(Auth) {
            sessionManager = KeystoreSessionManager(appContext)
        }
        install(Postgrest)
    }
}
