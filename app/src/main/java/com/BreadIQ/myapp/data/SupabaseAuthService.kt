package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.model.CurrentUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Ported from the iOS app's `Core/SupabaseAuthService.swift` +
 * `Core/SupabaseSessionRefresh.swift`.
 *
 * **A deliberate architecture departure from the iOS port, per this
 * plan's own direction.** iOS's `SupabaseAuthService` is a hand-rolled
 * `URLSession` client against GoTrue's REST API — a choice made there to
 * avoid an extra SDK dependency for a small, stable surface. This port
 * uses the official `supabase-kt` client instead (see
 * `SupabaseClientProvider`), so token refresh, request signing, and GoTrue
 * response parsing are the SDK's job, not this class's. What DOES port
 * 1:1 is the *behavior* iOS's version encodes on top of the raw API:
 *
 * **A real behavioral fact, confirmed on the iOS port against the live
 * project (`GET /auth/v1/settings` → `"mailer_autoconfirm": false`), not
 * assumed**: this project requires email confirmation before a session
 * exists. [signUp]'s return type is `Result<CurrentUser?, ...>` to
 * represent this — `null` on success means "account created, check your
 * email," not an immediately-usable session.
 */
class SupabaseAuthService(private val client: SupabaseClient) : AuthServicing {

    override suspend fun currentSession(): CurrentUser? {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.asCurrentUser()
    }

    override suspend fun signIn(email: String, password: String): Result<CurrentUser> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        client.auth.currentUserOrNull()?.asCurrentUser()
            ?: throw AuthServiceError("Sign-in didn't return a session.")
    }.mapAuthFailure()

    override suspend fun signUp(email: String, password: String, name: String?): Result<CurrentUser?> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // mailer_autoconfirm is off for this project (see class doc) —
        // sign-up does NOT establish a session by itself. Check
        // `sessionStatus` rather than trust `signUpWith`'s own return
        // value: per supabase-kt's own doc comment, that call returns the
        // created user's info precisely in the "no session yet" case and
        // null when auto-confirm DID establish a session — the inverse of
        // what's needed here, so sessionStatus is the reliable signal.
        val user = (client.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user

        // **Known limitation of this pinned supabase-kt version, not a
        // missed spot.** `Email.Config` doesn't gain a `data` field for
        // extra signup metadata until supabase-kt 3.6.0 (verified against
        // the real source at both this pin and 3.6.0) — this app is
        // intentionally pinned below that (see the version comment in
        // `gradle/libs.versions.toml`), so `name` can't be attached to
        // the *initial* signup request the way iOS's hand-rolled REST
        // call does (`body["data"] = ["name": name]`, sent unconditionally
        // regardless of confirmation status). The only place this SDK
        // version lets code attach user metadata is `Auth.updateUser`,
        // which needs an authenticated session — so it only works here
        // for the auto-confirm-on case (`user` non-null below). For this
        // project's real configuration (confirmation required), the name
        // the user typed on the sign-up form is silently NOT captured
        // yet — a real, documented gap, not swept under the rug. Revisit
        // once the supabase-kt pin moves to >= 3.6.0.
        if (user != null && !name.isNullOrEmpty()) {
            runCatching {
                client.auth.updateUser { data = buildJsonObject { put("name", name) } }
            }
        }
        user?.asCurrentUser()
    }.mapAuthFailure()

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }

    override suspend fun updateDisplayName(name: String, user: CurrentUser) {
        runCatching {
            client.auth.updateUser {
                data = buildJsonObject { put("name", name) }
            }
        }
    }

    /**
     * `auth.resetPasswordForEmail(email, redirectUrl: ...)`, matching
     * iOS's `AuthServicing.requestPasswordReset`. Unlike the source Expo
     * app's `handleForgotPassword` (which never checked this call's
     * result at all, always showing "email sent" regardless of success —
     * a gap the iOS port fixed per direct instruction there), this
     * surfaces the real outcome, same as the iOS port's own fix.
     *
     * `redirectUrl = "breadiq-mobile://reset-password"` — the exact
     * string iOS's own `requestPasswordReset` uses as `redirect_to`, and
     * the exact host [com.BreadIQ.myapp.core.DeepLinkRouting] routes on
     * now that a real deep-link consumer exists
     * ([com.BreadIQ.myapp.screens.SetNewPasswordScreen]). GoTrue only
     * honors a `redirect_to`/`redirectUrl` that's on the project's
     * Redirect URLs allowlist (Dashboard → Authentication → URL
     * Configuration) — otherwise it silently substitutes the project
     * default. **Assumed already present, not independently
     * re-verified from this session**: iOS's own doc comment records
     * that this exact string was confirmed live/added to the allowlist
     * when that port built this same flow, and both apps share one
     * Supabase project — flagged here rather than silently assumed
     * bulletproof, since this session had no direct dashboard access to
     * re-check it.
     */
    override suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email = email, redirectUrl = "breadiq-mobile://reset-password")
    }.mapAuthFailure()

    /**
     * Adopts a password-recovery session client-side, then sets the new
     * password on it — the real backend for `SetNewPasswordScreen`.
     *
     * **A real, non-obvious wrinkle iOS doesn't have to deal with**: iOS's
     * own `completePasswordRecovery` builds its `GoTrueSession` by hand
     * (`GoTrueSession(access_token:refresh_token:user:)`, no `expiresIn`
     * field at all — it just stores the raw tokens). `supabase-kt`'s
     * [UserSession] requires a real `expiresIn` (seconds-until-expiry) to
     * construct — decoded here from the access token JWT's own `exp`
     * claim rather than guessing a fixed TTL that might not match this
     * project's real GoTrue config (see [accessTokenExpiresInSeconds]).
     * `user = null` — the SDK's own default; `updateUser` below fills in
     * the real user info via its own response, matching the ordering
     * iOS's version uses (`PUT /auth/v1/user` returns the updated user
     * directly, then that response is what gets stored).
     *
     * Live-verified end to end is NOT claimed here the way iOS's own doc
     * comment claims for its hand-rolled REST version — this session had
     * no way to trigger a real GoTrue recovery email + click the link
     * during development; see this repo's own completion notes for the
     * manual-smoke-test status.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun completePasswordRecovery(accessToken: String, refreshToken: String, newPassword: String): Result<CurrentUser> = runCatching {
        client.auth.importSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = accessTokenExpiresInSeconds(accessToken),
                tokenType = "bearer",
                user = null,
            ),
        )
        client.auth.updateUser { password = newPassword }
        client.auth.currentUserOrNull()?.asCurrentUser()
            ?: throw AuthServiceError("Password updated, but no session was returned.")
    }.mapAuthFailure()

    override suspend fun currentAccessToken(): String? {
        client.auth.awaitInitialization()
        return client.auth.currentAccessTokenOrNull()
    }
}

@Serializable
private data class JwtPayloadExp(val exp: Long? = null)

/**
 * Decodes the access token JWT's own `exp` claim (standard
 * `header.payload.signature` base64url structure, no signature
 * verification needed here — this token was just handed to us by our own
 * deep-link handler, not received from an untrusted third party) and
 * returns seconds-until-expiry, clamped to at least 1. Falls back to a
 * conservative 1-hour estimate (GoTrue's real default access-token TTL)
 * if the token can't be parsed for any reason, rather than failing the
 * whole recovery flow over a decode issue.
 */
