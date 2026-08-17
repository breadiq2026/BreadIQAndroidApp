package com.BreadIQ.myapp.data

import com.BreadIQ.myapp.model.CurrentUser

/**
 * Ported from the iOS app's `Stores/AuthStore.swift`'s `AuthServiceError`.
 *
 * A raw, unhumanized auth error message. Deliberately NOT run through
 * [AuthErrorHumanizer] here — per that file's own doc comment, only the
 * sign-in path applies it, and only in the UI layer (`AuthScreen`). Apply
 * it at that call site, not inside this service or the view model.
 */
class AuthServiceError(message: String) : Exception(message)

/**
 * Ported from the iOS app's `Stores/AuthStore.swift`'s `AuthServicing`
 * protocol.
 *
 * Seam between `AuthViewModel` and the real backing implementation
 * ([SupabaseAuthService]). `AuthViewModel` is fully real against this
 * interface — only the concrete implementation differs by build
 * configuration, same pattern the iOS port uses.
 *
 * **`completePasswordRecovery` is real now** — the deep-link/App Links
 * infrastructure session wired up the one thing this method needed to be
 * reachable at all: a deep-link consumer that can actually hand it an
 * `accessToken`/`refreshToken` pair
 * ([com.BreadIQ.myapp.core.DeepLinkRouting]'s `DeepLinkDestination.PasswordRecovery`,
 * routed by `MainActivity.kt` to [com.BreadIQ.myapp.screens.SetNewPasswordScreen]).
 */
interface AuthServicing {
    /** The session that already exists at launch, if any. */
    suspend fun currentSession(): CurrentUser?
    suspend fun signIn(email: String, password: String): Result<CurrentUser>
    /**
     * `null` on success means the account was created but no session
     * exists yet — e.g. email confirmation is required, matching this
     * project's real Supabase configuration (`mailer_autoconfirm: false`,
     * confirmed on the iOS port against the same backend project).
     */
    suspend fun signUp(email: String, password: String, name: String?): Result<CurrentUser?>
    suspend fun signOut()
    suspend fun updateDisplayName(name: String, user: CurrentUser)
    suspend fun requestPasswordReset(email: String): Result<Unit>
    /**
     * Adopts a password-recovery session (from a `breadiq-mobile://reset-password#...`/
     * `https://breadiq.io/reset-password#...` deep link) and sets a new
     * password on it — the real backend for
     * [com.BreadIQ.myapp.screens.SetNewPasswordScreen]. Mirrors iOS's
     * `AuthServicing.completePasswordRecovery` exactly; see
     * [SupabaseAuthService]'s own implementation doc comment for how this
     * differs structurally from the iOS port's hand-rolled REST call
     * (this port uses the official `supabase-kt` SDK throughout).
     */
    suspend fun completePasswordRecovery(accessToken: String, refreshToken: String, newPassword: String): Result<CurrentUser>
    /**
     * The current session's raw access token, for callers that need to
     * authenticate directly against the custom backend (not Supabase
     * itself) — mirrors iOS's `AuthServicing.currentAccessToken()`, kept
     * here for the same future `BackendAPIClient` bearer-header use even
     * though nothing calls it yet.
     */
    suspend fun currentAccessToken(): String?
}

/**
 * Placeholder [AuthServicing] used until a real [SupabaseAuthService] is
 * wired up at a call site. Returns a clear, honest "not implemented yet"
 * error rather than crashing or silently no-op'ing, so the app stays
 * runnable (if inert) — same reasoning as iOS's `UnconfiguredAuthService`.
 */
class UnconfiguredAuthService : AuthServicing {
    override suspend fun currentSession(): CurrentUser? = null
    override suspend fun signIn(email: String, password: String): Result<CurrentUser> = Result.failure(notReady)
    override suspend fun signUp(email: String, password: String, name: String?): Result<CurrentUser?> = Result.failure(notReady)
    override suspend fun signOut() {}
    override suspend fun updateDisplayName(name: String, user: CurrentUser) {}
    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.failure(notReady)
    override suspend fun completePasswordRecovery(accessToken: String, refreshToken: String, newPassword: String): Result<CurrentUser> = Result.failure(notReady)
    override suspend fun currentAccessToken(): String? = null

    private companion object {
        val notReady = AuthServiceError(
            "Sign-in isn't available yet — check back once the account system is finished."
        )
    }
}

/**
 * Ported from the iOS app's `Stores/AuthStore.swift`'s `AccountServicing`
 * protocol — seam for `DELETE /api/me`, kept separate from
 * [AuthServicing] since account deletion hits the custom backend
 * directly, not Supabase itself. **Real now**:
 * [com.BreadIQ.myapp.data.BackendAccountService] (Settings + Connect-a-Browser
 * session) is wired into [com.BreadIQ.myapp.viewmodel.AuthViewModelFactory]
 * for real; [UnconfiguredAccountService] remains as the honest fallback
 * for any call site that doesn't thread a real implementation in.
 */
interface AccountServicing {
    suspend fun deleteAccount(): Result<Unit>
}

class UnconfiguredAccountService : AccountServicing {
    override suspend fun deleteAccount(): Result<Unit> = Result.failure(
        AuthServiceError("Account deletion isn't available yet — check back once the account system is finished.")
    )
}

/**
 * Ported from the iOS app's `Stores/AuthStore.swift`'s
 * `AuthLifecycleSyncing` protocol — seam for the two custom-backend calls
 * fired on sign-in/sign-up (`POST /api/me/start-trial`,
 * `POST /api/email/audience-sync`), both fire-and-forget on iOS (no error
 * surfaced to the user) and ported the same way here: non-throwing, no
 * `Result`. No backend client exists on Android yet, so
 * [UnconfiguredAuthLifecycleSyncer] is the only implementation for now —
 * `AuthViewModel` still calls through this seam at the right moments so
 * wiring in a real implementation later is a one-line change.
 */
interface AuthLifecycleSyncing {
    suspend fun startTrial(accessToken: String)
    suspend fun syncAudience(email: String)
}

class UnconfiguredAuthLifecycleSyncer : AuthLifecycleSyncing {
    override suspend fun startTrial(accessToken: String) {}
    override suspend fun syncAudience(email: String) {}
}
