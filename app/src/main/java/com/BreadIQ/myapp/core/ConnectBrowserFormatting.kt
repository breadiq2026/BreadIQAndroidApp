package com.BreadIQ.myapp.core

import java.time.Duration
import java.time.Instant

/**
 * Ported from the iOS app's `Screens/ConnectBrowserScreen.swift`'s
 * `ConnectBrowserFormatting` enum.
 *
 * Pure formatting/timing helpers for `ConnectBrowserScreen` — split out
 * the same way every other screen in this codebase separates testable
 * logic from view code ([SettingsTierPresentation], etc.).
 */
object ConnectBrowserFormatting {
    /** "4:32" — minutes:seconds remaining, clamped to "0:00" at/after expiry rather than going negative. */
    fun countdownText(expiresAt: Instant, now: Instant): String {
        val remaining = maxOf(0, Duration.between(now, expiresAt).seconds)
        val minutes = remaining / 60
        val seconds = remaining % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun isExpired(expiresAt: Instant, now: Instant): Boolean = !now.isBefore(expiresAt)

    /**
     * Cosmetic grouping only (`"ABCD1234"` → `"ABCD-1234"`), matching the
     * placeholder shape the Chrome extension's own pairing input shows
     * (`BreadIQChromeExtension/popup.html`'s `XXXX-XXXX`). Never applied
     * to what's actually sent over the wire — `redeemPairingCode` in the
     * extension strips it back out before submitting.
     */
    fun displayCode(raw: String): String {
        val stripped = raw.uppercase().filter { it.isLetterOrDigit() }
        if (stripped.length <= 4) return stripped
        return "${stripped.substring(0, 4)}-${stripped.substring(4)}"
    }
}
