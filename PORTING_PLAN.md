# BreadIQ Android — Porting Plan

Companion to `README.md`. Written 2026-08-13 while scaffolding this repo from the native iOS app at `~/Developer/iosBreadIQapp`. Read `iosBreadIQapp/PRODUCT_ROADMAP.md` and `iosBreadIQapp/ROADMAP.md` for the source app's own feature history and architecture notes — this doc only covers the Android-specific porting sequence.

## What exists as of this scaffold (2026-08-13)

- Gradle project (Kotlin DSL, version catalog), Compose + Material3, `applicationId com.BreadIQ.myapp`.
- 5-tab bottom navigation shell (Calculator, Recipes, Lexicon, Queue, Current Bake) with placeholder screen bodies — matches `MainTabView.swift`'s tab order/labels exactly.
- Brand color palette (light + dark) ported from `BreadIQColors.swift` into `ui/theme/Color.kt` + `Theme.kt`.
- `model/` now holds the plain-value-type data models (see step 1 below). `core/` and `data/` are still empty, ready for real files.
- Git repo initialized locally, remote set to the `BreadIQAndroidApp` GitHub repo you created manually (per the standing practice noted in `PRODUCT_ROADMAP.md` re: the earlier repo-backup incident — this session did not create or touch the GitHub repo itself, only pushed to the remote you already set up).

**Resolved 2026-08-12:** `./gradlew assembleDebug` now builds clean (`BUILD SUCCESSFUL`) — verified from the CLI, not just "should work in Android Studio." Two things this machine needed that Android Studio would normally set up silently on first launch, in case a future from-scratch clone hits the same gap before ever opening Android Studio:
- A JDK Gradle can actually use — the system had no `java` on `PATH` at all. Android Studio ships its own bundled JBR, but it's JDK 25, which this AGP 8.7.3 / Kotlin 2.0.21 combo can't parse (`java.lang.IllegalArgumentException: 25.0.2` from the Kotlin compiler's version parser). Installed `openjdk@17` via Homebrew instead and pointed `JAVA_HOME` at it.
- An Android SDK — none was installed yet (this machine hadn't opened Android Studio before). Installed via `brew install --cask android-commandlinetools`, accepted licenses, pulled `platform-tools`/`platforms;android-35`/`build-tools;35.0.0`, and wrote `local.properties` (gitignored, machine-specific — Android Studio would generate this automatically on first sync).

Two real scaffold bugs turned up and got fixed in the process:
- `screens/PlaceholderScreen.kt`'s KDoc comment contained the literal text `Screens/*.swift` — Kotlin block comments nest, so that `/*` opened a second nested comment that the doc comment's own closing `*/` only partially closed, silently swallowing the rest of the file into a comment (`Syntax error: Unclosed comment`, plus cascading "unresolved reference" errors in `MainActivity.kt` for the screens that comment-out hid). Reworded to avoid the literal `/*`.
- `.gitignore` only ignored a top-level `/build`, not per-module build output (`app/build/`), so a real build now leaves ~17MB of generated artifacts that `git add` would happily stage. Changed to `**/build/`.

Versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01) are confirmed working, not just "known-good combinations as of iOS's last work." If Android Studio's upgrade assistant suggests newer AGP/Compose versions on first open, that's still fine to accept.

## Suggested porting order

Mirrors `iosBreadIQapp/ROADMAP.md`'s own sequencing logic (foundational/offline-safety work before UI polish), adapted for what Android needs first:

1. **Data layer & models** — ✅ done 2026-08-12. Ported 18 of the iOS app's 20 `Models/*.swift` files to `model/` as Kotlin data classes: `TemperatureUnit`, `CurrentUser`, `TierInfo`, `SubscriptionPackage` (+ `SubscriptionIntroPrice`), `BakeStep`, `BakeSession`, `QueuedBakeConfig` (+ `FlourBlendEntry`), `QueuedBake` (+ `QueuedBakeStepPlan`), `ScheduledBake`, `Recipe`, `FormulaResult` (+ its 4 nested types), `ProofTimeResult` (+ `ProofStage`), `TechniqueGuide` (+ `BakingSection`), `LoafShape` (+ 46-entry catalog), `LexiconTerm` (+ `LexiconDetail`, 32-term catalog), `IngredientReferencePrice` (+ 22-entry catalog, `IngredientPriceOverride`), `NutritionInfo` (30-entry catalog), `BakeStepContent` (+ `NotifCopy`, its 4 lookup maps). `BreadStyleDef.swift` and `TechniqueGuideCatalog.swift` are deliberately **not** ported yet — the plan's own step 4 defers those two specifically (large static data tables that unblock the Calculator tab alongside the pure-logic calculators). Each file's header comment names its iOS source, following the pattern the iOS app's own `Core/*.swift` files use for what they replaced from the Expo app.

   Porting notes (deliberate departures from the iOS shape, not oversights):
   - No SwiftData/Room annotations yet — these are plain value types for now, per this step's own framing; step 5 designs the real Room schema from these.
   - iOS's SwiftData `@Model` classes carry back-reference fields (`BakeStep.session`, `QueuedBakeConfig.queuedBake`) that exist only to satisfy SwiftData's `@Relationship(inverse:)` requirement. Dropped from the Kotlin port — a plain value type doesn't need a bidirectional relationship, and keeping one would make sibling data classes a reference cycle (auto-generated `equals`/`hashCode`/`toString` would recurse forever on a real cycle). Documented inline at each drop site.
   - `QueuedBakeConfig`'s iOS port hand-writes a `copy()` method (with a long incident writeup about why — a real SwiftData reference-aliasing crash it fixes). Kotlin `data class` already synthesizes an equivalent `copy()` for free, so no manual method was needed; the file notes this instead of silently dropping the context.
   - Swift `enum … : String` (raw-value enums) became `enum class X(val rawValue: String)` — keeps the exact wire string for later JSON/Supabase use while using idiomatic Kotlin case naming, matching the existing `BreadIQDestination` enum's style in `navigation/BreadIQDestinations.kt`.
   - Swift `Date` → `java.time.Instant` (minSdk 26 has full `java.time` desugaring support, no extra dependency needed).
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
