package com.BreadIQ.myapp.core

import android.app.Activity
import com.BreadIQ.myapp.model.SubscriptionIntroPrice
import com.BreadIQ.myapp.model.SubscriptionPackage
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.RecurrenceMode
import com.revenuecat.purchases.models.StoreProduct

/**
 * Real [PurchasesServicing] implementation — the actual RevenueCat
 * Android SDK (`com.revenuecat.purchases:purchases`), replacing
 * [UnconfiguredPurchasesService]. Unlike [com.BreadIQ.myapp.data.SupabaseAuthService]'s
 * own "plain REST is enough" call, RevenueCat's real SDK earns its
 * dependency weight here — it owns the Play Billing purchase flow,
 * server-side receipt validation, and entitlement caching, none of
 * which is reasonable to reimplement.
 *
 * Uses the SDK's own Kotlin coroutine `awaitX()` suspend extensions
 * (`awaitCustomerInfo`/`awaitLogIn`/`awaitLogOut`/`awaitRestore`/
 * `awaitOfferings`/`awaitPurchase`) rather than the older callback-based
 * `xWith(...)` API — confirmed current on the pinned 10.16.2 release,
 * not assumed.
 *
 * **Real Play Console products, not a local test/sandbox
 * configuration — a direct choice, not a default, matching the source's
 * own "real App Store Connect products" call.** `activeEntitlements()`/
 * `fetchOfferings()` are plain reads with no purchase involved. Full
 * purchase-flow completion needs a Play Console license tester + real
 * subscription products, neither of which exist yet (no build has been
 * uploaded to Play Console, and Monetize has no subscription products
 * created) — same boundary the iOS port itself documented and accepted
 * for its own sandbox-tester requirement. This is built and wired for
 * real; full purchase-flow verification waits for those two
 * prerequisites, not for this session.
 *
 * [Purchases.configure] must be called once, before any other
 * `Purchases` API — done in `BreadIQApplication.onCreate()`, mirroring
 * the source's own call in `BreadIQApp.init()`.
 */
class RevenueCatPurchasesService : PurchasesServicing {

    override suspend fun activeEntitlements(): Set<String> = try {
        Purchases.sharedInstance.awaitCustomerInfo().entitlements.active.keys
    } catch (e: Exception) {
        emptySet()
    }

    override suspend fun logIn(userId: String) {
        try {
            Purchases.sharedInstance.awaitLogIn(userId)
        } catch (e: Exception) {
            // Best-effort, matches the source's own `_ = try? await ...`.
        }
    }

    override suspend fun logOut() {
        try {
            Purchases.sharedInstance.awaitLogOut()
        } catch (e: Exception) {
            // Best-effort, matches the source's own `_ = try? await ...`.
        }
    }

    override suspend fun restorePurchases(): RestoreResult = try {
        val info = Purchases.sharedInstance.awaitRestore()
        RestoreResult(
            activeEntitlements = info.entitlements.active.keys,
            activeSubscriptionProductIdentifiers = info.activeSubscriptions,
        )
    } catch (e: Exception) {
        RestoreResult(activeEntitlements = emptySet(), activeSubscriptionProductIdentifiers = emptySet())
    }

    override suspend fun fetchOfferings(): List<SubscriptionPackage> {
        val offerings = try {
            Purchases.sharedInstance.awaitOfferings()
        } catch (e: Exception) {
            return emptyList()
        }
        val packages = offerings.current?.availablePackages ?: emptyList()
        return packages.mapNotNull { pkg ->
            val classification = SubscriptionPackageClassification.classify(pkg.product.id) ?: return@mapNotNull null
            SubscriptionPackage(
                tier = classification.tier,
                period = classification.period,
                productIdentifier = pkg.product.id,
                title = pkg.product.title,
                priceString = pkg.product.price.formatted,
                price = pkg.product.price.amountMicros / 1_000_000.0,
                currencyCode = pkg.product.price.currencyCode,
                introPrice = introPrice(pkg.product),
            )
        }
    }

