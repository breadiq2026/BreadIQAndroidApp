package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.core.TierServicing
import com.BreadIQ.myapp.model.TierInfo
import kotlinx.serialization.json.Json

/**
 * Real [TierServicing] implementation — `GET /api/me/tier`, the fallback
 * `useUserTier` reaches for when RevenueCat itself reports free.
 * Response shape verified directly against the iOS port's own
 * confirmation (`me.ts`'s `computeEffectiveTier()`: `{ tier,
 * trialActive, trialExpiresAt, trialDaysRemaining }`) — matches
 * [TierInfo] field-for-field, no mapping needed.
 *
 * Authenticated — [client] is expected to be constructed with a real
 * [SupabaseAuthService.currentAccessToken]-backed token provider (see
 * `SubscriptionViewModelFactory`'s own construction site), matching the
 * source's own `authFetch` (not the plain `fetch`
 * [BackendImportURLFetcher] uses) for this call.
 */
class BackendTierService(private val client: BackendApiClient) : TierServicing {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchTier(): TierInfo? {
        val raw = client.send(path = "/api/me/tier", method = "GET") ?: return null
        if (raw.statusCode !in 200..299) return null
        return try {
            json.decodeFromString(TierInfo.serializer(), raw.body)
        } catch (e: Exception) {
            null
        }
    }
}
