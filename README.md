# BreadIQ — Android

Native Android port of the [BreadIQ](https://breadiq.io) iOS app (`iosBreadIQapp`, native SwiftUI + SwiftData). Built with Kotlin + Jetpack Compose to match the iOS app's native-first approach rather than a cross-platform framework — see `PORTING_PLAN.md` for the reasoning and full sequencing plan.

- **Package / applicationId:** `com.BreadIQ.myapp` (matches the app already registered in Google Play Console — do not change without also updating the Play Console listing)
- **Min SDK:** 26 (Android 8.0) · **Target/compile SDK:** 35
- **UI:** Jetpack Compose + Material 3
- **Backend:** Supabase (same project as iOS — see `SupabaseConfig.kt` once added)

## Current state

This is a scaffold, not a feature-complete app. It builds and runs a 5-tab shell (Calculator, Recipes, Lexicon, Queue, Current Bake — matching the iOS tab order) with placeholder screens and the BreadIQ brand color palette (light + dark) ported from `BreadIQColors.swift`. No backend, auth, data persistence, or real screen content yet.

## Getting started

1. Open this folder in Android Studio (Ladybug/Koala or newer).
2. Let Gradle sync — first sync will download the Android Gradle Plugin, Kotlin, and AndroidX/Compose dependencies over your own network (this couldn't be pre-verified from the sandboxed session that scaffolded this project; see `PORTING_PLAN.md`'s "Known limitation" note).
3. Run the `app` configuration on an emulator or device running Android 8.0+.

## Project layout

```
app/src/main/java/com/BreadIQ/myapp/
├── MainActivity.kt        # Entry point — bottom-nav shell + NavHost (replaces RootView.swift/MainTabView.swift)
├── BreadIQApplication.kt  # App-level init (Supabase/RevenueCat setup lands here later)
├── navigation/            # Tab + route definitions (replaces AppRouter.swift)
├── screens/                # One composable per screen (replaces Screens/*.swift) — currently placeholders
├── ui/theme/               # Colors ported from BreadIQColors.swift, Material3 theme
├── core/                   # (empty) — business logic, calculators, services land here (replaces Core/*.swift)
├── model/                  # (empty) — data models (replaces Models/*.swift)
└── data/                   # (empty) — Supabase client, repositories, local persistence
```

See `PORTING_PLAN.md` for what goes in `core/`, `model/`, and `data/`, and the suggested porting order.
