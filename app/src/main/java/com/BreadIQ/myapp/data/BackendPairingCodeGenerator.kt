package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.core.PairingCode
import com.BreadIQ.myapp.core.PairingCodeError
import com.BreadIQ.myapp.core.PairingCodeGenerating
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real [PairingCodeGenerating] implementation.
 *
 * ██ STUB-BACKEND ██ — `POST /api/auth/pairing-code/generate` does not
 * exist on the live `api-server` yet. Written against the exact contract
 * the Chrome-extension discovery memo proposed (`{ code, expiresAt }`),
 * authenticated the same way every other per-user call in this codebase
 * is ([client]'s default `authenticated: true`, resolving the current
 * Supabase access token) — nothing left to wire up client-side once the
 * route ships; this call just fails gracefully until then, same shape as
 * [BackendTierService]'s own non-2xx handling.
 *
 * **Date parsing is genuinely simpler here than on iOS**: the source
 * needs a custom `BackendDateCoding.decoder` because Foundation's
 * `.iso8601` strategy rejects this backend's fractional-second
 * timestamps (`"2026-06-12T12:33:49.164Z"`); [Instant.parse] handles
 * that format natively, no custom decoder needed.
 */
class BackendPairingCodeGenerator(private val client: BackendApiClient) : PairingCodeGenerating {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val code: String? = null, val expiresAt: String? = null)

    override suspend fun generateCode(): Result<PairingCode> {
        // ██ STUB-BACKEND ██ /api/auth/pairing-code/generate
        val raw = client.send(path = "/api/auth/pairing-code/generate", method = "POST")
            ?: return Result.failure(PairingCodeError("Couldn't reach the server. Check your connection and try again."))

        if (raw.statusCode !in 200..299) {
            val message = decodeErrorMessage(raw.body)
                ?: "Browser pairing isn't available yet — check back once the backend is finished."
            return Result.failure(PairingCodeError(message))
        }

        val decoded = try {
            json.decodeFromString(Response.serializer(), raw.body)
        } catch (e: Exception) {
            null
        }
        val code = decoded?.code
        val expiresAt = decoded?.expiresAt?.let { parseInstant(it) }
        if (code == null || expiresAt == null) {
            return Result.failure(PairingCodeError("Failed to generate a pairing code."))
        }
        return Result.success(PairingCode(code = code, expiresAt = expiresAt))
    }

    private fun decodeErrorMessage(body: String): String? = try {
        json.decodeFromString(BackendErrorResponse.serializer(), body).error
    } catch (e: Exception) {
        null
    }

    private fun parseInstant(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        null
    }
}
