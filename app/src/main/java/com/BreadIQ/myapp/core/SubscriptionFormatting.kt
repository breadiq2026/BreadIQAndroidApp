package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.SubscriptionIntroPrice

/**
 * Pure decision/formatting logic ported from `subscription.tsx`,
 * independent of the actual RevenueCat SDK calls and view code — same
 * scope boundary as [RevenueCatTierResolution].
 */
object SubscriptionFormatting {

    /** `hasFreeTrialOffer(pkg)`. */
    fun hasFreeTrialOffer(introPrice: SubscriptionIntroPrice?): Boolean {
        val intro = introPrice ?: return false
        return intro.price == 0.0 || intro.paymentMode == "FREE_TRIAL"
    }

    /** `trialDurationLabel(pkg)`. */
    fun trialDurationLabel(introPrice: SubscriptionIntroPrice?): String {
        val intro = introPrice ?: return "1 month"
        val n = intro.periodNumberOfUnits ?: 1
        val unit = (intro.periodUnit ?: intro.period ?: "month").lowercase()
        val unitLabel = if (n == 1) unit else "${unit}s"
        return "$n $unitLabel"
    }

    /**
     * The period toggle's "Save X%" badge: `annualMonthly = annual
     * package's price / 12` vs. the monthly package's price. `null`
     * when there's nothing to show (mirrors the source's `return null`
     * branches: no monthly price to compare against, or the annual
     * rate isn't actually cheaper per-month).
     */
    fun annualSavingsPercent(annualPrice: Double, monthlyPrice: Double): Int? {
        if (monthlyPrice <= 0) return null
        val annualMonthly = annualPrice / 12
        if (annualMonthly >= monthlyPrice) return null
        return ((1 - annualMonthly / monthlyPrice) * 100 + 0.5).toInt()
    }

    fun tierLabel(tier: String): String = if (tier == "basic") "Basic" else "Premium"

    /** Purchase button's `disabled` expression. */
    fun isPurchaseDisabled(hasSelectedPackage: Boolean, isPurchasing: Boolean, rcTier: String, selectedTier: String): Boolean =
        !hasSelectedPackage || isPurchasing || rcTier == selectedTier

    /** Purchase button's label — the three-way branch on current tier / trial eligibility. */
    fun purchaseButtonLabel(rcTier: String, selectedTier: String, trialEligible: Boolean, trialLabel: String): String {
        if (rcTier == selectedTier) return "You're on ${tierLabel(selectedTier)}"
        if (trialEligible) return "Start your free $trialLabel"
        return "Subscribe to ${tierLabel(selectedTier)}"
    }

    /** `!msg.toLowerCase().includes("cancel")` — the purchase-failure alert is suppressed for a user-initiated cancellation. */
    fun isCancellation(errorMessage: String): Boolean = errorMessage.lowercase().contains("cancel")

    /** The price block's secondary line, trial branch: "for X, then $Y/period". */
    fun trialPricePeriodText(priceString: String, period: String, trialLabel: String): String {
        val suffix = if (period == "annual") "$priceString/year" else "$priceString/month"
        return "for $trialLabel, then $suffix"
    }

    /** The price block's secondary line, non-trial branch: monthly is just "/ month"; annual also shows the effective per-month rate. */
    fun nonTrialPricePeriodText(period: String, annualPrice: Double, currencyCode: String): String {
        if (period != "annual") return "/ month"
        val perMonth = String.format("%.2f", annualPrice / 12)
        return "/ year — $currencyCode $perMonth/mo"
    }
}
