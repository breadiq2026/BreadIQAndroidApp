package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Core/BakeSessionEngine.swift` (the enum
 * itself, not the rest of that file — `BakeSessionEngine` is its own,
 * not-yet-ported sequence item; this 3-case enum is small and shared
 * widely enough — the Calculator screen's tier gating needs it now —
 * that it's worth splitting out on its own rather than waiting).
 *
 * **No real subscription store exists on Android yet** (`SubscriptionStore.swift`/
 * `RevenueCatPurchasesService.swift` are their own already-planned,
 * independent porting item — see PORTING_PLAN.md's RevenueCat step).
 * Every call site that needs a [BakeUserTier] today reads a hardcoded
 * [BakeUserTier.FREE] until that step lands — the correct, honest
 * fallback for "no subscription information available yet" rather than
 * a guess at a different default.
 */
enum class BakeUserTier(val rawValue: String) {
    FREE("free"),
    BASIC("basic"),
    PREMIUM("premium"),
}
