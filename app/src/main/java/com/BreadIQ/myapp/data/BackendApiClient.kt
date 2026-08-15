package com.BreadIQ.myapp.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * The custom BreadIQ backend (`api-server`, mounted at `/api`) — matches
 * the iOS source's `BackendConfig.baseURL`, verified live against the
 * deployed instance there, not just the source's route handlers.
 */
object BackendConfig {
    const val BASE_URL = "https://breadlab.replit.app"
}

/**
 * Minimal Ktor-based HTTP primitive — the Android counterpart of the iOS
 * source's `BackendAPIClient`. Deliberately narrower: only the one
 * unauthenticated `POST` call [BackendImportURLFetcher] needs
 * (`POST /api/import/fetch-url`), not the source's full multi-endpoint
 * client (bearer-token attachment via a live Supabase session, GET
 * support, etc.) — nothing else on Android needs a raw backend REST call
 * yet. Extend this if/when a second real call site shows up, rather than
 * building it out speculatively now.
 *
 * Reuses `io.ktor:ktor-client-android` (already a transitive dependency
 * from the Supabase phase — supabase-kt's own HTTP transport). No Ktor
 * `ContentNegotiation` plugin installed: request/response bodies are
 * encoded/decoded manually via `kotlinx.serialization.json.Json` at each
 * call site instead (see [BackendImportURLFetcher]), since
 * `kotlinx-serialization-json` is already a dependency too and adding
 * `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`
 * just for this one call isn't worth two more dependencies.
 */
object BackendApiClient {
    private val httpClient: HttpClient by lazy { HttpClient(Android) }

    /**
     * `POST` with a JSON body, unauthenticated — matches the source's
     * `authenticated: false` call sites (`/api/import/fetch-url`;
     * `/api/email/audience-sync` doesn't exist on Android). Returns the
     * raw response text on success, `null` on a network-level failure —
     * no status-code interpretation here, matching the source's own
     * `RawResponse`-then-caller-interprets shape (the route this backs
     * always responds 200 even on a logical failure, so status code
     * isn't actually meaningful for this call site — see
     * [BackendImportURLFetcher]'s own doc comment).
     */
    suspend fun postJson(path: String, bodyJson: String): String? = try {
        httpClient.post("${BackendConfig.BASE_URL}$path") {
            contentType(ContentType.Application.Json)
            setBody(bodyJson)
        }.bodyAsText()
    } catch (e: Exception) {
        null
    }
}
