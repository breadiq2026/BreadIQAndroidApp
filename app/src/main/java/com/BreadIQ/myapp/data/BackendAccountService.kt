package com.BreadIQ.myapp.data

import kotlinx.serialization.json.Json

/**
 * Real [AccountServicing] implementation — `DELETE /api/me`. Kept as its
 * own type (not folded into [BackendTierService]) matching the protocol
 * split [AccountServicing]'s own doc comment already documented: it's
 * specifically the custom-backend seam, distinct from Supabase's own
 * [AuthServicing].
 *
 * Threaded into [com.BreadIQ.myapp.viewmodel.AuthViewModelFactory] the
 * same [BackendApiClient]-with-a-real-`accessTokenProvider` construction
 * [com.BreadIQ.myapp.viewmodel.SubscriptionViewModelFactory] already
 * established for [BackendTierService] — [AuthViewModel.deleteAccount]
 * already calls through to whatever's injected here, unchanged.
 */
class BackendAccountService(private val client: BackendApiClient) : AccountServicing {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun deleteAccount(): Result<Unit> {
        val raw = client.send(path = "/api/me", method = "DELETE")
            ?: return Result.failure(AuthServiceError("Couldn't reach the server. Check your connection and try again."))
        if (raw.statusCode in 200..299) return Result.success(Unit)
        val message = try {
            json.decodeFromString(BackendErrorResponse.serializer(), raw.body).error
        } catch (e: Exception) {
            null
        } ?: "Failed to delete account. Please try again."
        return Result.failure(AuthServiceError(message))
    }
}
