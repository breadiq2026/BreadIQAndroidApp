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
 * Deliberately narrower than iOS's `AuthServicing`: this omits
 * `completePasswordRecovery`. That method exists on iOS to complete a
 * password-reset deep link (`RootView`'s `pendingPasswordRecovery` +
 * `SetNewPasswordScreen`, routed through `AppRouter`) — none of which is
 * in scope for this session (not in the source-file list this step was
 * given, and Android has no deep-link/App-Links handling set up yet to
 * ever call it). Rather than ship an unverified guess at how
 * `Auth.importSession` behaves for a recovery token pair with no way to
 * test it live, it's left out entirely and noted in PORTING_PLAN.md as
 * deferred, to be added once Android's deep-link consumer exists.
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
