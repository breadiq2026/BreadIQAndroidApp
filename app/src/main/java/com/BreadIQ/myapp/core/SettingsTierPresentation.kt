package com.BreadIQ.myapp.core

/**
 * Ported from the iOS app's `Screens/SettingsScreen.swift`'s
 * `SettingsTierPresentation` enum — the tier row's text branching.
 * Straight port of `app/settings.tsx`'s pure tier-label/description
 * branching.
 */
object SettingsTierPresentation {
    fun label(trialActive: Boolean, isPremium: Boolean, isBasic: Boolean): String {
        if (trialActive) return "Premium Trial"
        if (isPremium) return "Premium"
        if (isBasic) return "Basic"
        return "Free"
    }

    fun description(trialActive: Boolean, trialDaysRemaining: Int?, isPremium: Boolean, isBasic: Boolean): String {
        if (trialActive && trialDaysRemaining != null) {
            val plural = if (trialDaysRemaining == 1) "" else "s"
            return "$trialDaysRemaining day$plural remaining — all features unlocked"
        }
        if (isPremium) return "All features unlocked"
        if (isBasic) return "3 flour types · Pre-ferments · 10 recipes"
        return "Upgrade to unlock premium features"
    }
}
