package com.BreadIQ.myapp.data

/**
 * Ported from the iOS app's `Core/AuthErrorHumanizer.swift`.
 *
 * Maps a couple of raw Supabase Auth error messages to friendlier copy
 * for the sign-in screen.
 *
 * **Only applied to the sign-in error path**, matching the iOS port's own
 * note (traced there to the original Expo app's `auth.tsx`, whose
 * `handleSubmit` sign-up branch shows `res.error` raw, unhumanized — only
 * sign-in wraps it). That's a call-site decision, not something baked
 * into this function — `AuthScreen` applies it only to the sign-in path,
 * not to sign-up errors.
 */
object AuthErrorHumanizer {
    /** Case-insensitive substring match, first rule wins; falls back to the raw message unchanged if nothing matches. */
    fun humanize(message: String): String {
        val lower = message.lowercase()
        return when {
            "invalid login" in lower -> "Incorrect email or password."
            "email not confirmed" in lower -> "Check your email to confirm your account."
            else -> message
        }
    }
}
