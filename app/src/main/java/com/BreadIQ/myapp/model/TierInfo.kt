package com.BreadIQ.myapp.model

import kotlinx.serialization.Serializable

/**
 * Ported from the iOS app's `Models/TierInfo.swift`.
 *
 * The user's current subscription tier and trial status.
 *
 * Modeling decision (matches the iOS port): this is NOT user-owned,
 * user-editable data — the user never creates, edits, or deletes a
 * `TierInfo`. It's a read-only "current status" snapshot with two
 * possible sources, checked in order in the source app:
 *   1. RevenueCat's SDK, which already handles its own on-device caching
 *      internally — BreadIQ doesn't need to duplicate that persistence.
 *   2. A network call to `GET /api/me/tier` (only reached if RevenueCat
 *      reports "free" — a paid RC entitlement always wins outright), with
 *      no local cache today.
 * Since neither source is something the user writes to directly, this is
 * modeled as a plain value type — matching `FormulaResult`/
 * `ProofTimeResult`'s treatment of "a value fetched/computed fresh, not
 * independently persisted."
 *
 * `@Serializable` — [com.BreadIQ.myapp.data.BackendTierService] decodes
 * `GET /api/me/tier`'s response directly into this type; field names
 * match the wire response exactly, confirmed against the source, no
 * mapping/DTO layer needed.
 */
@Serializable
data class TierInfo(
    /**
     * Kept as a plain `String` rather than a `free`/`basic`/`premium`
     * enum, matching the iOS port's reasoning: the server's
     * `computeEffectiveTier()` widens it to plain `string`
     * (`(record?.tier ?? "free") as string`), and every consumer in the
     * source app treats an unrecognized tier as "not found" via graceful
     * fallback, not a hard error. A strict enum would throw/fail to parse
     * on decode for any unexpected server value instead of degrading
     * gracefully like the source does — worth revisiting as a
     * non-throwing lookup helper at consumption sites rather than baking
     * strictness into the wire model itself.
     */
    val tier: String,
    val trialActive: Boolean,
    val trialExpiresAt: String? = null,
    val trialDaysRemaining: Int? = null,
)
