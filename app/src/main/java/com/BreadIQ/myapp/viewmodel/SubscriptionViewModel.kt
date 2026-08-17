package com.BreadIQ.myapp.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.PurchaseOutcome
import com.BreadIQ.myapp.core.PurchasesServicing
import com.BreadIQ.myapp.core.RevenueCatPurchasesService
import com.BreadIQ.myapp.core.RevenueCatTierResolution
import com.BreadIQ.myapp.core.TierServicing
import com.BreadIQ.myapp.core.UnconfiguredPurchasesService
import com.BreadIQ.myapp.core.UnconfiguredTierService
import com.BreadIQ.myapp.data.BackendApiClient
import com.BreadIQ.myapp.data.BackendTierService
import com.BreadIQ.myapp.data.SupabaseAuthService
import com.BreadIQ.myapp.data.SupabaseClientProvider
import com.BreadIQ.myapp.model.SubscriptionPackage
import com.BreadIQ.myapp.model.TierInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val tierInfo: TierInfo? = null,
    val isLoading: Boolean = true,
    /**
     * The raw RevenueCat-resolved tier (`useSubscription().rcTier` in
     * the source) — distinct from `tierInfo.tier`, which can come from
     * the server fallback instead. A future Settings screen's "Manage
     * Subscription" row would gate on this specifically (`rcTier !==
     * "free"`), not the resolved display tier, since only an actual RC
     * subscription has anything to manage via the Play Store.
     */
    val rcTier: String = "free",
    val isRestoring: Boolean = false,
    /** `offerings?.current?.availablePackages ?? []`. */
    val packages: List<SubscriptionPackage> = emptyList(),
    val isLoadingOfferings: Boolean = true,
    val isPurchasing: Boolean = false,
)

val SubscriptionUiState.isPremium: Boolean
    get() = tierInfo?.let { RevenueCatTierResolution.isPremium(it) } ?: false

/**
 * Ported from the iOS app's `Stores/SubscriptionStore.swift`. Owns the
 * resolved tier, delegating the actual RevenueCat SDK calls and server
 * fetch to injected services ([RevenueCatPurchasesService]/
 * [BackendTierService] for the real thing), while reusing
 * [RevenueCatTierResolution] for the precedence decision itself.
 *
 * **Activity-scoped, not tied to a single nav back-stack entry** — the
 * architecture decision this session called for explicitly. This store
 * needs to be readable from the root login/logout binding
 * (`MainActivity.kt`'s own auth-gating `when` branch), from
 * `CalculatorViewModel` (its `userTier` now sources from here), and from
 * [com.BreadIQ.myapp.ui.subscription.SubscriptionScreen] itself — the
 * same three-consumer shape [AuthViewModel] already has, so this follows
 * that same `by viewModels {}` precedent rather than inventing a new one.
 *
 * **Deliberately not responsible for RevenueCat login/logout binding**
 * (`loginRevenueCat(session.user.id)`/`logoutRevenueCat()` in the
 * source). That binding lives in the root auth-gating coordinator
 * (`MainActivity.kt`'s `setContent` block), NOT inside this ViewModel —
 * [login]/[logout] below exist for that coordinator to call, preserving
 * the source's loose one-directional coupling rather than merging the
 * two stores together.
 *
 * `refreshTier()`/`refreshOfferings()` fire once from [init], matching
 * the source's own `SubscriptionProvider` firing both queries at mount
 * regardless of whether the paywall is ever opened — this ViewModel's
 * own construction (once per `MainActivity` instance) is the direct
 * Android analog of that mount point.
 */
