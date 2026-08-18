package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.core.ImportInboxFetchError
import com.BreadIQ.myapp.core.ImportInboxFetchOutcome
import com.BreadIQ.myapp.core.ImportInboxFetching
import com.BreadIQ.myapp.core.StagedImportListItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Real [ImportInboxFetching] implementation — `GET /api/import/staged`
 * (list, no token in the path), the Chrome-extension companion's
 * pending-imports inbox. Authenticated the same way
 * [BackendImportStagingFetcher] already is. The response is a plain JSON
 * array (`[{ token, recipeName?, sourceUrl? }]`, matching iOS's
 * `BackendImportInboxFetcher.ItemDTO`) — extra/unrecognized fields
 * (confidence/flags/ingredient count) decode fine and are simply ignored.
 */
class BackendImportInboxFetcher(private val client: BackendApiClient) : ImportInboxFetching {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ItemDto(
        val token: String,
        val recipeName: String? = null,
        val sourceUrl: String? = null,
    )

    override suspend fun fetchPendingImports(): ImportInboxFetchOutcome {
        val raw = client.send(path = "/api/import/staged", method = "GET")
            ?: return ImportInboxFetchOutcome.Failure(ImportInboxFetchError("Couldn't reach the server. Check your connection and try again."))

        if (raw.statusCode !in 200..299) {
            val message = try {
                json.decodeFromString(BackendErrorResponse.serializer(), raw.body).error
            } catch (e: Exception) {
                null
            } ?: "Import list isn't available yet — check back once the backend is finished."
            return ImportInboxFetchOutcome.Failure(ImportInboxFetchError(message))
        }

        val decoded = try {
            json.decodeFromString(ListSerializer(ItemDto.serializer()), raw.body)
        } catch (e: Exception) {
            return ImportInboxFetchOutcome.Failure(ImportInboxFetchError("Failed to load pending imports."))
        }

        return ImportInboxFetchOutcome.Success(
            decoded.map { StagedImportListItem(token = it.token, recipeName = it.recipeName, sourceURL = it.sourceUrl) },
        )
    }
}
