package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Core/BakeSessionEngine.swift` (the enum
 * itself, not the rest of that file — `BakeSessionEngine` is its own,
 * not-yet-ported sequence item; this 3-case enum is small and shared
 * widely enough — the Calculator screen's tier gating needs it now —
 * that it's worth splitting out on its own rather than waiting).
 *
 * **A real subscription store exists now** (`viewmodel/SubscriptionViewModel.kt`,
 * backed by `core/RevenueCatPurchasesService.kt` — PORTING_PLAN.md's
 * RevenueCat step) — `CalculatorViewModel.userTier` resolves for real
 * from it. [BakeUserTier.FREE] is still the honest default for the
 * brief window before that store's first tier resolution completes, or
 * for any call site that hasn't been threaded a live tier yet — no
 * longer a permanent stand-in.
 */
enum class BakeUserTier(val rawValue: String) {
    FREE("free"),
    BASIC("basic"),
    PREMIUM("premium"),
}
