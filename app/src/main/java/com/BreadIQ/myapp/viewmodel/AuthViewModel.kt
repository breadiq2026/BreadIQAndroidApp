package com.BreadIQ.myapp.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.data.AccountServicing
import com.BreadIQ.myapp.data.AuthLifecycleSyncing
import com.BreadIQ.myapp.data.AuthServicing
import com.BreadIQ.myapp.data.BackendAccountService
import com.BreadIQ.myapp.data.BackendApiClient
import com.BreadIQ.myapp.data.SupabaseAuthService
import com.BreadIQ.myapp.data.SupabaseClientProvider
import com.BreadIQ.myapp.data.UnconfiguredAccountService
import com.BreadIQ.myapp.data.UnconfiguredAuthLifecycleSyncer
import com.BreadIQ.myapp.data.UnconfiguredAuthService
import com.BreadIQ.myapp.model.CurrentUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The two fields the iOS `@Observable AuthStore` exposes (`currentUser`, `isLoading`), as one immutable state object for `StateFlow`. */
data class AuthUiState(
    val currentUser: CurrentUser? = null,
    val isLoading: Boolean = true,
)

/**
 * Ported from the iOS app's `Stores/AuthStore.swift`.
 *
 * Owns the current session/user and exposes sign-in/up/out, delegating
 * the actual network/secure-storage work to an injected [AuthServicing]
 * ([com.BreadIQ.myapp.data.SupabaseAuthService] for the real thing). Uses
 * `StateFlow` where the iOS source uses `@Observable` — Compose collects
 * it the same way SwiftUI reads `@Observable` properties.
 *
 * Also owns the source's display-name-fallback behavior: if the live
 * session carries a name (from auth metadata), that value wins and is
 * cached; otherwise the last-cached name is used. iOS uses `UserDefaults`
 * for this; plain (unencrypted) [SharedPreferences] is the direct Android
 * equivalent — a display name isn't secret, matching iOS's own reasoning
 * for why this doesn't belong in Keychain/Keystore.
 *
 * `completePasswordRecovery` is real now, mirroring iOS's
 * `AuthStore.completePasswordRecovery` exactly — see [AuthServicing]'s
 * own doc comment for the deep-link infrastructure that makes it
 * reachable.
 */
class AuthViewModel(
    private val service: AuthServicing = UnconfiguredAuthService(),
    private val accountService: AccountServicing = UnconfiguredAccountService(),
    private val lifecycleSync: AuthLifecycleSyncing = UnconfiguredAuthLifecycleSyncer(),
    private val prefs: SharedPreferences? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadInitialSession() }
    }

    /** Call once at launch — the Kotlin equivalent of the source's `supabase.auth.getSession()` effect. */
    private suspend fun loadInitialSession() {
        val user = applyDisplayNameFallback(service.currentSession())
        _uiState.value = AuthUiState(currentUser = user, isLoading = false)
        service.currentAccessToken()?.let { lifecycleSync.startTrial(it) }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        service.signIn(email, password).fold(
            onSuccess = { user ->
                _uiState.value = _uiState.value.copy(currentUser = applyDisplayNameFallback(user))
                service.currentAccessToken()?.let { lifecycleSync.startTrial(it) }
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )

    suspend fun signUp(email: String, password: String, name: String?): Result<Unit> =
        service.signUp(email, password, name).fold(
            onSuccess = { user ->
                user?.let { _uiState.value = _uiState.value.copy(currentUser = applyDisplayNameFallback(it)) }
                lifecycleSync.syncAudience(email)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )

    suspend fun signOut() {
        service.signOut()
        _uiState.value = _uiState.value.copy(currentUser = null)
    }

    /** `handleForgotPassword`'s `resetPasswordForEmail` call — propagates the real result, see [AuthServicing.requestPasswordReset]'s own doc comment. */
    suspend fun requestPasswordReset(email: String): Result<Unit> = service.requestPasswordReset(email)

    /**
     * `SetNewPasswordScreen`'s submit action — adopts the recovery
     * session and sets a new password on it. On success, assigns the
     * returned user through the same [applyDisplayNameFallback] every
     * other auth-success path already uses, then fires the same
     * idempotent `lifecycleSync.startTrial` call [signIn]/[loadInitialSession]
     * already fire — a freshly-adopted recovery session is a real
     * signed-in session, same as those.
     */
    suspend fun completePasswordRecovery(accessToken: String, refreshToken: String, newPassword: String): Result<Unit> =
        service.completePasswordRecovery(accessToken, refreshToken, newPassword).fold(
            onSuccess = { user ->
                _uiState.value = _uiState.value.copy(currentUser = applyDisplayNameFallback(user))
                service.currentAccessToken()?.let { lifecycleSync.startTrial(it) }
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )

    /** `DELETE /api/me`, then the same sign-out the source performs on success. Inert until a real [AccountServicing] is wired up — see that interface's own doc comment. */
    suspend fun deleteAccount(): Result<Unit> {
        val result = accountService.deleteAccount()
        if (result.isSuccess) signOut()
        return result
    }

    suspend fun setDisplayName(name: String) {
        val trimmed = name.trim()
        val user = _uiState.value.currentUser
        if (trimmed.isEmpty() || user == null) return
        prefs?.edit { putString(CACHED_DISPLAY_NAME_KEY, trimmed) }
        val updated = user.copy(displayName = trimmed)
        _uiState.value = _uiState.value.copy(currentUser = updated)
        service.updateDisplayName(trimmed, user)
    }

    private fun applyDisplayNameFallback(user: CurrentUser?): CurrentUser? {
        if (user == null) return null
        val name = user.displayName
        if (!name.isNullOrEmpty()) {
            prefs?.edit { putString(CACHED_DISPLAY_NAME_KEY, name) }
            return user
        }
        val cached = prefs?.getString(CACHED_DISPLAY_NAME_KEY, null)
        return user.copy(displayName = cached)
    }

    private companion object {
        const val CACHED_DISPLAY_NAME_KEY = "breadiq.cachedDisplayName"
    }
}

/**
 * Builds an [AuthViewModel] backed by the real [SupabaseAuthService] and
 * the app's shared [SupabaseClientProvider] instance. Plain
 * `ViewModelProvider.Factory` rather than the newer `viewModelFactory {}`
 * DSL, to avoid depending on an exact `lifecycle-viewmodel-compose`
 * version for a one-off construction site.
 */
class AuthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val client = SupabaseClientProvider.getInstance(appContext)
        val prefs = appContext.getSharedPreferences("breadiq_prefs", Context.MODE_PRIVATE)
        // Real BackendAccountService now, not the UnconfiguredAccountService()
        // default — same BackendApiClient(accessTokenProvider: ...) shape
        // SubscriptionViewModelFactory already established for BackendTierService.
        val backendClient = BackendApiClient(accessTokenProvider = {
            SupabaseAuthService(client).currentAccessToken()
        })
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(
            service = SupabaseAuthService(client),
            accountService = BackendAccountService(backendClient),
            prefs = prefs,
        ) as T
    }
}
