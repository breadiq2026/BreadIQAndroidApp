# BreadIQ Android — Porting Plan

Companion to `README.md`. Written 2026-08-13 while scaffolding this repo from the native iOS app at `~/Developer/iosBreadIQapp`. Read `iosBreadIQapp/PRODUCT_ROADMAP.md` and `iosBreadIQapp/ROADMAP.md` for the source app's own feature history and architecture notes — this doc only covers the Android-specific porting sequence.

## What exists as of this scaffold (2026-08-13)

- Gradle project (Kotlin DSL, version catalog), Compose + Material3, `applicationId com.BreadIQ.myapp`.
- 5-tab bottom navigation shell (Calculator, Recipes, Lexicon, Queue, Current Bake) with placeholder screen bodies — matches `MainTabView.swift`'s tab order/labels exactly.
- Brand color palette (light + dark) ported from `BreadIQColors.swift` into `ui/theme/Color.kt` + `Theme.kt`.
- `model/` holds the plain-value-type data models (step 1). `data/` now holds the Supabase client + auth service layer (step 2/3, see below). `core/` is still empty, ready for the calculators (step 4).
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

1. **Data layer & models** — ✅ done 2026-08-12. Ported 18 of the iOS app's 20 `Models/*.swift` files to `model/` as Kotlin data classes: `TemperatureUnit`, `CurrentUser`, `TierInfo`, `SubscriptionPackage` (+ `SubscriptionIntroPrice`), `BakeStep`, `BakeSession`, `QueuedBakeConfig` (+ `FlourBlendEntry`), `QueuedBake` (+ `QueuedBakeStepPlan`), `ScheduledBake`, `Recipe`, `FormulaResult` (+ its 4 nested types), `ProofTimeResult` (+ `ProofStage`), `TechniqueGuide` (+ `BakingSection`), `LoafShape` (+ 46-entry catalog), `LexiconTerm` (+ `LexiconDetail`, 32-term catalog), `IngredientReferencePrice` (+ 22-entry catalog, `IngredientPriceOverride`), `NutritionInfo` (30-entry catalog), `BakeStepContent` (+ `NotifCopy`, its 4 lookup maps). `BreadStyleDef.swift` and `TechniqueGuideCatalog.swift` were deliberately **not** ported in this pass — the plan's own step 4 deferred those two specifically (large static data tables that unblock the Calculator tab alongside the pure-logic calculators); both are now done as part of step 4, see below. Each file's header comment names its iOS source, following the pattern the iOS app's own `Core/*.swift` files use for what they replaced from the Expo app.

   Porting notes (deliberate departures from the iOS shape, not oversights):
   - No SwiftData/Room annotations yet — these are plain value types for now, per this step's own framing; step 5 designs the real Room schema from these.
   - iOS's SwiftData `@Model` classes carry back-reference fields (`BakeStep.session`, `QueuedBakeConfig.queuedBake`) that exist only to satisfy SwiftData's `@Relationship(inverse:)` requirement. Dropped from the Kotlin port — a plain value type doesn't need a bidirectional relationship, and keeping one would make sibling data classes a reference cycle (auto-generated `equals`/`hashCode`/`toString` would recurse forever on a real cycle). Documented inline at each drop site.
   - `QueuedBakeConfig`'s iOS port hand-writes a `copy()` method (with a long incident writeup about why — a real SwiftData reference-aliasing crash it fixes). Kotlin `data class` already synthesizes an equivalent `copy()` for free, so no manual method was needed; the file notes this instead of silently dropping the context.
   - Swift `enum … : String` (raw-value enums) became `enum class X(val rawValue: String)` — keeps the exact wire string for later JSON/Supabase use while using idiomatic Kotlin case naming, matching the existing `BreadIQDestination` enum's style in `navigation/BreadIQDestinations.kt`.
   - Swift `Date` → `java.time.Instant` (minSdk 26 has full `java.time` desugaring support, no extra dependency needed).
2. **Supabase client** — ✅ done 2026-08-12, together with step 3. Added `io.github.jan-tennert.supabase` (supabase-kt) — Auth + Postgrest modules, via its BOM — plus `io.ktor:ktor-client-android` (its Android HTTP engine) and `kotlinx-serialization-json`. Ported `Core/SupabaseConfig.swift` → `data/SupabaseConfig.kt` (same URL/anon key — same Supabase project as iOS).

   **Dependency-version story, since it wasn't a straight pin-and-go** — full detail here since a future bump needs the same care:
   - Ktor 3.2.0 exactly hits a real Android packaging bug (D8/dex: `Space characters in SimpleName ... not allowed prior to DEX version 040`) — confirmed against JetBrains' own tracker, **KTOR-8583**, State "Fixed", Target release "3.2.1" (queried live via the YouTrack REST API, not assumed from a search summary).
   - supabase-kt versions are tightly coupled to specific Kotlin versions (confirmed by reading supabase-kt's own `CHANGELOG.md` directly, not inferred): 3.2.0–3.2.6 build against Kotlin 2.2.0/2.2.20/2.2.21; 3.3.0 (Jan 2026) jumps to Kotlin `3.3.0` and stays there through the latest release (3.7.0, Jul 2026) at time of writing. This repo's Kotlin was 2.0.21 (a Kotlin-metadata-incompatible mismatch with anything on the 2.2.x+ line, confirmed by a real compiler error: "Module was compiled with an incompatible version of Kotlin ... expected version is 2.0.0").
   - **Landed on: Kotlin bumped 2.0.21 → 2.2.0, supabase-kt pinned to `3.2.2`, Ktor to `3.2.2`** (both AGP 8.7.3 was already known-good with 2.0.21; jumping to whatever "Kotlin 3.3.0" turns out to require has no verified AGP 8.7.3 compatibility story and wasn't worth risking mid-way through an auth-plumbing step). This is the smallest bump off the Kotlin-2.2.x line that clears the KTOR-8583 dex bug.
   - **Real cost of that pin, not silently absorbed**: `Email.Config.data` (extra signup metadata) and `SessionManager.loadSession()`'s throwing, non-nullable contract both only exist from supabase-kt 3.6.0 onward (confirmed by diffing the actual source at both version tags, after being wrong twice by trusting `master`-branch source for API shapes instead of the pinned tag — see `KeystoreSessionManager.kt` and `SupabaseAuthService.kt`'s own comments for exactly where this shows up and how each is worked around). Revisit both workarounds whenever the pin moves to >= 3.6.0.

   Ported `Core/KeychainStore.swift` → `data/KeystoreSessionManager.kt`, implementing supabase-kt's `SessionManager` plugin point. **Deliberately not `androidx.security:security-crypto`'s `EncryptedSharedPreferences`**, which an earlier version of this plan suggested — checked current guidance first: Google deprecated the whole `security-crypto` library as of `1.1.0-beta01` ("Deprecated all APIs in favour of ... direct use of Android Keystore"), confirmed still true at its latest stable release (`1.1.0`, Jul 2025). Talks to `AndroidKeyStore` (AES/GCM) directly instead — one dependency fewer, and the same "talk to the platform security API directly" choice iOS's `KeychainStore` makes calling `Security.framework` directly. Stores the *entire* `UserSession` (not just the refresh token the way iOS's Keychain entry does) — a necessary difference, not stylistic: supabase-kt owns the whole session lifecycle through this interface, where iOS's hand-rolled `SupabaseAuthService` only needs the refresh token durably stored.

   Ported `Core/SupabaseAuthService.swift` + `Core/SupabaseSessionRefresh.swift` → `data/SupabaseAuthService.kt`, `Core/AuthErrorHumanizer.swift` → `data/AuthErrorHumanizer.kt`, `Stores/AuthStore.swift` → `viewmodel/AuthViewModel.kt` (`StateFlow<AuthUiState>` instead of Swift's `@Observable`). **A deliberate architecture departure from iOS, per this plan's own direction**: iOS's `SupabaseAuthService` is a hand-rolled `URLSession` REST client against GoTrue (a deliberate low-dependency choice made there); this port uses the official `supabase-kt` SDK instead, so token refresh/request-signing/response-parsing are the SDK's job, not hand-ported code. What DOES port 1:1 is the *behavior* layered on top — most notably that this project requires email confirmation before a session exists (`mailer_autoconfirm: false`, confirmed on the iOS port against the live project), so `signUp` returns `Result<CurrentUser?>` where `null` means "check your email," not an immediately-usable session.

   `AuthServicing`'s full surface (`AccountServicing`/`AuthLifecycleSyncing` seams, `UnconfiguredXxx` stub pattern) ported for structural fidelity with `AuthStore.swift`, since the stubs are trivial no-ops carrying zero real risk. **Deliberately narrower than iOS in one place**: no `completePasswordRecovery`. That exists on iOS to complete a password-reset deep link (`RootView`'s `pendingPasswordRecovery` + `SetNewPasswordScreen`, via `AppRouter`) — none of that is in scope this session (not in the source-file list this step was given, and Android has no deep-link/App-Links handling yet to ever call it). Rather than ship an unverified guess at `Auth.importSession`'s behavior for a recovery token pair with no way to test it live, it's left out entirely, noted in `AuthServicing.kt`'s own doc comment, to be added once a deep-link consumer exists.

