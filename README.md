# BreadIQ — Android

Native Android port of the [BreadIQ](https://breadiq.io) iOS app (`iosBreadIQapp`, native SwiftUI + SwiftData). Built with Kotlin + Jetpack Compose to match the iOS app's native-first approach rather than a cross-platform framework — see `PORTING_PLAN.md` for the reasoning and full sequencing plan.

- **Package / applicationId:** `com.BreadIQ.myapp` (matches the app already registered in Google Play Console — do not change without also updating the Play Console listing)
- **Min SDK:** 26 (Android 8.0) · **Target/compile SDK:** 35
- **UI:** Jetpack Compose + Material 3
- **Backend:** Supabase (same project as iOS — see `data/SupabaseConfig.kt`)

## Current state

Not a feature-complete app yet, but auth is real: sign in/up/out against the live Supabase project, session-gated navigation, and a secure on-device session store (Android Keystore-backed). Once signed in, the app shows the 5-tab shell (Calculator, Recipes, Lexicon, Queue, Current Bake — matching the iOS tab order) with placeholder screens beyond Auth and the BreadIQ brand color palette (light + dark) ported from `BreadIQColors.swift`. No local persistence or real calculator/recipe/etc. content yet.

## Getting started

1. Open this folder in Android Studio (Ladybug/Koala or newer), or build from the CLI with `./gradlew assembleDebug` — both paths are verified working (see `PORTING_PLAN.md`'s build-verification notes for the JDK/SDK setup a from-scratch machine needs for the CLI path).
2. Let Gradle sync — pulls the Android Gradle Plugin, Kotlin, AndroidX/Compose, and supabase-kt dependencies over your own network.
3. Run the `app` configuration on an emulator or device running Android 8.0+.

## Project layout

```
app/src/main/java/com/BreadIQ/myapp/
├── MainActivity.kt        # Entry point — session-gated: AuthScreen vs. the bottom-nav tab shell + NavHost (replaces RootView.swift/MainTabView.swift)
├── BreadIQApplication.kt  # App-level init (RevenueCat setup lands here later)
├── navigation/            # Tab + route definitions (replaces AppRouter.swift)
├── screens/                # One composable per screen (replaces Screens/*.swift) — AuthScreen is real, the 5 tabs are still placeholders
├── ui/theme/               # Colors ported from BreadIQColors.swift, Material3 theme
├── core/                   # (empty) — business logic, calculators, services land here (replaces Core/*.swift)
├── model/                  # Plain-value-type data models, ported from Models/*.swift — see PORTING_PLAN.md step 1
├── data/                   # Supabase client + auth service (SupabaseConfig, SupabaseClientProvider, KeystoreSessionManager, SupabaseAuthService, AuthErrorHumanizer) — see PORTING_PLAN.md step 2/3
└── viewmodel/              # AuthViewModel (replaces Stores/AuthStore.swift)
```

See `PORTING_PLAN.md` for what goes in `core/`, `model/`, `data/`, and `viewmodel/`, and the suggested porting order.
