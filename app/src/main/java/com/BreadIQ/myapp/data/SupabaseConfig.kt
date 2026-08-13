package com.BreadIQ.myapp.data

/**
 * Ported from the iOS app's `Core/SupabaseConfig.swift`.
 *
 * Matches `lib/supabase.ts`'s `EXPO_PUBLIC_SUPABASE_URL`/
 * `EXPO_PUBLIC_SUPABASE_ANON_KEY` env vars from the original Expo app —
 * both are meant to ship inside the client binary (the anon key is
 * specifically the publishable key protected by this project's Postgres
 * Row Level Security policies, not a secret credential). Same values as
 * the iOS app — this is the same Supabase project, same backend.
 */
object SupabaseConfig {
    const val url = "https://gxvrqxherrqdqoeacyut.supabase.co"
    const val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd4dnJxeGhlcnJxZHFvZWFjeXV0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgxOTI4MTEsImV4cCI6MjA5Mzc2ODgxMX0.H057S1jUfETGP6bhneNG10p4LFd4gXOr8WNTLUdo3t0"
}
