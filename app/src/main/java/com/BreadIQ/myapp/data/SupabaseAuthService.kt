package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.model.CurrentUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
     * `auth.resetPasswordForEmail(email)`, matching iOS's
     * `AuthServicing.requestPasswordReset`. Unlike the source Expo app's
     * `handleForgotPassword` (which never checked this call's result at
     * all, always showing "email sent" regardless of success — a gap the
     * iOS port fixed per direct instruction there), this surfaces the
     * real outcome, same as the iOS port's own fix.
     *
     * No explicit `redirectUrl`: Android has no deep-link consumer for the
     * recovery link yet (see [AuthServicing]'s own doc comment on why
     * `completePasswordRecovery` is out of scope this session). Supabase
     * falls back to the project's dashboard-configured default site URL
     * when none is given — the same graceful fallback GoTrue applies on
     * iOS whenever a `redirect_to` isn't on the project's allowlist.
     */
    override suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email = email)
    }.mapAuthFailure()

    override suspend fun currentAccessToken(): String? {
        client.auth.awaitInitialization()
        return client.auth.currentAccessTokenOrNull()
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
