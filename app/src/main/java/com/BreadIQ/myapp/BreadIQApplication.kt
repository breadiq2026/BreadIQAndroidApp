package com.BreadIQ.myapp

import android.app.Application
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.data.local.DatabaseProvider

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
 * `init()` calling `Self.makeModelContainer()`. Unlike that call, this
 * one can't itself fail or signal anything meaningful yet (see
 * `data/local/BreadIQDatabase.kt`'s doc comment on why Room's lazy-open
 * model has no single construction-time success/failure point the way
 * SwiftData's `ModelContainer` does) — this just ensures the singleton
 * exists from launch, matching the iOS call site's timing even though
 * the two calls can't behave identically yet.
 *
 * RevenueCat purchase configuration is still a later porting pass; see
 * PORTING_PLAN.md.
 *
 * [BakeNotificationScheduler.init]/[BakeNotificationScheduler.createNotificationChannel]
 * are called eagerly here too (PORTING_PLAN.md step 7) — the Android
 * counterpart of `BreadIQApp.swift` having no equivalent step at all
 * (`UNUserNotificationCenter` needs no app-launch setup on iOS; a
 * notification *channel*, required on Android's API 26+, is the one
 * piece of setup Android needs that iOS doesn't).
 */
class BreadIQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseProvider.getInstance(this)
        BakeNotificationScheduler.init(this)
        BakeNotificationScheduler.createNotificationChannel(this)
    }
}