private val jwtJson = Json { ignoreUnknownKeys = true }

private fun accessTokenExpiresInSeconds(accessToken: String, fallbackSeconds: Long = 3600L): Long {
    return try {
        val payloadSegment = accessToken.split(".").getOrNull(1) ?: return fallbackSeconds
        val padded = payloadSegment + "=".repeat((4 - payloadSegment.length % 4) % 4)
        val decoded = String(Base64.getUrlDecoder().decode(padded))
        val exp = jwtJson.decodeFromString(JwtPayloadExp.serializer(), decoded).exp
            ?: return fallbackSeconds
        val nowSeconds = System.currentTimeMillis() / 1000
        (exp - nowSeconds).coerceAtLeast(1L)
    } catch (e: Exception) {
        fallbackSeconds
    }
}

private fun UserInfo.asCurrentUser(): CurrentUser = CurrentUser(
    id = id,
    email = email,
    displayName = userMetadata?.get("name")?.jsonPrimitive?.contentOrNull,
)

/**
 * Maps supabase-kt's thrown exceptions to a single [AuthServiceError]
 * with a human-readable message — the SDK-backed equivalent of the iOS
 * port's hand-rolled `GoTrueErrorResponse` decoding. There, done by hand
 * against raw REST bodies since that port doesn't use the official SDK;
 * here, the SDK already parses the GoTrue error body for us, into
 * [AuthRestException.errorDescription].
 */
private fun <T> Result<T>.mapAuthFailure(): Result<T> {
    val error = exceptionOrNull() ?: return this
    val mapped = when (error) {
        is AuthServiceError -> error
        is AuthRestException -> AuthServiceError(error.errorDescription)
        is RestException -> AuthServiceError(error.error)
        is HttpRequestTimeoutException, is HttpRequestException ->
            AuthServiceError("Couldn't reach the server. Check your connection and try again.")
        else -> AuthServiceError(error.message ?: "Something went wrong. Please try again.")
    }
    return Result.failure(mapped)
}
