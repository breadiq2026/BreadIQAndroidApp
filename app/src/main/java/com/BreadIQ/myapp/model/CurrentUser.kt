package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/CurrentUser.swift`.
 *
 * A lightweight, non-secret snapshot of "who's currently signed in."
 * Deliberately excludes the actual session/JWT material (access token,
 * refresh token, expiry) — that belongs in Android's equivalent of Keychain
 * (EncryptedSharedPreferences / the Keystore-backed credential store),
 * never in this plain model or any other queryable/exportable store.
 *
 * Ground truth (per the iOS port): `context/AuthContext.tsx` in the
 * original Expo app. The source's `session`/`user` values are the full
 * `Session`/`User` types from `@supabase/supabase-js` itself (not custom
 * BreadIQ types) — these carry the actual bearer tokens plus a large
 * surface of Supabase-internal fields this app has no reason to
 * duplicate. This class captures only the fields actually read elsewhere
 * in the app: RevenueCat login binding uses `user.id`, Settings displays
 * `user.email`, and the bake-screen greeting displays the cached display
 * name.
 */
data class CurrentUser(
    val id: String,
    val email: String? = null,
    val displayName: String? = null,
)
