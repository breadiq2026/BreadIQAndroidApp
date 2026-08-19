package com.BreadIQ.myapp.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * The custom BreadIQ backend (`api-server`, mounted at `/api`), now
 * hosted on Vercel (migrated 2026-08-18, see the Android port status doc).
 * iOS's main app and both browser extensions were repointed here too as
 * part of the v1.5 cutover (2026-08-19) — the old Replit host
 * (`breadlab.replit.app`) is no longer used by any client, though it's
 * being left running until v1.5 has shipped and adoption is confirmed.
 */
object BackendConfig {
    const val BASE_URL = "https://breadiqapi.vercel.app"
}

/**
 * Generic low-level HTTP primitive shared by every `Backend*Service` —
 * the Android counterpart of the iOS source's `BackendAPIClient`.
 * Originally deliberately narrower (a single unauthenticated `POST`
 * helper, `postJson`, for [BackendImportURLFetcher] only — see that
 * era's own doc comment); extended here for
 * [com.BreadIQ.myapp.core.RevenueCatPurchasesService]'s `GET /api/me/tier`
 * call, which needs both GET support and a bearer token attached, plus
 * status-code-aware responses (that route's caller has to check
 * `200..299` before decoding, unlike `/fetch-url`, which always
 * responds 200 even on a logical failure).
 *
 * A real `class` now, not an `object` — [accessTokenProvider] is
 * constructor-injected per call site, matching the source's own
 * `BackendAPIClient(accessTokenProvider: { await auth.currentAccessToken() })`
 * construction exactly, rather than a single app-wide singleton that
 * can't vary its auth behavior per caller. [BackendImportURLFetcher]
 * builds its own unauthenticated instance (`BackendApiClient()`, the
 * default no-token provider) internally; [BackendTierService] is built
 * with a real [com.BreadIQ.myapp.data.SupabaseAuthService.currentAccessToken]-backed
 * provider at its own construction site.
 *
 * No Ktor `ContentNegotiation` plugin installed: request/response bodies
 * are encoded/decoded manually via `kotlinx.serialization.json.Json` at
 * each call site instead, since `kotlinx-serialization-json` is already
 * a dependency and adding `ktor-client-content-negotiation` +
 * `ktor-serialization-kotlinx-json` isn't worth two more dependencies
 * for this app's still-small set of raw REST calls.
 */
class BackendApiClient(private val accessTokenProvider: suspend () -> String? = { null }) {

    private val httpClient: HttpClient by lazy { HttpClient(Android) }

    data class RawResponse(val statusCode: Int, val body: String)

    /**
     * `send(path:method:body:authenticated:)`. Returns `null` on a
     * network-level failure only — a real HTTP error status (4xx/5xx)
     * still comes back as a [RawResponse] with that status code, for
     * the caller to interpret (matching the source's own
     * `Result<RawResponse, ClientError>` split: `ClientError` is
     * network-only, an HTTP error status is a normal successful
     * `RawResponse`).
     */
    suspend fun send(path: String, method: String, bodyJson: String? = null, authenticated: Boolean = true): RawResponse? = try {
        val response = httpClient.request("${BackendConfig.BASE_URL}$path") {
            this.method = HttpMethod.parse(method)
            if (authenticated) {
                val token = accessTokenProvider()
                if (token != null) header("Authorization", "Bearer $token")
            }
            if (bodyJson != null) {
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }
        }
        RawResponse(response.status.value, response.bodyAsText())
    } catch (e: Exception) {
        null
    }
}

/**
 * Ported from the iOS app's `Core/BackendAPIClient.swift`'s
 * `BackendErrorResponse` — a non-2xx [BackendApiClient.RawResponse]'s
 * body, when the backend sends one. Shared by every `Backend*Service`
 * that needs to surface a real server error message instead of a
 * generic fallback ([com.BreadIQ.myapp.data.BackendPairingCodeGenerator],
 * [com.BreadIQ.myapp.data.BackendAccountService]), same "decode best
 * effort, fall back to a canned message on failure" shape each of those
 * uses.
 */
@Serializable
data class BackendErrorResponse(val error: String? = null)
