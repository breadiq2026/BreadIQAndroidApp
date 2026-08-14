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
4. **Core calculators** — ✅ done 2026-08-13. `FormulaCalculator.swift`, `ProofTimeCalculator.swift`, `AutolyseCalculator.swift`, `NutritionCalculator.swift`, `CostEstimator.swift` are pure logic with no UIKit/SwiftUI dependency — the most mechanical part of the port so far. These plus `Models/BreadStyleDef.swift` and `Models/TechniqueGuideCatalog.swift` (large static data tables) unblock the Calculator tab, which is the app's primary screen (`CalculatorScreen.swift` is the largest file in the iOS app at ~140KB) — still not ported itself; that's the next step.

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
   single largest file in the app). Logic is ready from step 4; likely needs
   2-3 commits given size, same pattern as step 4.
3. **Recipes + Lexicon tabs** (`RecipesScreen.swift` ~24KB,
   `LexiconScreen.swift` ~16KB) -- smaller, lower risk, and Recipes exercises
   the new Room layer end-to-end before anything more complex depends on it.
4. **Bake session engine** (`BakeSessionEngine.swift` + `BakeStepAssembler.swift`
   + `ProofStageNarrator.swift`, ~94KB combined) -- the hardest remaining
   business logic, its own session so problems surface before Queue/Current
   Bake assume a shape that turns out wrong.
5. **Queue + Current Bake tabs** (`QueueScreen.swift`, `CurrentBakeScreen.swift`,
   `BakeDetailScreen.swift`) -- unblocked by step 4 above.
6. **RevenueCat + Subscription screen** (`RevenueCatPurchasesService.swift`,
   `RevenueCatTierResolution.swift`, `SubscriptionStore.swift`,
   `SubscriptionScreen.swift`) -- independent vertical, slotted here so
   paywall gating exists before final polish.
7. **Platform integrations, one session each** (not bundled as "step 8"):
   - Camera scan/import -- `RecipeScanner.swift`, `ImportAnalyzer.swift`,
     `ImportModal.swift`, `ImportReviewScreen.swift` (~93KB combined, the
     biggest of these) -> CameraX + on-device text recognition (ML Kit).
   - Push notifications -- `BakeNotificationScheduler.swift` -> WorkManager +
     notification channels.
   - Calendar -- `CalendarEventScheduler.swift` -> `CalendarContract`.
   - XLSX export -- `RecipeXLSXBuilder.swift`, `RecipeXLSXExporter.swift`,
     `RecipeXLSXStyles.swift`, `XLSXWorkbook.swift`, `XLSXWorkbookXML.swift`
     (~55KB combined) -> same manual XML/zip construction ports directly;
     Storage Access Framework for the share/save step.
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