class SubscriptionViewModel(
    private val purchases: PurchasesServicing = UnconfiguredPurchasesService(),
    private val tierService: TierServicing = UnconfiguredTierService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshTier() }
        viewModelScope.launch { refreshOfferings() }
    }

    /** Call at launch (see [init]) and whenever the tier might have changed (e.g. after a purchase completes) — the Kotlin equivalent of `useUserTier`'s `queryFn`. */
    suspend fun refreshTier() {
        resolveTier(purchases.activeEntitlements())
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    /** `offeringsQuery` — call at launch alongside [refreshTier]. */
    suspend fun refreshOfferings() {
        val packages = purchases.fetchOfferings()
        _uiState.value = _uiState.value.copy(packages = packages, isLoadingOfferings = false)
    }

    /** Called by the root auth-gating coordinator on sign-in. */
    fun login(userId: String) {
        viewModelScope.launch { purchases.logIn(userId) }
    }

    /** Called by the root auth-gating coordinator on sign-out. */
    fun logout() {
        viewModelScope.launch { purchases.logOut() }
    }

    sealed class RestoreOutcome {
        data object Restored : RestoreOutcome()
        data object NoPurchasesFound : RestoreOutcome()
    }

    /**
     * `useSubscription().restore` (`Purchases.restorePurchases()`).
     *
     * In the source, restoring only refetches `customerInfoQuery`
     * (updating `rcTier`) — `useUserTier`'s separately-cached `tierInfo`
     * isn't touched by that refetch at all, since the two are genuinely
     * independent `react-query` caches with no invalidation wired
     * between them there. That looks like an incidental side effect of
     * the source's two-context split rather than a deliberate "don't
     * refresh the display tier after a restore" decision. Since this
     * store already merges both concerns (matching the iOS port's own
     * Phase 2 merge), restoring here re-resolves both together via the
     * same [resolveTier] path [refreshTier] uses — keeping them in sync
     * is a natural consequence of that earlier architecture choice, not
     * a new behavior being invented.
     */
    suspend fun restorePurchases(): RestoreOutcome {
        _uiState.value = _uiState.value.copy(isRestoring = true)
        val result = purchases.restorePurchases()
        resolveTier(result.activeEntitlements)
        _uiState.value = _uiState.value.copy(isRestoring = false)
        return if (result.activeSubscriptionProductIdentifiers.isEmpty()) RestoreOutcome.NoPurchasesFound else RestoreOutcome.Restored
    }

    /**
     * `purchaseMutation` (`purchase(confirmPkg)` in
     * `handleConfirmPurchase`). On success, re-resolves tier/rcTier —
     * the Kotlin equivalent of the source's `onSuccess: () =>
     * customerInfoQuery.refetch()`. [activity] is threaded straight
     * through to [PurchasesServicing.purchase] — see that interface's
     * own doc comment for why Google Play Billing needs one and StoreKit
     * doesn't.
     */
    suspend fun purchase(activity: Activity, productIdentifier: String): PurchaseOutcome {
        _uiState.value = _uiState.value.copy(isPurchasing = true)
        val result = purchases.purchase(activity, productIdentifier)
        if (result is PurchaseOutcome.Success) {
            resolveTier(purchases.activeEntitlements())
        }
        _uiState.value = _uiState.value.copy(isPurchasing = false)
        return result
    }

    private suspend fun resolveTier(activeEntitlements: Set<String>) {
        val resolvedRcTier = RevenueCatTierResolution.rcTier(activeEntitlements)
        val newTierInfo = when (val resolution = RevenueCatTierResolution.resolve(resolvedRcTier)) {
            is RevenueCatTierResolution.Resolution.UseTier -> resolution.tierInfo
            RevenueCatTierResolution.Resolution.FetchServerTier ->
                tierService.fetchTier() ?: TierInfo(tier = "free", trialActive = false, trialExpiresAt = null, trialDaysRemaining = null)
        }
        _uiState.value = _uiState.value.copy(rcTier = resolvedRcTier, tierInfo = newTierInfo)
    }
}

/**
 * Builds a [SubscriptionViewModel] backed by the real
 * [RevenueCatPurchasesService]/[BackendTierService]. [BackendTierService]'s
 * client is constructed with a real
 * [SupabaseAuthService.currentAccessToken]-backed token provider,
 * matching the source's own `BackendAPIClient(accessTokenProvider: {
 * await auth.currentAccessToken() })` construction exactly.
 */
class SubscriptionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val backendClient = BackendApiClient(accessTokenProvider = {
            SupabaseAuthService(SupabaseClientProvider.getInstance(appContext)).currentAccessToken()
        })
        @Suppress("UNCHECKED_CAST")
        return SubscriptionViewModel(
            purchases = RevenueCatPurchasesService(),
            tierService = BackendTierService(backendClient),
        ) as T
    }
}
