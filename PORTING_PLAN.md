# BreadIQ Android — Porting Plan

Companion to `README.md`. Written 2026-08-13 while scaffolding this repo from the native iOS app at `~/Developer/iosBreadIQapp`. Read `iosBreadIQapp/PRODUCT_ROADMAP.md` and `iosBreadIQapp/ROADMAP.md` for the source app's own feature history and architecture notes — this doc only covers the Android-specific porting sequence.

## What exists as of this scaffold (2026-08-13)

- Gradle project (Kotlin DSL, version catalog), Compose + Material3, `applicationId com.BreadIQ.myapp`.
- 5-tab bottom navigation shell (Calculator, Recipes, Lexicon, Queue, Current Bake) with placeholder screen bodies — matches `MainTabView.swift`'s tab order/labels exactly.
- Brand color palette (light + dark) ported from `BreadIQColors.swift` into `ui/theme/Color.kt` + `Theme.kt`.
- Empty `core/`, `model/`, `data/` packages, ready for real files.
- Git repo initialized locally, remote set to the `BreadIQAndroidApp` GitHub repo you created manually (per the standing practice noted in `PRODUCT_ROADMAP.md` re: the earlier repo-backup incident — this session did not create or touch the GitHub repo itself, only pushed to the remote you already set up).

**Known limitation:** this scaffold was built in a network-sandboxed session that could not reach Maven Central / Google's Maven repo, so the Gradle build has not actually been compiled end-to-end yet. Versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01) are known-good combinations as of the source app's last iOS work, but the *first* real build needs to happen in Android Studio on your machine, where Gradle can actually resolve dependencies. If Android Studio's upgrade assistant suggests newer AGP/Compose versions on first open, that's expected and fine to accept.

## Suggested porting order

Mirrors `iosBreadIQapp/ROADMAP.md`'s own sequencing logic (foundational/offline-safety work before UI polish), adapted for what Android needs first:

1. **Data layer & models** — port `Models/*.swift` to `model/` as Kotlin data classes (`Recipe`, `BakeSession`, `BakeStep`, `FormulaResult`, `NutritionInfo`, etc.). Straightforward 1:1 port; these are plain value types on both platforms.
2. **Supabase client** — add `io.github.jan-tennert.supabase` (supabase-kt), the official Kotlin Multiplatform client, so Android talks to the *same* Supabase project as iOS (same URL/anon key as `SupabaseConfig.swift`). Port `SupabaseAuthService.swift` → `data/SupabaseAuthService.kt`, `AuthStore.swift` → a `ViewModel`.
3. **Auth screen + session gating** — port `AuthScreen.swift`; wire `MainActivity`'s currently-unconditional tab shell to gate on session state, matching `RootView.swift`.
4. **Core calculators** — `FormulaCalculator.swift`, `ProofTimeCalculator.swift`, `AutolyseCalculator.swift`, `NutritionCalculator.swift`, `CostEstimator.swift` are pure logic with no UIKit/SwiftUI dependency — the most mechanical part of the port. These plus `Models/BreadStyleDef.swift` and `Models/TechniqueGuideCatalog.swift` (large static data tables) unblock the Calculator tab, which is the app's primary screen (`CalculatorScreen.swift` is the largest file in the iOS app at ~140KB).
5. **Local persistence** — Room is the Android equivalent of SwiftData. Design the schema from `Models/*.swift` once step 1 is done; needed for the offline-first behavior `PRODUCT_ROADMAP.md` calls "a binary problem, not a spectrum improvement."
6. **Remaining tabs** — Recipes, Lexicon, Queue, Current Bake, in roughly that order (Lexicon is mostly static content; Queue/Current Bake depend on the bake-session engine, `BakeSessionEngine.swift` + `BakeStepAssembler.swift` + `ProofStageNarrator.swift`, which is the most complex single piece of business logic in the app).
7. **RevenueCat subscriptions** — `purchases-android` SDK exists and mirrors the iOS API closely; port `RevenueCatPurchasesService.swift` + `SubscriptionStore.swift` once there's a paywall screen to gate.
8. **Platform-specific features, evaluate Android equivalents case by case:**
   - Recipe camera scan/import (`RecipeScanner.swift`, `ImportAnalyzer.swift`) → CameraX + on-device text recognition (ML Kit).
   - Push notifications for bake timing (`BakeNotificationScheduler.swift`) → WorkManager + notification channels.
   - Calendar integration (`CalendarEventScheduler.swift`) → Android's `CalendarContract` provider.
   - XLSX export (`RecipeXLSXExporter.swift` + friends) → same file-format logic ports almost directly (it's manual XML/zip construction, not an iOS-only API); Storage Access Framework for the share/save step.
   - Safari extension / Chrome extension pairing-code import — Chrome extension already exists and is platform-agnostic on the backend side; the pairing-code redeem flow should work unchanged once Android has an authenticated Supabase session (step 2).

## Explicitly out of scope for Android v1 (per `PRODUCT_ROADMAP.md`)

- Combustion Inc. thermometer integration — still queued behind the Android port on iOS too.
- Sourdough — sequenced after Combustion integration on iOS; same ordering applies here once Android catches up to iOS feature parity.

## Design tokens reference

If `BreadIQColors.swift` changes, update `ui/theme/Color.kt` to match — there's no shared source of truth between the two codebases (a future improvement might be a shared design-tokens JSON, but that's not set up yet).