3. **Auth screen + session gating** — ✅ done 2026-08-12, together with step 2. Ported `Screens/AuthScreen.swift` → `screens/AuthScreen.kt` — the first real (non-placeholder) screen in the app. Client validation (`AuthFormValidation`), the fixed non-theme-aware brand color palette, the sign-in/sign-up tabs, password show/hide toggle, forgot-password flow (surfacing the real result, matching the iOS port's own fix over the original Expo app's silent-always-"sent" bug), the post-signup "check your email" confirmation view, and the trial badge/disclosure text all ported faithfully. Not pixel-for-pixel on every SwiftUI-specific animation detail (e.g. the sliding tab-underline indicator is a static per-tab bar here, not a `GeometryReader`-animated one) — matched for intent, same reasoning as the tab-icon choices in `BreadIQDestinations.kt`.

   Wired session gating into `MainActivity`, replacing the previously-unconditional tab shell: a loading spinner while `AuthViewModel`'s initial session check is in flight, `AuthScreen` when there's no session, the tab shell when there is — matching `RootView.swift`'s three-state split. Deliberately narrower than the full `RootView.swift`, which this same split sits inside of on iOS: no password-recovery branch (see step 2's note above), no bake-session reconciliation or subscription-store login/logout binding (both depend on features — `BakeSessionEngine`, `SubscriptionStore` — that don't exist on Android yet either).

   Added the `INTERNET` permission to `AndroidManifest.xml` — Supabase Auth calls are the app's first real network traffic; nothing needed it before this step.
4. **Core calculators** — ✅ done 2026-08-13. `FormulaCalculator.swift`, `ProofTimeCalculator.swift`, `AutolyseCalculator.swift`, `NutritionCalculator.swift`, `CostEstimator.swift` are pure logic with no UIKit/SwiftUI dependency — the most mechanical part of the port so far. These plus `Models/BreadStyleDef.swift` and `Models/TechniqueGuideCatalog.swift` (large static data tables) unblock the Calculator tab, which is the app's primary screen (`CalculatorScreen.swift` is the largest file in the iOS app at ~140KB) — ported itself as its own later step, see the "Execution sequence" section below.

   **Data half done first** (the calculators read `BreadStyleCatalog`), its own commit: ported `Models/BreadStyleDef.swift` → `model/BreadStyleDef.kt` (`NumericRange`, `BreadStyleDef`, `BreadStyleCatalog` — all 12 styles, transcribed exactly) and `Models/TechniqueGuideCatalog.swift` → `model/TechniqueGuideCatalog.kt` (all 6 catalogs — `kneading`/`proofingGeneral` at 13 entries each, `shapingByShape`/`baking` at 7 each, `shapingByStyle`/`bakingByStyle` at 10 each — reusing the `TechniqueSection`/`BakingSection` types already ported in step 1, no new model types needed). Every entry preserved verbatim, including the pre-existing `whole_wheat` gap noted in both source files' own comments (present in the technique catalogs, absent from `BreadStyleCatalog` itself — not something to silently fix here).

   **Then the five calculators, second commit**, all under a new `core/` package:
   - `core/FormulaCalculator.kt` — `FormulaInput`, `FormulaCalculator` (baker's-percentage formula math: flour/water/salt/yeast weights, sweetener/egg/milk/butter enrichment, preferment split, per-flour breakdown for blended formulas). The 49-entry `loafFlourWeights` table is kept deliberately independent from `LoafShapeCatalog` (46 entries), matching the iOS port's own choice — 3 legacy keys (`pullman`/`rolls`/`sheet_pan`) exist only for old-recipe backward compat and would break silently if reconciled into the picker-facing catalog.
   - `core/ProofTimeCalculator.kt` — `ProofTimeInput`, `ProofTimeMath`, `ProofTimeCalculator` (the Arrhenius-kinetics thermal fermentation model: estimated initial dough temperature, time-stepped bulk/cold-retard temperature drift toward ambient/fridge, per-style/hydration/salt/sweetener/specialty-flour timing multipliers, the full per-loaf-shape piece-size-factor table). This is genuinely the most mathematically complex file ported so far — deliberately transcribed formula-by-formula rather than restructured, per this step's own instruction that precision matters more than idiomatic-Kotlin cleanup for baking science.
   - `core/AutolyseCalculator.kt` — `AutolyseTier`, `AutolyseGuidance`, `AutolyseCalculator` (tiered mixing/fermentation guidance for high whole-wheat/rye blends — BreadIQ-native, not a port of anything in the original Expo app either).
   - `core/CostEstimator.kt` — `BatchCost`, `CostEstimator` (per-batch/per-piece ingredient cost, reusing `IngredientReferencePriceCatalog` from step 1 rather than a second independent price table — the iOS port itself only reached that consolidated state after finding real drift between two previously-separate tables, documented in that catalog's own comment).
   - `core/NutritionCalculator.kt` — `BakeLossCatalog`, `NutrientTotals`, `BatchNutrition`, `NutritionCalculator` (whole-batch nutrient totals from real ingredient weights, scaled to per-100g-baked/per-piece/per-USDA-serving via a bake-loss-by-style catalog — BreadIQ-native, no source-app equivalent).

   **One porting note worth flagging explicitly**: Swift's `Double.rounded()` default mode is round-half-*away-from-zero*; Kotlin's `kotlin.math.round()` is round-half-*to-even* ("banker's rounding") — a real behavioral difference at exact `.5` boundaries that every one of these calculators hits constantly (durations, percentages, gram weights). Added a small `Double.swiftRounded()` extension in `FormulaCalculator.kt` (shared across the whole `core` package via Kotlin `internal` visibility) that replicates Swift's actual behavior, and used it everywhere the iOS source calls `.rounded()`, rather than reaching for `kotlin.math.round()` and silently drifting from the verified-against-server math on tie values.

   Not ported this step, unchanged from iOS's own scope there either: `ProofStageNarrator` (the prose/stage-array half of `calcProofTime()` — `ProofTimeMath` holds only the numeric half, matching the iOS port's own explicit scoping), and `CalculatorScreen.swift` itself (the UI — deliberately out of scope per this step's own instruction).
5. **Local persistence** — ✅ done 2026-08-14 (see "Execution sequence for steps 5-8" below, item 1 — built in that resequenced order, ahead of the Calculator screen). Room is the Android equivalent of SwiftData, replacing `BreadIQApp.swift`'s `ModelContainer`/`makeModelContainer()`.

   Identified which of step 1's ported models are real SwiftData `@Model` classes (need Room persistence) vs. plain `Codable` structs (bundled reference data, already correctly left as plain Kotlin data classes) by grepping the iOS `Models/` directory for `@Model` — confirmed against `makeModelContainer()`'s own `Schema([...])` list, which names exactly 7 types: `BakeSession`, `BakeStep`, `QueuedBake`, `QueuedBakeConfig`, `ScheduledBake`, `Recipe`, `IngredientPriceOverride`.

   New `data/local/` package, three commits (data classes, then DAOs, then database wiring — same split as step 4's data-then-logic pattern):
   - **Entities + converters**: one file per aggregate — `BakeSessionEntity.kt` (+ `BakeStepEntity`, FK `sessionId` `ON DELETE CASCADE`), `QueuedBakeEntity.kt` (+ `QueuedBakeConfigEntity`, whose primary key IS its foreign key `queuedBakeId` — matching the source's genuinely 1:1 relationship rather than adding a meaningless surrogate key), `ScheduledBakeEntity.kt`, `RecipeEntity.kt`, `IngredientPriceOverrideEntity.kt`, plus `RoomConverters.kt` (`Instant`<->epoch millis, the two raw-value enums<->their string, and JSON `TypeConverter`s via kotlinx.serialization for the small list-of-value-object fields SwiftData would otherwise serialize into a column for free).
   - **DAOs**: one per aggregate, get-by-id/observe-all (`Flow`)/upsert/delete, relying on `ON DELETE CASCADE` rather than manually deleting child rows (Room enables SQLite FK enforcement by default).
   - **`BreadIQDatabase.kt`** (the `@Database` class) + **`DatabaseProvider.kt`** (a double-checked-locking singleton, same shape as `SupabaseClientProvider.kt`) + wired into `BreadIQApplication.onCreate()`.

   **Entity-plus-mapper, not entity-IS-the-domain-model** — decided and documented in `BakeSessionEntity.kt`'s own doc comment, not a style preference: `BakeSession`/`QueuedBake` are nested value types (an embedded `List<BakeStep>`/a required `QueuedBakeConfig`), matching how SwiftData exposes a `@Relationship` to-many as a plain array property. Room has no equivalent — a one-to-many is always a separate joined table, never an embedded list column — so the domain models from step 1 literally can't be annotated `@Entity` as-is. Applied uniformly to every entity in this package once forced for the two relationship-holding ones, rather than mixing patterns depending on whether an individual model happens to have a relationship today.

   **A real relationship-direction fix, not a straight port**: SwiftData's `ScheduledBake` holds `@Relationship(deleteRule: .cascade) var queueItem: QueuedBake` — the *parent* (`ScheduledBake`) references the *child* it owns, and deleting the parent cascades to the child. Standard SQL `ON DELETE CASCADE` only fires in the other direction (deleting the *referenced* row cascades to rows holding the FK, never the reverse), so modeling this literally would mean deleting a `ScheduledBakeEntity` leaves its `QueuedBakeEntity` behind. Fixed by putting the FK on the owned side instead (`QueuedBakeEntity.scheduledBakeId`, nullable — null for the Queue tab's own independent entries) — same direction every other cascade in this schema already uses, and produces the identical observable delete behavior. Verified directly against the generated schema JSON after building, not just assumed from reading the annotations: `bake_steps -> bake_sessions CASCADE`, `queued_bakes -> scheduled_bakes CASCADE`, `queued_bake_configs -> queued_bakes CASCADE` — all three correct.

   **Assembling a full `ScheduledBake` needs 3 tables** (itself + its owned `QueuedBake` + that bake's own config). Rather than nesting a `@Relation` POJO inside another `@Relation`-bearing POJO (`QueuedBakeWithConfig`) — a real but less-common Room pattern not worth reaching for without being able to verify it end to end — `ScheduledBakeDao` does the assembly as two explicit queries inside one `@Transaction` instead.

   **`makeModelContainer()`'s error-recovery story has no Room equivalent yet — a documented scope decision, not an oversight.** SwiftData's `ModelContainer(for:configurations:)` validates and opens the store *eagerly* at construction time, so a corrupted store or failed migration throws right at app launch, somewhere iOS can catch it and branch `RootView` to `DataStoreErrorScreen` ("Try Again"/"Erase & Start Fresh"). Room's `.build()` is *lazy* — it returns a working reference immediately regardless of the on-disk file's condition, and any real failure only surfaces later, from whatever background-thread query first triggers it. There's no single construction-time success/failure point to gate a screen on the way iOS has. Since no screen queries this database yet (this step is persistence-layer-only, per its own scope), building a recovery UI now would mean designing it against a failure this app can't yet produce or test — revisit once a real screen/repository makes the first actual query and a genuine failure mode needs somewhere to surface to.

   Also deliberately NOT calling `fallbackToDestructiveMigration()` on the database builder — that's a foot-gun to leave configured by default, silently wiping local data on any future schema change whose migration was forgotten, rather than failing loudly (Room's actual default without it) the way this local-first data (no server backup for bake sessions/queued/scheduled bakes) should.

   Schema version 1, exported to `app/schemas/` (committed) via the classic `ksp { arg("room.schemaLocation", ...) }` form rather than the newer dedicated `androidx.room` Gradle plugin — one fewer plugin for the same result.

   **Dependency versions, same "verify against the pin, not latest" discipline as step 2** (all confirmed against live Maven metadata, not assumed): Room 2.8.4 (latest stable, requires Kotlin 2.0+ — no toolchain bump needed off the existing 2.2.0 pin), KSP `2.2.0-2.0.2` (the exact stable KSP build for this repo's pinned Kotlin version — KSP releases are tied 1:1 to a Kotlin version). Also added the `kotlin.plugin.serialization` compiler plugin for the first time — step 2's `kotlinx-serialization-json` dependency only ever decoded supabase-kt's own pre-compiled types before now; Room's JSON `TypeConverter`s are the first time this app needs `@Serializable` on its own classes (`FlourBlendEntry`, `QueuedBakeStepPlan`, both marked accordingly).

   Not wired into any screen yet, per this step's own scope — no screen needs Room until the Calculator screen / Recipes tab land next in the sequence.
6. **Remaining tabs** — Recipes, Lexicon, Queue, Current Bake, in roughly that order (Lexicon is mostly static content; Queue/Current Bake depend on the bake-session engine, `BakeSessionEngine.swift` + `BakeStepAssembler.swift` + `ProofStageNarrator.swift`, which is the most complex single piece of business logic in the app).
7. **RevenueCat subscriptions** — `purchases-android` SDK exists and mirrors the iOS API closely; port `RevenueCatPurchasesService.swift` + `SubscriptionStore.swift` once there's a paywall screen to gate.
8. **Platform-specific features, evaluate Android equivalents case by case:**
   - Recipe camera scan/import (`RecipeScanner.swift`, `ImportAnalyzer.swift`) → CameraX + on-device text recognition (ML Kit).
   - Push notifications for bake timing (`BakeNotificationScheduler.swift`) → WorkManager + notification channels.
   - Calendar integration (`CalendarEventScheduler.swift`) → Android's `CalendarContract` provider.
   - XLSX export (`RecipeXLSXExporter.swift` + friends) → same file-format logic ports almost directly (it's manual XML/zip construction, not an iOS-only API); Storage Access Framework for the share/save step.
   - Safari extension / Chrome extension pairing-code import — Chrome extension already exists and is platform-agnostic on the backend side; the pairing-code redeem flow should work unchanged once Android has an authenticated Supabase session (step 2).

## Execution sequence for steps 5-8 (refined 2026-08-14)

Steps 5-8 above describe *what* remains; this section describes *what order to
build it in and why*, worked out with Jeremy once the size/complexity of what
was left became clearer after step 4. Two principles drove the resequencing:

- **Dependency risk first.** Room (step 5) has to land before any more
  screens are built, not after -- Recipes, Queue, and Current Bake are all
  inherently persistence-backed (a queued bake or an active bake session has
  to survive a process kill), so building them against throwaway in-memory
  state first would mean rebuilding them once Room exists. The Calculator
  screen doesn't have that problem (it's live computation over the
  calculators from step 4), so it's unblocked either way. Likewise, Queue and
  Current Bake can't be ported at all without the bake-session engine
  (`BakeSessionEngine.swift` + `BakeStepAssembler.swift` +
  `ProofStageNarrator.swift`) -- that needs its own session *before* those two
  tabs, not bundled into "step 6."
- **Right-size each handoff session.** Step 4 (2,595 iOS lines) already
  needed 3 commits instead of 1. Step 8 as originally scoped bundles four
  unrelated verticals (camera scan/import ~93KB across 4 files, notifications,
  calendar, XLSX export ~55KB across 5 files) -- each gets its own session
  below rather than trying to satisfy "step 8" in one pass.

Concrete order:

1. **Room persistence** (step 5) -- ✅ done 2026-08-14. Foundational, unblocks everything below. Full writeup under step 5 above.
2. **Calculator screen** (`Screens/CalculatorScreen.swift`, ~140KB -- the
   single largest file in the app) -- ✅ done 2026-08-14, across 9 commits
   (bigger than the 2-3 estimated, in line with step 4's own "needed more
   commits than expected" precedent). Full 5-card wizard: live
   calculation via the calculators/static data from step 4, results
   display, Save Recipe + Queue for Later wired to the local Room DAOs
   from step 5, and the "Start Over" reset flow.
   - Shared UI infra new to this step: `ui/components/` (`Card`, `Badge`,
     `BreadIQButton`) -- no shared component package existed before this;
     established one now, kept general-purpose rather than
     Calculator-specific. `ui/theme/Color.kt` grew a `BreadIQColorTokens`
     interface + `LocalBreadIQColors` CompositionLocal so any composable
     can read the full BreadIQ palette the way SwiftUI views read
     `BreadIQColors.*` directly (Material3's `ColorScheme` only has slots
     for a handful of these tokens).
   - `core/TemperatureFormatting.kt`, `core/Haptics.kt` (reimplemented
     over `Vibrator`/`VibrationEffect` -- no Android equivalent of
     `UIImpactFeedbackGenerator` exists), `data/TemperatureUnitStore.kt`
     (no Settings screen exists yet to call `setUnit()`, so it only ever
     reads its Fahrenheit default this session).
   - `core/ProofStageNarrator.kt` -- pulled forward from step 4 (the
     bake-session-engine step below) after verifying it only depends on
     `ProofTimeInput`/`ProofTimeMath`/`TemperatureFormatting`, not
     `BakeSessionEngine`/`BakeStepAssembler`; approved directly
     mid-session since Calculator's `calculate()` needs its stage prose
     for the Proof Timeline card and Save Recipe's `proofMinutes` field.
   - `ui/calculator/FormulaResultView.kt`, `CalculatorAtoms.kt`
     (`CalcSectionLabel`/`CalcStepperRow`/`CalcChipRow`/`CalcSelectMenu`/
     `CalcInfoBox`/etc. -- `FlowWrap` is Compose's now-stable built-in
     `FlowRow` instead of a hand-rolled `Layout`), `CalculatorScreen.kt`
     (header/footer/card-switch shell), `CalculatorCards.kt` (Cards 0-3),
     `CalculatorResultsCard.kt` (Card 4 + `ProofTimelineCard`/
     `BakingGuideCard`/`MixingGuideCard`), `AutolyseGuidanceScreen.kt`,
     `NutritionAnalysisScreen.kt` -- the two user-selectable detail
     screens, nav-pushed rather than sheet-presented, sharing the
     Calculator route's own `CalculatorViewModel` instance (scoped to
     that back stack entry) instead of passing `FormulaResult`/
     `AutolyseGuidance` through route arguments.
   - `model/BakeUserTier.kt` -- split out of `BakeSessionEngine.swift`
     early since Calculator's tier gating needs it now; every tier read
     defaults to `FREE` until step 6 (RevenueCat) lands a real
     `SubscriptionStore` -- the correct fallback for "no subscription
     info available," not a gap.
   - `viewmodel/CalculatorViewModel.kt` -- one `StateFlow`-held
     `CalculatorUiState` mirroring the source's full `@State` list
     (`AuthViewModel`'s existing pattern), plus the ported action logic
     (`selectStyle`, `resetToDefaults`, flour-blend mutation,
     `calculate()`, `handleQueueBake()`, `handleSaveRecipe()`/
     `handleUpdateRecipe()`).
   - **Deferred, each rendered as a visibly disabled control rather than
     omitted** (per direct instruction): Import (needs `RecipeScanner`
     CameraX + ML Kit -- step 7), Settings gear (no `SettingsScreen` port
     exists yet -- step 8), Schedule Bake (needs the bake-session engine
     below, deferred regardless per direct instruction), Start Now (needs
     `BakeSessionEngine`/`BakeStepAssembler` -- step 4 below), Share
     Recipe (needs `RecipeXLSXExporter` -- step 7). Recipe backend sync
     (`BackendRecipeSyncService`) is not wired either -- Save/Update
     Recipe persist locally via Room only, matching this port's
     established "local mutation is real today, sync layers on top
     later" pattern.
   - **A real, non-obvious bug caught while porting, not reproduced**:
     both `AutolyseGuidanceScreen.swift` and `NutritionAnalysisScreen.swift`
     wrap their top info card in `Card(...).background(amber).clipShape(...)`,
     but `Card`'s own body already paints an opaque `BreadIQColors.card`
     background as part of the view being modified, and SwiftUI's
     `.background(_:)` places its argument BEHIND that already-opaque
     view -- the amber fill can never actually show through. Confirmed
     dead code in the source (traced through `Card.swift` directly), so
     it's not reproduced in the Android port.
3. **Recipes + Lexicon tabs** (`RecipesScreen.swift` ~24KB,
   `LexiconScreen.swift` ~16KB) -- ✅ done 2026-08-14, 2 commits. Much
   smaller than steps 2/4 (955 iOS lines combined), as expected.
   - `ui/lexicon/LexiconScreen.kt` -- pure browser over the static
     `LexiconCatalog` from step 1, no persistence. `LexiconSection`/
     `LexiconSearch` ported as pure functions. Category-pill <->
     scroll-position sync reworked for Compose rather than
     transliterated: the source measures section-header positions via a
     `GeometryReader`-backed `PreferenceKey` (a SwiftUI-specific
     workaround); this reads `LazyListState.layoutInfo.visibleItemsInfo`
     directly instead, the same "last header scrolled to or past the
     top edge" rule. The source's dead `pillScrollRef` (declared, never
     used to auto-scroll) is correctly not reproduced.
   - `viewmodel/RecipesViewModel.kt` + `ui/recipes/RecipesScreen.kt` --
     live `RecipeDao.observeAll()` list (sorted by `createdAt`
     descending), recipe card with stat pills, detail bottom sheet
     (formula/weights/flour-blend/pre-ferment/fermentation/notes
     sections, real delete via `RecipeDao.deleteById` with a
     confirmation dialog). Backend sync (`BackendRecipeSyncService`)
     stays a real, callable no-op, same deferral as Save Recipe's
     backend half from step 2.
   - **A real, unfinished handoff found in the iOS source, not just a
     gap in this port**: `RecipesScreen.swift`'s "Load into Calculator"
     sets `AppRouter.pendingRecipe` and switches tabs, but
     `CalculatorScreen.swift` never actually reads `pendingRecipe`
     anywhere (confirmed by grepping the whole file) -- the iOS app
     itself never finished wiring the consumption side. Built fresh for
     this port since there was nothing to transcribe:
     `CalculatorViewModel.loadFromRecipe(recipeId)` populates every
     field `Recipe` stores (recovering baker's percentages Recipe
     doesn't store directly -- salt, sweetener, pre-ferment flour/
     hydration -- from its stored gram weights, the inverse of
     `buildRecipe`'s own math), then auto-calculates and jumps to Card 4
     (both approved directly, no iOS precedent to follow either way).
     Fields `Recipe` never stores at all (egg/milk/butter %, malt,
     SpeedRun, cold-retard duration/temp, proof-environment temps,
     pretzel bath type) fall back to the matched style's own defaults --
     a pre-existing `Recipe` schema limitation, not something this port
     introduces. `MainActivity`'s `BreadIQApp()` composable carries the
     handoff itself (`pendingRecipeId`, a plain remembered value set by
     Recipes' `onLoadIntoCalculator` and consumed once by the Calculator
     route) -- the Compose counterpart of `AppRouter.pendingRecipe`,
     scoped to where it's actually needed rather than a new app-wide
     router class.
4. **Bake session engine** (`BakeSessionEngine.swift` + `BakeStepAssembler.swift`
   + `ProofStageNarrator.swift`, ~94KB combined) -- ✅ done 2026-08-14
   for the two remaining pieces, 2 commits (`ProofStageNarrator` was
   already pulled forward into the Calculator session -- see step 2's
   own writeup). Business logic only, no screens -- Queue/Current Bake
   (step 5) are what actually call this.
   - `core/BakeSessionEngine.kt` -- `RawBakeStep`, `BakeStartFailure`
     (a `BakeStartResult` sealed class stands in for the source's
     `Result<BakeSession, BakeStartFailure>`), and the state-machine
     transitions: `startBake`, `advanceStep`, `pauseBake`, `resumeBake`,
     `startStepTimer`, `extendStep`, `abandonBake`, and the wall-clock
     catch-up function `reconcile`. `BakeUserTier` (already a stub from
     the Calculator session) confirmed to match the source's
     free/basic/premium cases exactly.

     **API shape adapted for Kotlin, not transliterated**: the source
     mutates a SwiftData `@Model` class in place; every transition here
     instead takes a `BakeSession` and returns a NEW one via `.copy()`,
     matching every other state holder in this app (`CalculatorUiState`,
     `AuthUiState`, ...). `abandonBake` keeps the source's array-in/
     array-out shape (the one function that already had it).

     `reconcile` -- exactly the kind of function flagged as easy to
     half-port going in -- carries forward two real bugs the iOS
     source found in ITSELF and already fixed there (full write-up in
     both the source's and this port's own doc comments): a cascade-
     through-multiple-elapsed-steps fix (chaining `scheduledEndAt` off
     the step that just "ended" instead of off `now`, so a session that
     missed several steps' worth of wall-clock time while backgrounded
     correctly cascades through all of them in one call instead of
     stopping after one with a bogus fresh countdown), and a
     `manualStart`-step-silently-auto-started fix (checking `noTimer ||
     manualStart`, not `noTimer` alone, before auto-timing a newly-
     activated step). This port implements the already-corrected logic
     directly -- both fixes were already applied in the iOS source
     itself, nothing further to find or fix here.

     Actual notification scheduling stays out of scope (native-
     integration territory, its own later porting item, matching the
     source's own documented boundary) -- but the two pure decision
     functions inside that scheduling code (`ovenPreheatFireTime`,
     `wantsCoilFolds`/`coilFoldFireTimes`) are ported, since a future
     scheduling step will need them and they're fully testable
     independent of any notification API.
   - `core/BakeStepContentLookup.kt` -- the behavior half of the
     bake-step content system (`model/BakeStepContent.kt`, from step 1,
     is the data half): `stepCompleteNotif`'s three-tier fallback,
     `stepPrepNotif`'s Dutch-oven-styles special case,
     `ovenPreheatNotif`, `stepDescription`'s exact-then-prefix fallback.
   - `core/BakeStepAssembler.kt` -- turns a calculated formula + proof
     result + style into the ordered `List<RawBakeStep>` a bake session
     starts with (recipe-card text, ingredient-line breakdowns,
     preferment-vs-straight-dough step splitting, per-style boil/bath/
     mixing special-casing). Faithful to the source's own documented
     one-offs rather than "corrected" -- two deliberately-separate label
     tables confirmed as real drift from similarly-named tables
     elsewhere in this port, the final-dough salt ingredient line
     reading the top-level `formulaResult.saltWeight` instead of
     `finalMix.saltWeight` (equal in practice, but the source's own
     choice), and an unreachable-but-harmless multi-flour-without-a-
     breakdown branch kept for source fidelity. This is what Queue/
     Current Bake will call next session -- its output shape
     (`List<RawBakeStep>`, feeding straight into
     `BakeSessionEngine.startBake`) is locked in now.
5. **Queue + Current Bake tabs** (`QueueScreen.swift`, `CurrentBakeScreen.swift`,
   `BakeDetailScreen.swift`, `ScheduleModal.swift`) -- ✅ done 2026-08-14,
   4 commits. Wired last session's `BakeSessionEngine`/`BakeStepAssembler`
   into real UI and closed the loop on Calculator's "Start Now"/
   "Schedule Bake" buttons, which had rendered disabled since the
   Calculator session.
   - `core/BakeDetailFormatting.kt`, `core/ScheduledBakePlanner.kt`,
     `core/ScheduleModalFormatting.kt`, `core/QueueFormatting.kt` --
     the pure formatting/decision logic each screen leans on (countdown
     text, arc progress/color state, schedule-window validity, etc.).
   - `core/BakeNotificationScheduler.kt` -- every real call site across
     Queue/Current Bake/Bake Detail (`afterStart`, `cancel`, `snapshot`,
     `syncAfterMutation`, `cancelEverything`, ~a dozen sites) is wired
     in, but each actual scheduling call is a stub/no-op naming the
     dependency inline -- push notifications are still step 7, not done
     yet. `snapshot` is the one real (pure) function ported, since it's
     just data extraction.
   - `ui/components/{BakeProgressArc,BakeCard,ScheduledBakeCard,
     BakeStepRow}.kt` -- shared bake UI atoms, alongside `Card`/`Badge`/
     `BreadIQButton` from the Calculator session rather than
     screen-local. `BakeStepRow` has zero real call sites in either
     codebase but is ported anyway, matching the iOS port's own
     precedent of building it despite that.
   - `viewmodel/QueueViewModel.kt` + `ui/queue/QueueScreen.kt` --
     observes `QueuedBakeDao`/`BakeSessionDao` live; "Start Now" uses
     the bake's already-stored `QueuedBakeStepPlan` list directly (not
     reassembled through `BakeStepAssembler`) -- a genuine two-tier
     step-richness distinction the source itself makes, preserved as-is.
   - `viewmodel/CurrentBakeViewModel.kt` + `ui/currentbake/CurrentBakeScreen.kt`
     -- Scheduled/In Progress/Completed sections; `removeScheduled` is a
     single `ScheduledBakeDao.deleteById` relying on Room's declarative
     `ON DELETE CASCADE`, replacing the source's explicit leaf-first
     delete order (a SwiftData-crash workaround that Room doesn't need --
     write-up in the ViewModel's own doc comment).
   - `viewmodel/BakeDetailViewModel.kt` + `ui/bakedetail/BakeDetailScreen.kt`
     -- the largest file this session; live 1s-ticking timer/arc UI,
     step advance/pause/resume/extend, early-completion confirm,
     collapsible timeline. `session` is derived from the same
     `BakeSessionDao.observeAll()` query every other bake screen uses
     (filtered by id), mirroring the source's own `@Query` +
     `.first { }` pattern rather than a separate single-row query.
   - `viewmodel/ScheduleViewModel.kt` + `ui/schedule/ScheduleScreen.kt`
     -- real scheduling data-entry/validation UI; calendar event
     creation stays stubbed per direct instruction (`ScheduleModal.swift`'s
     own doc comment already flags it as a later phase). The picker is a
     Material3 `DatePicker`+`TimePicker` dialog rather than a direct
     port of SwiftUI's single inline `DatePicker(.graphical)`, since
     Compose has no equivalent combined widget. The source's two-step
     "Scheduled -- Open Calendar?" post-save flow is simplified to one
     "OK" confirmation shown directly by this screen, since the SwiftUI
     `.sheet`+`.alert` dismissal-timing bug it works around doesn't
     apply to a normal Compose nav destination.
   - `CalculatorViewModel.handleStartBake()`/`buildBakePlan()` and
     `CalculatorResultsCard.kt`'s "Start Now"/"Schedule Bake" buttons
     are now live instead of disabled. `MainActivity.kt`'s `NavHost`
     gained real Queue/Current Bake screens (replacing their
     `PlaceholderScreen` stand-ins, now deleted) plus the pushed Bake
     Detail and Schedule routes, and a `pendingSchedulePlan` handoff
     value mirroring the existing `pendingRecipeId` pattern.
6. **RevenueCat + Subscription screen** (`RevenueCatPurchasesService.swift`,
   `RevenueCatTierResolution.swift`, `SubscriptionStore.swift`,
   `SubscriptionScreen.swift`) -- independent vertical, slotted here so
   paywall gating exists before final polish.
7. **Platform integrations, one session each** (not bundled as "step 8"):
   - Camera scan/import -- `RecipeScanner.swift`, `ImportAnalyzer.swift`,
     `ImportModal.swift`, `ImportReviewScreen.swift` (~93KB combined, the
     biggest of these) -> CameraX + on-device text recognition (ML Kit).
     Split across two sessions given the size.
     - **Session A (core logic + camera/OCR capture) -- ✅ done 2026-08-14,
       1 commit.** Everything with no UI screen, ported 1:1: `core/IngredientClassifier.kt`,
       `core/IngredientLineParser.kt`, `core/IngredientDensityConverter.kt`
       (the keyword-classification/OCR-text-parsing/volume-to-gram
       pipeline `ImportModal.tsx` inlines from `lib/ingredient-densities`
       -- two confirmed mobile/web drift points ported faithfully, two
       confirmed genuine bugs fixed per direct instruction, all
       documented inline same as the source), `core/CalculatorImportMapping.kt`
       (the Safari-extension deep-link auto-apply heuristic -- fixed a
       confirmed real `* 10` vs. `* 1000` arithmetic bug and an `.egg`
       vs. `.egg + .eggYolk` summing inconsistency, both per direct
       instruction), `core/ImportServices.kt` (the shared scan/URL-import/
       staged-import seam types), and `core/ImportAnalyzer.kt` (the
       `POST /api/import/analyze` route logic ported to run fully
       offline -- confirmed `calcImportProofTime` is a genuinely separate
       formula from `ProofTimeCalculator.calculate`, not reusable, per
       that file's own doc comment).

       **`core/RecipeScanner.kt` + `ui/components/RecipeScanCapture.kt`
       -- a necessary two-file split the source doesn't have.** Every
       other `core/` file in this codebase is Compose-free pure Kotlin;
       camera/photo-picker capture is inherently Compose/Activity-bound
       on Android in a way iOS's `PHPickerViewController`/
       `UIImagePickerController` aren't (those present from any plain
       class via the app's root view controller). `core/RecipeScanner.kt`
       holds the real, UI-independent half -- resize-before-OCR (2000px
       max width, matching the source's own pipeline-parity note) and ML
       Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`,
       the bundled on-device variant -- confirmed offline, no network
       call at inference). `ui/components/RecipeScanCapture.kt` holds the
       real Compose-layer orchestration: the Photo Picker
       (`ActivityResultContracts.PickVisualMedia`, stable since
       `androidx.activity` 1.6.0 -- confirmed already well under this
       project's existing 1.9.3 pin, no version bump needed) for the
       library path, matching the source's own deliberate upgrade to
       `PHPickerViewController` for its no-permission-prompt property
       exactly (no `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` requested
       or declared anywhere); real CameraX capture
       (`camera-core`/`camera-camera2`/`camera-lifecycle`/`camera-view`
       1.5.1, newly added) with a live `PreviewView` for the camera path,
       requesting `CAMERA` lazily at the point of use -- the calendar
       session's lazy-permission pattern, not the notifications
       session's eager-at-launch one, since camera is only needed at an
       explicit "Scan Recipe" tap.

       **Not wired into any screen or nav destination this session** --
       per direct instruction, this was Session A of two. `rememberRecipeScanner`/
       `CameraCaptureScreen`/`RecipeScannerCameraOverlay` are real,
       complete, and ready for Session B to call from `ImportModal`,
       matching this codebase's existing "port it for real even without
       a caller yet" precedent (`ui/components/BakeStepRow.kt`).
     - **Session B (`ImportModal` UI + wiring) -- ✅ done 2026-08-14,
       1 commit. Camera scan/import is now fully done (both sessions).**
       Ports `Screens/ImportModal.swift` + `Core/ImportModalFormatting.swift`
       as a new `ui/calculator/ImportScreen.kt` -- the real 3-step
       ingredients -> environment -> results wizard, wired as a real
       Compose Navigation route (`BreadIQRoutes.IMPORT`), matching this
       codebase's established convention for iOS `.sheet` screens
       (`ScheduleScreen`/`NutritionAnalysisScreen`/`AutolyseGuidanceScreen`),
       not a `Dialog`. Calculator's "Import" header button is real now --
       no longer the dimmed stub from the Calculator session.

       **Confirmed genuinely self-contained, directly against the
       source**: `ImportScreen` takes only `onClose`, has its own
       `ImportViewModel`, and does NOT read from or write into
       `CalculatorViewModel` -- `ImportModal.swift` has no
       "Apply to Calculator" action anywhere; Step 3 just displays the
       computed `ImportAnalysisResult` in place.
       `core/CalculatorImportMapping.kt` (ported in Session A) is
       deliberately NOT used here -- it belongs to the separate Safari/
       Chrome-extension staged-import deep-link flow (see the new backlog
       note below), a different flow this session correctly leaves alone.

       **The one genuinely new piece of infrastructure**: `data/BackendApiClient.kt`
       + `data/BackendImportURLFetcher.kt` -- a minimal Ktor-based client
       for the one real, live, unauthenticated backend call `ImportModal`
       needs (`POST https://breadlab.replit.app/api/import/fetch-url`),
       confirmed NOT a stub (unlike the pairing-code feature). Reuses
       `io.ktor:ktor-client-android` (already a transitive dependency from
       the Supabase phase) with manual `kotlinx.serialization.json.Json`
       encode/decode rather than adding the Ktor `ContentNegotiation` +
       `kotlinx-json` plugins for this one call. Confirmed against the
       source's own doc comment that the route always responds 200 even
       on a logical failure -- both shapes decode into the same lenient
       DTO. Deliberately narrower than the source's full `BackendAPIClient`
       (no bearer-token attachment, no GET support) -- nothing else on
       Android needs a raw backend REST call yet; extend it if/when a
       second real call site shows up.

       Step 1's ingredient rows respect the existing app-wide
       `TemperatureUnitStore` for Step 2's temp fields, same pattern
       `CalculatorViewModel` already uses. The camera/library scan
       trigger itself stays at the Composable layer
       (`rememberRecipeScanner`/`RecipeScannerCameraOverlay` from Session
       A) -- `ImportViewModel` only consumes an already-resolved
       `RecipeScanOutcome`, matching the Context/Activity boundary every
       other camera/picker feature in this app already established.

   - **New backlog item, not yet planned or sequenced**: `ImportReviewScreen.swift`
     (551 lines) + `PendingImportsListScreen.swift` -- the Safari/Chrome-extension
     staged-import deep-link flow (`AppRouter.pendingImportToken`,
     populated by iOS's `onOpenURL`). Checked directly during the camera
     scan/import Session B handoff: Android has zero deep-link/App Links
     infrastructure anywhere in this app yet (no `intent-filter` for App
     Links, no URL-open handler) -- there is currently no way for a
     staged-import token to ever reach the app, so porting
     `ImportReviewScreen` before that infra exists would build a screen
     nothing can ever navigate to. `PendingImportsListScreen` is
     separately also backend-blocked on `GET /api/import/staged`'s list
     endpoint not existing yet (`core/ImportServices.kt`'s
     `UnconfiguredImportInboxFetcher` already documents this). Needs: (1)
     deep-link/App Links infra (new, nothing to build on), (2)
     `ImportReviewScreen` itself, (3) `PendingImportsListScreen` once its
     backend route ships. `core/CalculatorImportMapping.kt` is already
     ported and ready for whenever this lands.
   - Push notifications -- `BakeNotificationScheduler.swift` -- ✅ done
     2026-08-14, 1 commit. Real bodies for `core/BakeNotificationScheduler.kt`'s
     four stub functions (`afterStart`, `cancel`, `syncAfterMutation`,
     `cancelEverything`) -- no ViewModel call site changed, only the
     stub bodies. Reuses `BakeSessionEngine.ovenPreheatFireTime`/
     `wantsCoilFolds`/`coilFoldFireTimes` and `BakeStepContentLookup`'s
     copy, both already ported.

     **`AlarmManager` + a `BroadcastReceiver`, not `WorkManager`** --
     this plan's original note undersold it. The source deliberately
     upgraded to `UNCalendarNotificationTrigger`'s absolute-time firing
     specifically to avoid OS-scheduling drift; `WorkManager`'s
     minimum-latency/Doze-batching model would reintroduce exactly that
     drift, so `AlarmManager.setExactAndAllowWhileIdle` (via the new
     `core/BakeNotificationReceiver.kt`, which posts the actual
     notification at fire time) is the real equivalent. A notification
     channel is created once in `BreadIQApplication.onCreate()`.

     `BakeNotificationScheduler` holds a plain application `Context`
     field (`init(context)`, called from `BreadIQApplication.onCreate()`)
     rather than constructor injection -- none of the four ViewModels
     that call it carry a `Context` themselves (same boundary
     `Haptics.kt` already established), and unlike haptics this object
     has no Composable call site to fire a Context-needing action from.
     It also now holds a `BakeSessionDao` reference (two new narrow
     `UPDATE` queries, `updateStepNotificationIds`/`updateOvenPreheatNotifId`)
     to persist the ids scheduling produces -- the Android counterpart of
     the source mutating its SwiftData model in place and calling
     `modelContext.save()`, not a new layering violation (the source
     already blends persistence into this same file).

     `POST_NOTIFICATIONS` is requested once, early, from `MainActivity`
     rather than lazily at the first schedule call the way the source's
     `requestAuthorization()` is -- Android's runtime permission dialog
     needs a live Activity's `ActivityResultLauncher`, which no
     ViewModel has; every real `schedule()` call still checks permission
     itself first and silently skips otherwise, matching the source's
     own guard. `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`, which
     Play policy restricts to alarm-clock/calendar apps) is requested
     the same way, via a Settings deep link on API 33+ since it has no
     in-app dialog there; a revoked grant degrades real schedule calls
     to an inexact `AlarmManager.set` rather than dropping the
     notification, a middle ground the source has no equivalent for.

     **Known gap, deliberately not solved this session**: `AlarmManager`
     alarms don't survive a reboot (iOS local notifications do). Left as
     a TODO doc comment on `BakeNotificationScheduler.kt` -- a
     `BOOT_COMPLETED` receiver reconstructing every active session's
     pending notifications from Room is real additional scope for a
     future session, not built here.

     `ScheduledBakePlanner`'s `startReminderNotifId`/`startTimeNotifId`
     stay unused -- verified directly against
     `ScheduledBakePlanner.swift`, which never calls its own scheduling
     function for those either; a real, pre-existing iOS scope gap, not
     something this port introduced. `sweepOrphanedNotifications`
     (the source's startup orphan-cleanup sweep) also isn't ported --
     its call site, `RootView`'s bake-session reconciliation, doesn't
     exist on Android yet (see step 3's own narrower-than-`RootView`
     scope note above).
   - Calendar -- `CalendarEventScheduler.swift` -- ✅ done 2026-08-14,
     1 commit. Real `CalendarContract`/`ContentResolver` event creation
     via a new `core/CalendarEventScheduler.kt`, the Android counterpart
     of `EKEventStore`/`EKEvent` -- matches the source's own real
     `EventKit` upgrade over the original RN app's `calshow:` deep-link
     no-op (confirmed directly against `lib/calendar.ts`'s 34 lines and
     its own "no native module calls" comment -- the source's upgrade
     really is the first code that ever writes a real value into
     `ScheduledBake.calendarEventId`).

     `requestAccess()` is a permission *check* here (`READ_CALENDAR`/
     `WRITE_CALENDAR`), not a request -- same Context-boundary reasoning
     as `BakeNotificationScheduler`'s `POST_NOTIFICATIONS` split last
     session: Android's system permission dialog needs a live Activity's
     `ActivityResultLauncher`, which this object doesn't have. Unlike
     that session, though, this permission has exactly one interactive
     call site (`ScheduleScreen.kt`'s new "Add to Calendar" button), so
     it's requested lazily right there via `rememberLauncherForActivityResult`
     the moment the user taps it -- much closer to the source's own
     lazy `requestAccess()` call than the notifications session could
     manage with its many non-interactive call sites.

     `addBakeEvent`/`removeBakeEvent` are both `suspend`, dispatched on
     `Dispatchers.IO` (`ContentResolver` calls are blocking I/O). The
     default-calendar lookup (`store.defaultCalendarForNewEvents ??
     store.calendars(for: .event).first`) walks `CalendarContract.Calendars`
     preferring the primary calendar, falling back to the first
     genuinely writable one (`CALENDAR_ACCESS_LEVEL >= CAL_ACCESS_CONTRIBUTOR`,
     skipping read-only subscribed calendars like holidays) -- `null`
     surfaces as the same "No calendar is available" failure message the
     source uses.

     Object holds a plain application `Context` field (`init`, called
     from `BreadIQApplication.onCreate()`), the same shape
     `BakeNotificationScheduler` already established -- reached from two
     call sites (`ScheduleViewModel.addToCalendar`,
     `CurrentBakeViewModel.removeScheduled`) that don't otherwise carry
     a `Context`.

     **`ScheduleScreen.kt`'s post-schedule confirmation now matches the
     source's real two-button shape** (`CalculatorScreen.swift`'s
     `scheduledConfirmation` alert: "No Thanks" / "Add to Calendar"),
     replacing last session's placeholder single-"OK" dismiss -- that
     simplification was written before this session's real calendar
     scheduler existed to give the second button something to call. A
     separate "Couldn't Add to Calendar" dialog surfaces failures,
     matching the source's distinct `calendarEventError` alert. All
     three exit paths (dismiss/"No Thanks", the error dialog's OK, and a
     successful add) converge on a single `LaunchedEffect`-driven
     navigate-away rather than each calling the nav callback directly --
     avoids a double-navigation bug where both an explicit tap handler
     and a state-driven effect could each fire it once for the same
     outcome.

     **Fixed a genuine content-drift bug while wiring this in**:
     `ScheduleModalFormatting.scheduledConfirmationMessage` -- present in
     both codebases, called from neither -- had drifted from the real
     live copy (`CalculatorScreen.swift`'s own inline
     `scheduledConfirmation` text): the dead function's question ended
     "Open Calendar at your bake start time?" where the actual live
     alert ends "Add this to your Calendar?". Corrected the function's
     text to match reality, then wired it into the dialog for real
     instead of leaving two diverging copies of the same string.

     `ScheduledBakeDao` gained one narrow `updateCalendarEventId` query,
     same "patch one column" pattern as last session's
     `updateStepNotificationIds`/`updateOvenPreheatNotifId`.
     `CurrentBakeViewModel.removeScheduled` now actually calls
     `removeBakeEvent` for `bake.calendarEventId` when present -- this
     was the one still-silent no-op left in that function after last
     session's notification-cancel wiring, and was leaving orphaned
     calendar events behind on every cancel.
   - XLSX export ("Share Recipe") -- `RecipeXLSXBuilder.swift`,
     `RecipeXLSXExporter.swift`, `RecipeXLSXStyles.swift`,
     `XLSXWorkbook.swift`, `XLSXWorkbookXML.swift`, `TechniqueGuideLookup.swift`
     -- ✅ done 2026-08-14, 1 commit.

     **`java.util.zip.ZipOutputStream`/`ZipEntry` (standard JDK) replaces
     the source's `ZIPFoundation` SPM dependency** -- a real
     simplification the platform gives for free, not a gap; no new
     Gradle dependency needed for the zip-container half at all. The
     cell/style/worksheet model and its dedup logic
     (`core/XLSXWorkbook.kt`) port directly -- `XLSXStyle` is a Kotlin
     `data class` (structural `equals`/`hashCode`, the direct
     counterpart of the source's `Hashable` struct), usable as a `Map`
     key the same way, with every cell pre-scanned before `styles.xml`
     generation so its dedup indices are stable. `core/XLSXWorkbookXML.kt`
     ports the OOXML part templates as Kotlin extension functions on
     `XLSXWorkbook.Companion` (`XLSXWorkbook.stylesXML(...)` etc.) --
     same call-site shape as the source's own `static func`s split
     into a second file. The reserved-slot font/fill/border index-offset
     math (`i+1`/`i+2`/`i+1`) is load-bearing, ported exactly, not
     re-derived. `core/RecipeXLSXStyles.kt` ports the palette hex-for-hex
     and the row/section builder functions unchanged.
     `core/RecipeXLSXBuilder.kt` ports the section-by-section sheet
     population with the same gating conditions as the source
     (preferment tables only when a preferment is used, production
     schedule only when proof stages exist, banners only when their
     trigger fields are present) -- confirmed no embedded logo, per the
     source's own documented decision (this app has no wordmark asset).

     **A real bug caught and fixed while porting `RecipeXLSXExporter.kt`**:
     the flour-blend-breakdown gate must be `context.flourBlend.size > 1`
     (the raw list size), not "count of positive-percent entries > 1" --
     an initial draft used the latter, which would silently collapse a
     multi-entry blend with a zero-percent placeholder down to a single
     un-broken-down "Flour" row instead of the "Flour (total)" breakdown
     the source always shows once the blend has more than one slot.
     Caught before committing, not shipped.

     `core/TechniqueGuideLookup.kt` -- the one file in this item that
     wasn't already ported (`model/TechniqueGuideCatalog.kt`/`TechniqueGuide.kt`,
     the six real technique catalogs, were already ported and sitting
     unused on Android). Ports the `KNEADING[style] ?? KNEADING.artisan`-style
     fallback chain, the `soft_roll` shaping-steps filter (depends on the
     specific shape -- dinner roll vs. burger bun vs. hoagie vs.
     pullman), and the "Divide After Bulk"/"Portion After Bulk" row math
     (a real per-shape `piecesPerUnit` table -- bagels/pretzels divide
     into 6, most rolls into 13, `em_`-prefixed shapes into 12) exactly,
     including the documented real drift between this export-specific
     logic and the on-screen `DivideInfo` component's simpler version
     (which the source itself flags as NOT what this export should use).

     **The one genuinely new piece of infrastructure**: a real
     `FileProvider` (`AndroidManifest.xml` `<provider>` +
     `res/xml/file_paths.xml`, a `<cache-path>` scoped to the app's cache
     dir only) -- nothing in this app had one before. `CalculatorViewModel.shareRecipe()`
     writes the built `.xlsx` bytes to `Context.cacheDir`, gets a
     `content://` `Uri` via `FileProvider.getUriForFile`, and surfaces it
     through a new one-shot `CalculatorUiState.shareFileUri` field --
     same "state field the Composable observes once, then clears"
     convention as the existing `upgradePromptTitle`/`upgradePromptBody`
     pair, not a new Channel/SharedFlow mechanism. `CalculatorScreen.kt`
     launches a real `Intent.ACTION_SEND` chooser
     (`FLAG_GRANT_READ_URI_PERMISSION`, MIME type
     `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`)
     off that field, the direct analog of the source's
     `UIActivityViewController`.

     **Wired now, gated on the real (currently-always-FREE) `isPremium`
     check, per direct instruction -- did not wait for RevenueCat.**
     `CalculatorResultsCard.kt`'s "Share Recipe" button is no longer
     disabled; `userTier` stays hardcoded `BakeUserTier.FREE` (a separate,
     already-planned item), so today this always shows the upgrade
     prompt -- exactly matching what a FREE-tier user sees on iOS right
     now, and switching on automatically with zero further changes once
     RevenueCat lands, same reasoning already applied to
     `isPremium`/`isBasicOrPremium` elsewhere in that file.
   - Chrome extension pairing-code redeem -- smallest of these, just needs
     the authenticated Supabase session that already exists from step 2/3.
8. **Remaining smaller screens sweep** -- `SettingsScreen.swift`,
   `ConnectBrowserScreen.swift`, `IngredientCostsScreen.swift`,
   `SetNewPasswordScreen.swift`, `DataStoreErrorScreen.swift`,
   `PendingImportsListScreen.swift` -- leftover screens that don't fit
   cleanly into any vertical above.


## Explicitly out of scope for Android v1 (per `PRODUCT_ROADMAP.md`)

- Combustion Inc. thermometer integration — still queued behind the Android port on iOS too.
- Sourdough — sequenced after Combustion integration on iOS; same ordering applies here once Android catches up to iOS feature parity.

## Design tokens reference

If `BreadIQColors.swift` changes, update `ui/theme/Color.kt` to match — there's no shared source of truth between the two codebases (a future improvement might be a shared design-tokens JSON, but that's not set up yet).