    override suspend fun purchase(activity: Activity, productIdentifier: String): PurchaseOutcome {
        val offerings = try {
            Purchases.sharedInstance.awaitOfferings()
        } catch (e: Exception) {
            return PurchaseOutcome.Failure(PurchaseError("This package isn't available right now. Please try again."))
        }
        val pkg = offerings.current?.availablePackages?.firstOrNull { it.product.id == productIdentifier }
            ?: return PurchaseOutcome.Failure(PurchaseError("This package isn't available right now. Please try again."))

        return try {
            Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            PurchaseOutcome.Success
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) {
                // Contains "cancel" deliberately — SubscriptionFormatting
                // .isCancellation(errorMessage:) relies on this exact
                // substring to suppress the "Purchase Failed" alert for
                // cancellations, same as iOS.
                PurchaseOutcome.Failure(PurchaseError("Purchase cancelled."))
            } else {
                PurchaseOutcome.Failure(PurchaseError(e.message ?: "Something went wrong. Please try again."))
            }
        } catch (e: Exception) {
            PurchaseOutcome.Failure(PurchaseError(e.message ?: "Something went wrong. Please try again."))
        }
    }

    // MARK: - Mapping helpers

    /**
     * **A real Android Billing shape difference from iOS's
     * `StoreProductDiscount`, not a straight port.** Google Play
     * subscriptions model pricing as a list of `PricingPhase`s (free
     * trial → intro discount → base price) on a `SubscriptionOption`,
     * not StoreKit's single `introductoryDiscount` field. Maps
     * [StoreProduct.defaultOption]'s [PricingPhase.freePhase] (a genuine
     * free trial) or, failing that, its [PricingPhase.introPhase] (a
     * paid introductory price) onto the existing [SubscriptionIntroPrice]
     * shape — `null` when neither exists, matching iOS's `nil`
     * `introductoryDiscount` case.
     */
    private fun introPrice(product: StoreProduct): SubscriptionIntroPrice? {
        val option = product.defaultOption ?: return null
        val freePhase = option.freePhase
        if (freePhase != null) {
            return SubscriptionIntroPrice(
                price = freePhase.price.amountMicros / 1_000_000.0,
                priceString = freePhase.price.formatted,
                paymentMode = "FREE_TRIAL",
                period = unitString(freePhase.billingPeriod),
                periodUnit = unitString(freePhase.billingPeriod),
                periodNumberOfUnits = freePhase.billingPeriod.value,
            )
        }
        val introPhase = option.introPhase ?: return null
        // Only `FREE_TRIAL` is ever actually read (`SubscriptionFormatting
        // .hasFreeTrialOffer` checks `paymentMode == "FREE_TRIAL"` only)
        // — a paid intro phase's exact PAY_UP_FRONT/PAY_AS_YOU_GO split
        // doesn't affect any real behavior downstream, same as iOS's own
        // "reasonable, consistent placeholder" treatment for its other
        // two payment-mode cases. Recurring across more than one billing
        // cycle reads as "pay as you go," a single discounted cycle
        // reads as "pay up front."
        val paymentMode = if (introPhase.recurrenceMode == RecurrenceMode.FINITE_RECURRING && (introPhase.billingCycleCount ?: 1) > 1) {
            "PAY_AS_YOU_GO"
        } else {
            "PAY_UP_FRONT"
        }
        return SubscriptionIntroPrice(
            price = introPhase.price.amountMicros / 1_000_000.0,
            priceString = introPhase.price.formatted,
            paymentMode = paymentMode,
            period = unitString(introPhase.billingPeriod),
            periodUnit = unitString(introPhase.billingPeriod),
            periodNumberOfUnits = introPhase.billingPeriod.value,
        )
    }

    private fun unitString(period: Period): String = when (period.unit) {
        Period.Unit.DAY -> "day"
        Period.Unit.WEEK -> "week"
        Period.Unit.MONTH -> "month"
        Period.Unit.YEAR -> "year"
        else -> "month"
    }
}
