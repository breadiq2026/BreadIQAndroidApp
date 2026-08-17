package com.BreadIQ.myapp

import android.app.Application
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.core.CalendarEventScheduler
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * App-level entry point. Supabase auth/session wiring (PORTING_PLAN.md
 * step 2) lives in `data/SupabaseClientProvider.kt` + `viewmodel/
 * AuthViewModel.kt` instead of here — `MainActivity`'s `AuthViewModelFactory`
 * builds the single app-wide `SupabaseClient` on first access (effectively
 * app launch, since it's the first thing `MainActivity.onCreate` needs),
 * so there's no separate eager-init step needed here the way iOS's
 * `BreadIQApp.swift` does it in its own app-launch hook.
 *
 * `DatabaseProvider.getInstance(this)` below IS called eagerly here,
 * though — the direct structural parallel to `BreadIQApp.swift`'s
 * `init()` calling `Self.makeModelContainer()`. This call alone still
 * can't fail or signal anything meaningful (Room's `.build()` stays
 * lazy no matter when it's called) — it just ensures the singleton
 * exists from launch, matching the iOS call site's timing. The actual
 * success/failure signal now comes from `MainActivity.kt`'s own
 * `DatabaseProvider.openEagerly` call, made once per launch (and again
 * on retry/erase) from its `DbOpenState` gate — see that class's and
 * `DatabaseProvider`'s own doc comments for the fuller writeup.
 *
 * [Purchases.configure] is called eagerly here too (PORTING_PLAN.md
 * step 6) — must run before any other `Purchases` API call, so it has
 * to happen before [SubscriptionViewModel][com.BreadIQ.myapp.viewmodel.SubscriptionViewModel]'s
 * own first `refreshTier()`/`refreshOfferings()` call, which can only
 * be guaranteed by configuring here at process start rather than lazily
 * at that ViewModel's own construction — mirrors the iOS source's own
 * `Purchases.configure(withAPIKey:)` call in `BreadIQApp.init()`, which
 * runs before its `SubscriptionStore`'s first refresh for the identical
 * reason. Public SDK key for the Play Store app (RevenueCat dashboard
 * — entitlements/products are project-scoped, shared with the iOS app,
 * but this API key is platform-specific).
 *
 * [BakeNotificationScheduler.init]/[BakeNotificationScheduler.createNotificationChannel]
 * are called eagerly here too (PORTING_PLAN.md step 7) — the Android
 * counterpart of `BreadIQApp.swift` having no equivalent step at all
 * (`UNUserNotificationCenter` needs no app-launch setup on iOS; a
 * notification *channel*, required on Android's API 26+, is the one
 * piece of setup Android needs that iOS doesn't).
 *
 * [CalendarEventScheduler.init] is called eagerly here for the same
 * reason as `BakeNotificationScheduler.init` — see that object's own
 * doc comment.
 */
class BreadIQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseProvider.getInstance(this)
        BakeNotificationScheduler.init(this)
        BakeNotificationScheduler.createNotificationChannel(this)
        CalendarEventScheduler.init(this)
        Purchases.configure(PurchasesConfiguration.Builder(this, REVENUECAT_PUBLIC_SDK_KEY).build())
    }

    private companion object {
        const val REVENUECAT_PUBLIC_SDK_KEY = "goog_QWhHRqIUeUVxUdiduiBVXpElemC"
    }
}
