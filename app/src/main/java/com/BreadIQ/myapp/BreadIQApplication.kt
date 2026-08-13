package com.BreadIQ.myapp

import android.app.Application

/**
 * App-level entry point. Supabase auth/session wiring (PORTING_PLAN.md
 * step 2) lives in `data/SupabaseClientProvider.kt` + `viewmodel/
 * AuthViewModel.kt` instead of here — `MainActivity`'s `AuthViewModelFactory`
 * builds the single app-wide `SupabaseClient` on first access (effectively
 * app launch, since it's the first thing `MainActivity.onCreate` needs),
 * so there's no separate eager-init step needed here the way iOS's
 * `BreadIQApp.swift` does it in its own app-launch hook. RevenueCat
 * purchase configuration is still a later porting pass; see
 * PORTING_PLAN.md.
 */
class BreadIQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
