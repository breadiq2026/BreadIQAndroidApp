package com.BreadIQ.myapp.data

import android.net.Uri
import com.BreadIQ.myapp.core.ImportStagingFetching
import com.BreadIQ.myapp.core.IngredientCategory
import com.BreadIQ.myapp.core.StagedImportFetchError
import com.BreadIQ.myapp.core.StagedImportFetchOutcome
import com.BreadIQ.myapp.core.StagedImportIngredient
import com.BreadIQ.myapp.core.StagedImportPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real [ImportStagingFetching] implementation — `GET /api/import/staged/:token`,
 * the Safari-extension-equivalent deep-link handoff. Authenticated
 * (matching the source's own `authFetch` call for this route even though
 * the handler itself doesn't check the caller's identity — matching the
 * source exactly rather than second-guessing it). The row this reads was
 * written by the browser extension and is atomically consumed (deleted)
 * server-side on fetch, so this can only ever succeed once per token.
 *
 * `category` decodes as a raw `String` first, not straight into
 * [IngredientCategory] — an unrecognized value degrades to
 * [IngredientCategory.UNKNOWN] instead of failing the whole decode,
 * matching the source's own defensive `IngredientCategory(rawValue:) ??
 * .unknown`.
 */
class BackendImportStagingFetcher(private val client: BackendApiClient) : ImportStagingFetching {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class IngredientDto(
        val name: String,
        val category: String,
        val grams: Double,
        val flagged: String? = null,
    )

    @Serializable
    private data class ResponseDto(
        val recipeName: String? = null,
        val sourceUrl: String? = null,
        val ingredients: List<IngredientDto>,
        val confidence: String,
        val flags: List<String>,
    )

    override suspend fun fetchStagedImport(token: String): StagedImportFetchOutcome {
        val path = "/api/import/staged/${Uri.encode(token)}"
        val raw = client.send(path = path, method = "GET")
            ?: return StagedImportFetchOutcome.Failure(StagedImportFetchError("Couldn't reach the server. Check your connection and try again."))

        if (raw.statusCode !in 200..299) {
            val message = try {
                json.decodeFromString(BackendErrorResponse.serializer(), raw.body).error
            } catch (e: Exception) {
                null
            } ?: "Failed to load import."
            return StagedImportFetchOutcome.Failure(StagedImportFetchError(message))
        }

        val decoded = try {
            json.decodeFromString(ResponseDto.serializer(), raw.body)
        } catch (e: Exception) {
            return StagedImportFetchOutcome.Failure(StagedImportFetchError("Failed to load import."))
        }

        val payload = StagedImportPayload(
            recipeName = decoded.recipeName,
            sourceURL = decoded.sourceUrl,
            ingredients = decoded.ingredients.map { dto ->
                StagedImportIngredient(
                    name = dto.name,
                    category = IngredientCategory.entries.firstOrNull { it.rawValue == dto.category } ?: IngredientCategory.UNKNOWN,
                    grams = dto.grams,
                    flagged = dto.flagged,
                )
            },
            confidence = decoded.confidence,
            flags = decoded.flags,
        )
        return StagedImportFetchOutcome.Success(payload)
    }
}
