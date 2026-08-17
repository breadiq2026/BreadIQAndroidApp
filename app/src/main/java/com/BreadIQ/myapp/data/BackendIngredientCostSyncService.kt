package com.BreadIQ.myapp.data

import android.net.Uri
import com.BreadIQ.myapp.core.IngredientCostSyncError
import com.BreadIQ.myapp.core.IngredientCostSyncing
import com.BreadIQ.myapp.core.ReferencePricesFetch
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Real [IngredientCostSyncing] implementation. `GET /api/reference-prices`,
 * `GET/PUT/DELETE /api/ingredient-costs` — verified live against the
 * deployed server, not stubbed.
 */
class BackendIngredientCostSyncService(private val client: BackendApiClient) : IngredientCostSyncing {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ReferencePriceRow(val ingredientType: String, val pricePerGram: Double, val updatedAt: String, val notes: String? = null)

    @Serializable
    private data class OverrideRow(val ingredientType: String, val pricePerGram: Double, val updatedAt: String)

    @Serializable
    private data class SetOverrideRequest(val pricePerGram: Double)

    override suspend fun fetchReferencePrices(): ReferencePricesFetch? {
        val raw = client.send(path = "/api/reference-prices", method = "GET", authenticated = false) ?: return null
        if (raw.statusCode !in 200..299) return null
        val rows = try {
            json.decodeFromString(ListSerializer(ReferencePriceRow.serializer()), raw.body)
        } catch (e: Exception) {
            return null
        }
        val prices = rows.associate { it.ingredientType to it.pricePerGram }
        val updatedAt = rows.mapNotNull { parseInstant(it.updatedAt) }.maxOrNull()
        return ReferencePricesFetch(prices = prices, updatedAt = updatedAt)
    }

    override suspend fun fetchOverrides(): Result<Map<String, Double>> {
        val raw = client.send(path = "/api/ingredient-costs", method = "GET")
            ?: return Result.failure(IngredientCostSyncError("Couldn't reach the server. Check your connection and try again."))
        if (raw.statusCode !in 200..299) {
            return Result.failure(IngredientCostSyncError("Something went wrong. Please try again."))
        }
        val rows = try {
            json.decodeFromString(ListSerializer(OverrideRow.serializer()), raw.body)
        } catch (e: Exception) {
            return Result.failure(IngredientCostSyncError("Something went wrong. Please try again."))
        }
        return Result.success(rows.associate { it.ingredientType to it.pricePerGram })
    }

    override suspend fun setOverride(key: String, pricePerGram: Double): Result<Unit> {
        val path = "/api/ingredient-costs/${Uri.encode(key)}"
        val bodyJson = json.encodeToString(SetOverrideRequest.serializer(), SetOverrideRequest(pricePerGram))
        val raw = client.send(path = path, method = "PUT", bodyJson = bodyJson)
            ?: return Result.failure(IngredientCostSyncError("Couldn't reach the server. Check your connection and try again."))
        if (raw.statusCode in 200..299) return Result.success(Unit)
        val message = try {
            json.decodeFromString(BackendErrorResponse.serializer(), raw.body).error
        } catch (e: Exception) {
            null
        } ?: "Something went wrong. Please try again."
        return Result.failure(IngredientCostSyncError(message))
    }

    override suspend fun deleteOverride(key: String): Result<Unit> {
        val path = "/api/ingredient-costs/${Uri.encode(key)}"
        val raw = client.send(path = path, method = "DELETE")
            ?: return Result.failure(IngredientCostSyncError("Couldn't reach the server. Check your connection and try again."))
        if (raw.statusCode in 200..299) return Result.success(Unit)
        return Result.failure(IngredientCostSyncError("Something went wrong. Please try again."))
    }

    private fun parseInstant(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        null
    }
}
