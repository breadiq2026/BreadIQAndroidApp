package com.BreadIQ.myapp.core

import java.time.Instant

/**
 * Ported from the iOS app's `Core/BackendIngredientCostSyncService.swift`'s
 * protocol + supporting types.
 *
 * `GET /api/reference-prices`, `GET/PUT/DELETE /api/ingredient-costs` —
 * verified live against the deployed server, not just the route source.
 * No STUB-BACKEND boundary here, unlike [PairingCodeGenerating] — these
 * endpoints exist and work today. `ingredientType` on the wire is this
 * app's `ingredientKey` ([com.BreadIQ.myapp.model.IngredientPriceOverride.ingredientKey])
 * — same value space as [com.BreadIQ.myapp.model.IngredientReferencePriceCatalog]'s
 * own `key` field, different name.
 *
 * Same "interface + error type + Unconfigured stub bundled in one file"
 * convention this codebase already uses for
 * [com.BreadIQ.myapp.data.AuthServicing]/[PurchasesServicing]/
 * [PairingCodeGenerating].
 */
interface IngredientCostSyncing {
    /**
     * Surfaces both the price values themselves AND their timestamp in
     * one call — not timestamp-only. `null` on any failure (network
     * error, non-2xx, decode failure): this endpoint is unauthenticated
     * public reference data, so a failure here just means the info/stale
     * banner and the live-reference-price layer silently fall back to
     * their bundled-catalog defaults, same "show nothing rather than an
     * error state" behavior as every other purely-cosmetic fetch in this
     * app.
     */
    suspend fun fetchReferencePrices(): ReferencePricesFetch?

    suspend fun fetchOverrides(): Result<Map<String, Double>>

    suspend fun setOverride(key: String, pricePerGram: Double): Result<Unit>

    suspend fun deleteOverride(key: String): Result<Unit>
}

/**
 * `ingredientType` → `pricePerGram`, keyed the same way
 * [com.BreadIQ.myapp.model.IngredientReferencePriceCatalog]/
 * [com.BreadIQ.myapp.core.CostEstimator.ingredientReferencePrices]
 * already are, so callers can drop this straight into the same
 * `refPrices`/lookup shape those already use. [updatedAt] is the max
 * value across every row in the response.
 */
data class ReferencePricesFetch(
    val prices: Map<String, Double>,
    val updatedAt: Instant?,
)

/** Matches this codebase's own [com.BreadIQ.myapp.data.AuthServiceError]/[PairingCodeError] shape — an [Exception] subtype carried inside a [Result] failure. */
class IngredientCostSyncError(message: String) : Exception(message)

class UnconfiguredIngredientCostSyncService : IngredientCostSyncing {
    override suspend fun fetchReferencePrices(): ReferencePricesFetch? = null
    override suspend fun fetchOverrides(): Result<Map<String, Double>> = Result.failure(
        IngredientCostSyncError("Ingredient cost sync isn't available yet — check back once the account system is finished.")
    )
    override suspend fun setOverride(key: String, pricePerGram: Double): Result<Unit> = Result.failure(
        IngredientCostSyncError("Ingredient cost sync isn't available yet — check back once the account system is finished.")
    )
    override suspend fun deleteOverride(key: String): Result<Unit> = Result.failure(
        IngredientCostSyncError("Ingredient cost sync isn't available yet — check back once the account system is finished.")
    )
}
