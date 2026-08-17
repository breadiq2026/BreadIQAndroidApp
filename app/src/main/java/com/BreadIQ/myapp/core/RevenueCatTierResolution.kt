package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.TierInfo

/**
 * Port of `lib/revenuecat.tsx`'s `getRcTierFromCustomerInfo` and
 * `hooks/useUserTier.ts`'s tier-resolution precedence — the pure
 * decision logic for "what subscription tier is this user on right
 * now," independent of the actual RevenueCat SDK calls and network
 * fetch that supply its inputs.
 *
 * **Scope boundary, deliberate**: actually calling the RevenueCat
 * Android SDK and the `GET /api/me/tier` network fetch are both real
 * SDK-wiring concerns handled by [RevenueCatPurchasesService]/
 * [com.BreadIQ.myapp.data.BackendTierService] — this object only covers
 * the pure decision of which source to trust and how to interpret each
 * one's result.
 *
 * **Mobile/web drift, checked against `bread-lab/src/hooks/
 * use-user-tier.ts`**: genuinely different, and not a bug — web's
 * `useUserTier` has NO RevenueCat/entitlement check at all; it goes
 * straight to `/api/me/tier`. Makes sense architecturally: web has no
 * App Store/Play Store in-app purchase to check RevenueCat entitlements
 * against, so it's 100% server-authoritative, unlike mobile's
 * "RevenueCat wins outright, server is the free-tier fallback"
 * precedence. Ported mobile's actual (more involved) precedence, since
 * that's this item's real scope.
 *
 * **A live, currently-active drift found in the process**:
 * `PRE_ALPHA_MODE` (a manual override that unlocks premium for
 * everyone, used pre-launch) is `false` on mobile today but `true` on
 * web (`bread-lab/src/lib/preAlpha.ts`) — the two platforms are mid a
 * coordinated-but-staggered rollout, not a bug. Ported as a real,
 * flippable constant (matching mobile's current value) rather than
 * omitted as dead code, since web's live `true` value confirms this
 * mechanism is genuinely exercised today, just not on mobile.
 */
object RevenueCatTierResolution {

    /**
     * `getRcTierFromCustomerInfo()`. The caller passes the set of
     * currently-active RevenueCat entitlement identifiers (read from a
     * real `CustomerInfo.entitlements.active`'s keys once
     * [RevenueCatPurchasesService] resolves it); `null` represents the
     * source's `info: CustomerInfo | undefined` case (no customer info
     * available at all — same "free" outcome as an empty set, kept as a
     * distinct input for parity with the source's own null check).
     *
     * `RC_ENTITLEMENT_PREMIUM`/`RC_ENTITLEMENT_BASIC` in the source are
     * named constants purely for readability — their values are
     * literally the strings "premium"/"basic" (real RevenueCat
     * entitlement identifiers already configured in this project's
     * dashboard, shared across both the iOS and Android apps since
     * entitlements are project-scoped, not per-app), not distinct
     * identifiers from the tier names they represent, so used directly
     * here rather than introducing parallel named constants.
     */
    fun rcTier(activeEntitlements: Set<String>?): String {
        val active = activeEntitlements ?: emptySet()
        if (active.contains("premium")) return "premium"
        if (active.contains("basic")) return "basic"
        return "free"
    }

    /** `PRE_ALPHA_MODE` — see this object's own doc comment for the mobile/web drift this constant represents. */
    const val preAlphaModeEnabled: Boolean = false

    private val preAlphaTier = TierInfo(tier = "premium", trialActive = false, trialExpiresAt = null, trialDaysRemaining = null)

    sealed class Resolution {
        /** Use this tier directly — no server fetch needed. */
        data class UseTier(val tierInfo: TierInfo) : Resolution()
        /** Fall back to `GET /api/me/tier` (real SDK wiring's job to actually call). */
        data object FetchServerTier : Resolution()
    }

    /**
     * The core precedence decision from `useUserTier`'s `queryFn`:
     * pre-alpha override first, then a non-free RevenueCat tier (which
     * always wins outright — trial fields are forced to
     * inactive/`null`, even if the server might separately track a
     * trial), and only then defer to the server. Pass `rcTier = "free"`
     * for BOTH a genuine free-tier `CustomerInfo` AND the source's
     * try/catch fallback (RC SDK threw, or isn't configured) — the
     * source treats both identically, falling through to the server
     * either way.
     *
     * [preAlphaModeEnabled] defaults to the real current constant but is
     * overridable, matching this codebase's own `now: Instant` pattern
     * elsewhere — otherwise the pre-alpha branch would be untestable
     * through the public API while the flag is `false`.
     */
    fun resolve(rcTier: String, preAlphaModeEnabled: Boolean = this.preAlphaModeEnabled): Resolution {
        if (preAlphaModeEnabled) {
            return Resolution.UseTier(preAlphaTier)
        }
        if (rcTier != "free") {
            return Resolution.UseTier(TierInfo(tier = rcTier, trialActive = false, trialExpiresAt = null, trialDaysRemaining = null))
        }
        return Resolution.FetchServerTier
    }

    /** `useIsPremium()`. */
    fun isPremium(tierInfo: TierInfo): Boolean = tierInfo.tier == "premium"
}
