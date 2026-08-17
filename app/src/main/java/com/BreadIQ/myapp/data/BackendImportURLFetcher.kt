package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.core.ImportURLFetchError
import com.BreadIQ.myapp.core.ImportURLFetchOutcome
import com.BreadIQ.myapp.core.ImportURLFetchResult
import com.BreadIQ.myapp.core.ImportURLFetching
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ImportUrlFetchRequestDto(val url: String)

@Serializable
private data class ImportUrlFetchResponseDto(
    val ingredients: List<String>? = null,
    val confidence: String? = null,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Real [ImportURLFetching] implementation — `POST /api/import/fetch-url`.
 * Unauthenticated, matching the source's `ImportModal.tsx`'s own plain
 * `fetch` (not `authFetch`) for this call — the route scrapes
 * third-party HTML server-side with no need for the caller's identity.
 *
 * **The route always responds 200 even on failure** (`{ error, message }`
 * with no ingredients) — verified directly against `import.ts`'s
 * `/fetch-url` handler by the iOS port, not assumed from the client
 * side. Only genuinely malformed requests (missing/invalid `url`) get a
 * real 400. Both shapes decode into the same lenient DTO below rather
 * than needing separate success/failure response types.
 *
 * Builds its own unauthenticated [BackendApiClient] (the class's default
 * no-token provider) — this call site never needed authentication and
 * still doesn't, even now that [BackendApiClient] supports it.
 */
object BackendImportURLFetcher : ImportURLFetching {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = BackendApiClient()

    override suspend fun fetchIngredients(url: String): ImportURLFetchOutcome {
        val requestBody = json.encodeToString(ImportUrlFetchRequestDto.serializer(), ImportUrlFetchRequestDto(url))
        val rawResponse = client.send(path = "/api/import/fetch-url", method = "POST", bodyJson = requestBody, authenticated = false)
            ?: return ImportURLFetchOutcome.Failure(ImportURLFetchError("Couldn't reach the server. Check your connection and try again."))

        val decoded = try {
            json.decodeFromString(ImportUrlFetchResponseDto.serializer(), rawResponse.body)
        } catch (e: Exception) {
            return ImportURLFetchOutcome.Failure(ImportURLFetchError("Something went wrong. Please try again."))
        }

        val ingredients = decoded.ingredients
        val confidence = decoded.confidence
        if (ingredients != null && confidence != null) {
            return ImportURLFetchOutcome.Success(ImportURLFetchResult(ingredients, confidence))
        }
        return ImportURLFetchOutcome.Failure(ImportURLFetchError(decoded.message ?: decoded.error ?: "Something went wrong. Please try again."))
    }
}
