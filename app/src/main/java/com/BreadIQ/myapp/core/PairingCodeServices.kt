package com.BreadIQ.myapp.core

import java.time.Instant

// MARK: - Browser pairing — POST /auth/pairing-code/generate

/**
 * Ported from the iOS app's `Core/PairingCodeServices.swift`.
 *
 * The code shown on `ConnectBrowserScreen` for a user to type into the
 * Chrome extension's own pairing form (`BreadIQChromeExtension/auth.js`'s
 * `redeemPairingCode`) — the counterpart to that extension's
 * `POST /auth/pairing-code/redeem`. Short-lived by design: [expiresAt]
 * drives the countdown the screen shows, matching the Chrome-extension
 * discovery memo's "poll on foreground, not push" v1 scope — pairing
 * itself still needs to feel immediate even though staged-import pickup
 * doesn't.
 *
 * `expiresAt` is a real [Instant], not a formatted string — the countdown
 * UI needs real time math ([ConnectBrowserFormatting.countdownText]).
 */
data class PairingCode(val code: String, val expiresAt: Instant)

/** Matches this codebase's own `AuthServiceError` shape — an [Exception] subtype carried inside a [Result] failure, not a separate sealed error type. */
class PairingCodeError(message: String) : Exception(message)

/**
 * Seam for the "Connect a Browser" flow. ██ STUB-BACKEND ██ —
 * `POST /api/auth/pairing-code/generate` does not exist on `api-server`
 * yet (that source lives outside this repo, deployed at
 * `breadlab.replit.app`). [BackendPairingCodeGenerator][com.BreadIQ.myapp.data.BackendPairingCodeGenerator]
 * is written against the agreed contract (`{ code, expiresAt }`) and is
 * ready to work the moment the route ships; every call fails with a
 * real, honest error until then, same as this file's own
 * [UnconfiguredPairingCodeGenerator] would. Grep this codebase for
 * `STUB-BACKEND` to find every call site this feature touches.
 */
interface PairingCodeGenerating {
    suspend fun generateCode(): Result<PairingCode>
}

class UnconfiguredPairingCodeGenerator : PairingCodeGenerating {
    override suspend fun generateCode(): Result<PairingCode> = Result.failure(
        PairingCodeError("Browser pairing isn't available yet — check back once the backend is finished.")
    )
}
