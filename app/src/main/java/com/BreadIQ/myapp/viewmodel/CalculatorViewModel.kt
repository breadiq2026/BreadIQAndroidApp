package com.BreadIQ.myapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.core.BakeSessionEngine
import com.BreadIQ.myapp.core.BakeStartResult
import com.BreadIQ.myapp.core.BakeStepAssemblyInput
import com.BreadIQ.myapp.core.BakeStepAssembler
import com.BreadIQ.myapp.core.CalculatorFormatting
import com.BreadIQ.myapp.core.FormulaCalculator
import com.BreadIQ.myapp.core.FormulaInput
import com.BreadIQ.myapp.core.CalculatorImportApplier
import com.BreadIQ.myapp.core.CalculatorImportMapping
import com.BreadIQ.myapp.core.CalculatorImportMappingResult
import com.BreadIQ.myapp.core.ImportInboxFetchOutcome
import com.BreadIQ.myapp.core.ImportInboxFetching
import com.BreadIQ.myapp.core.ImportReviewFormatting
import com.BreadIQ.myapp.core.ImportStagingFetching
import com.BreadIQ.myapp.core.IngredientCostSyncing
import com.BreadIQ.myapp.core.StagedImportFetchOutcome
import com.BreadIQ.myapp.core.StagedImportListItem
import com.BreadIQ.myapp.core.StagedImportPayload
import com.BreadIQ.myapp.core.UnconfiguredImportInboxFetcher
import com.BreadIQ.myapp.core.UnconfiguredImportStagingFetcher
import com.BreadIQ.myapp.core.UnconfiguredIngredientCostSyncService
import com.BreadIQ.myapp.core.ProofStageNarrator
import com.BreadIQ.myapp.core.ProofTimeCalculator
import com.BreadIQ.myapp.core.ProofTimeInput
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.RecipeXLSXExportContext
import com.BreadIQ.myapp.core.RecipeXLSXExporter
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.data.BackendApiClient
import com.BreadIQ.myapp.data.BackendImportInboxFetcher
import com.BreadIQ.myapp.data.BackendImportStagingFetcher
import com.BreadIQ.myapp.data.BackendIngredientCostSyncService
import com.BreadIQ.myapp.data.SupabaseAuthService
import com.BreadIQ.myapp.data.SupabaseClientProvider
import com.BreadIQ.myapp.data.TemperatureUnitStore
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.QueuedBakeConfigEntity
import com.BreadIQ.myapp.data.local.QueuedBakeDao
import com.BreadIQ.myapp.data.local.RecipeDao
import com.BreadIQ.myapp.data.local.IngredientPriceOverrideDao
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.data.local.toEntity
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.BreadStyleCatalog
import com.BreadIQ.myapp.model.BreadStyleDef
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FlourBlendTemplate
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.LoafShape
import com.BreadIQ.myapp.model.LoafShapeCatalog
import com.BreadIQ.myapp.model.PrefermentInfo
import com.BreadIQ.myapp.model.ProofTimeResult
import com.BreadIQ.myapp.model.Recipe
import com.BreadIQ.myapp.model.SweetenerOption
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.model.YeastOption
import com.BreadIQ.myapp.model.calculatorSweetenerTypes
import com.BreadIQ.myapp.model.calculatorYeastTypes
import com.BreadIQ.myapp.model.prefermentTypes
import com.BreadIQ.myapp.model.QueuedBake
import com.BreadIQ.myapp.model.QueuedBakeConfig
import com.BreadIQ.myapp.model.QueuedBakeStepPlan
import com.BreadIQ.myapp.ui.calculator.ImportReviewOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The `@State` list of the iOS app's `Screens/CalculatorScreen.swift`, as
 * one immutable state object for `StateFlow` — same "one data class +
 * `.copy()`" shape [com.BreadIQ.myapp.viewmodel.AuthUiState] already
 * established for this codebase.
 *
 * **Fields that only exist to support a still-deferred flow are left out
 * entirely rather than carried as dead state** — they'll be added back
 * when that flow is actually built: `scheduleBakePlan`/
 * `scheduledConfirmation`/`calendarEventError` (Schedule),
 * `recipeSyncErrorMessage` (backend recipe sync). The `import*`/`pending*`
 * fields are real now — see their own doc comments (Import Review
 * screen session). `showNutritionAnalysis`/`showAutolyseGuidance` are also
 * left out — those two map to Compose Nav destinations for this port
 * (see `AutolyseGuidanceScreen`/`NutritionAnalysisScreen`'s own porting
 * step) rather than SwiftUI `.sheet(isPresented:)` booleans threaded
 * through this state class.
 */
data class CalculatorUiState(
    // Card carousel
    val cardIndex: Int = 0,

    // Style & shape
    val selectedStyle: BreadStyleDef = BreadStyleCatalog.all[0],
    val selectedShapeValue: String = BreadStyleCatalog.all[0].defaultShape,

    // Batch
    val numLoaves: Double = 1.0,
    val baguetteMod: Double = 1.0,
    val focacciaScale: Double = 1.0,

    // Flour blend
    val flourBlend: List<FlourBlendEntry> = listOf(FlourBlendEntry(type = "bread", percent = 100.0)),

    // Main sliders
    val hydration: Double = BreadStyleCatalog.all[0].hydrationIdeal,
    val fat: Double = BreadStyleCatalog.all[0].defaultFat ?: 0.0,
    val salt: Double = 2.0,
    val yeast: Double = BreadStyleCatalog.all[0].yeastIdeal,
    val yeastType: String = "instant",
    val showAdvancedFormula: Boolean = false,

    // Sweetener
    val sweetenerType: String? = null,
    val sweetenerPct: Double = 3.0,

    // Enriched ingredients
    val eggsPercent: Double = 40.0,
    val milkPercent: Double = 21.0,
    val butterPercent: Double = 60.0,
    val liquidType: String = "water",
    val dairyDisplayName: String? = null,

    // Diastatic malt
    val maltPct: Double = 0.0,

    // Pre-ferment
    val usePrefermant: Boolean = false,
    val prefermentType: String = "poolish",
    val prefermentFlourPct: Double = 40.0,
    val prefermentHydration: Double = 100.0,

    // SpeedRun
    val isSpeedRun: Boolean = false,
    val originalYeast: Double? = null,

    // Proof / environment
    val waterTempF: Double = 75.0,
    val ambientTempF: Double = 72.0,
    val useColdRetard: Boolean = false,
    val coldRetardHours: Double = 12.0,
    val coldRetardTempF: Double = 38.0,
    val finalProofTempF: Double = 72.0,
    val isHumidityMode: Boolean = false,
    val relativeHumidity: Double = 50.0,
    val pretzelBathType: String = "baked_baking_soda",

    // Results
    val formulaResult: FormulaResult? = null,
    val proofResult: ProofTimeResult? = null,
    val loading: Boolean = false,
    val startingBake: Boolean = false,
    /** Consumed once by the screen's own nav handoff to Bake Detail once Start Now succeeds. */
    val startedSessionId: String? = null,
    /**
     * Consumed once by the screen's own `ACTION_SEND` share-sheet launch
     * once [CalculatorViewModel.shareRecipe] finishes writing the `.xlsx`
     * file — same one-shot-then-cleared convention as [startedSessionId]/
     * [upgradePromptTitle] below.
     */
    val shareFileUri: Uri? = null,

    // Save recipe
    val recipeName: String = "",
    val saving: Boolean = false,
    val savedId: Int? = null,
    val loadedFromRecipeId: Int? = null,
    val loadedFromRecipeName: String = "",

    // Import (browser-extension deep link -> single staged import,
    // IMPORT_REVIEW_SPEC.md). `importReview` non-null means "show
    // ImportReviewScreen instead of the normal card content" — the
    // direct analog of the source's own `importReview: (payload:
    // StagedImportPayload, mapping: CalculatorImportMapping)?`.
    val importFetching: Boolean = false,
    val importError: String? = null,
    val importReview: Pair<StagedImportPayload, CalculatorImportMapping>? = null,
    /**
     * Chrome-extension companion's pending-imports inbox (`GET
     * /api/import/staged`) — the cross-device counterpart to the
     * Safari-extension deep link the fields above already handle.
     * Refreshed by [refreshPendingStagedImports] (launch + foreground);
     * [CalculatorScreen]'s import status banner surfaces the count and
     * presents `PendingImportsListScreen` to pick one, which routes
     * through [selectStagedImport] into the SAME [fetchStagedImport]
     * pipeline above, unchanged.
     */
    val pendingStagedImports: List<StagedImportListItem> = emptyList(),
    /**
     * Set true once [applyImportReviewOutcome] confirms an import —
     * drives the mandatory auto-save in [calculate] (`IMPORT_REVIEW_SPEC.md`
     * §5) and is cleared after the first attempt so re-calculating within
     * the same session doesn't re-save. The three `pending*` fields below
     * carry the review screen's format note and the staged payload's
     * source info through to that auto-save, since neither is known yet
     * at [Recipe]-construction time inside [handleSaveRecipe]'s existing
     * shape — same lifetime pattern already used for [recipeName] (set
     * well before [calculate], read inside it).
     */
    val isImportSession: Boolean = false,
    val pendingImportSourceURL: String? = null,
    val pendingImportSourceName: String? = null,
    val pendingFormatNote: String? = null,

    // Alerts — plain nullable message state; the Composable screen (task
    // #6/#7) renders these as alert dialogs and clears them back to null.
    val upgradePromptTitle: String? = null,
    val upgradePromptBody: String? = null,
    val startError: String? = null,
    val queueError: String? = null,
    val queueSuccessShown: Boolean = false,
    val showResetConfirm: Boolean = false,

    /**
     * **New — live Supabase reference prices for the Cost Analysis
     * card, not part of the original state list.** Always empty this
     * session: `IngredientCostSyncing`/`BackendIngredientCostSyncService`
     * (the network sync that would populate this) isn't ported yet — see
     * that interface's iOS doc comment. [CostEstimator.calcBatchCost]'s
     * own price-resolution precedence (`customPrices` then `refPrices`
     * then the bundled [com.BreadIQ.myapp.model.IngredientReferencePriceCatalog]
     * fallback) already degrades correctly when this is empty: every
     * non-overridden ingredient just uses the bundled catalog, exactly
     * the fallback behavior that catalog exists for.
     */
    val serverReferencePrices: Map<String, Double> = emptyMap(),

    /** Local, user-editable per-ingredient price overrides — real, DB-backed (no network dependency), unlike [serverReferencePrices] above. */
    val customIngredientPrices: Map<String, Double> = emptyMap(),

    /** Mirrors the source's live `@Query private var recipes: [Recipe]` — kept current from [RecipeDao.observeAll]. */
    val recipes: List<Recipe> = emptyList(),

    /** Mirrors the source's `queuedBakeCount` computed property (backed by its own `@Query`). */
    val queuedBakeCount: Int = 0,

    /** Mirrors the source's live `@Query private var sessions: [BakeSession]` — needed by [BakeSessionEngine.startBake]'s own active-bake-limit gate. */
    val sessions: List<BakeSession> = emptyList(),

    /**
     * Mirrors the source's `@Environment(TemperatureUnitStore.self)` —
     * kept live from the shared [TemperatureUnitStore] (see that class's
     * own doc comment for the reactivity fix): `SettingsScreen`'s
     * temperature-unit row calls [TemperatureUnitStore.setUnit] and this
     * field updates immediately, no relaunch needed.
     */
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,

    /**
     * Mirrors the source's `@Environment(SubscriptionStore.self)`-derived
     * `userTier` — real now, kept live from the shared
     * [com.BreadIQ.myapp.viewmodel.SubscriptionViewModel] (Activity-scoped,
     * threaded in via [CalculatorViewModelFactory]) rather than hardcoded.
     * Defaults to [BakeUserTier.FREE] only for the brief window before
     * that store's first tier resolution completes — the same honest
     * "no subscription information available yet" fallback
     * [BakeUserTier]'s own doc comment already describes, now genuinely
     * transient instead of permanent.
     */
    val userTier: BakeUserTier = BakeUserTier.FREE,
)

// MARK: - Derived/computed properties (source: private computed vars on the View struct)

val CalculatorUiState.isPremium: Boolean get() = userTier == BakeUserTier.PREMIUM
val CalculatorUiState.isBasicOrPremium: Boolean get() = userTier == BakeUserTier.BASIC || userTier == BakeUserTier.PREMIUM
val CalculatorUiState.maxFlour: Int get() = if (isPremium) 5 else if (isBasicOrPremium) 3 else 1
val CalculatorUiState.isPretzel: Boolean get() = selectedStyle.value == "pretzel"

val CalculatorUiState.availableShapes: List<LoafShape>
    get() = LoafShapeCatalog.all.filter { it.styles.contains(selectedStyle.value) }

val CalculatorUiState.selectedShape: LoafShape?
    get() = LoafShapeCatalog.all.firstOrNull { it.value == selectedShapeValue } ?: availableShapes.firstOrNull()

val CalculatorUiState.currentShapeName: String
    get() = selectedShape?.label ?: selectedShapeValue

val CalculatorUiState.hydAdj: Double get() = CalculatorFormatting.computeHydrationAdj(flourBlend)
val CalculatorUiState.yeastMeta: YeastOption get() = calculatorYeastTypes.firstOrNull { it.value == yeastType } ?: calculatorYeastTypes[0]
val CalculatorUiState.sweetMeta: SweetenerOption? get() = calculatorSweetenerTypes.firstOrNull { it.value == sweetenerType }
val CalculatorUiState.prefInfo: PrefermentInfo? get() = prefermentTypes[prefermentType]
val CalculatorUiState.autolyseGuidance: com.BreadIQ.myapp.core.AutolyseGuidance get() = com.BreadIQ.myapp.core.AutolyseCalculator.calculate(flourBlend)

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift`.
 *
 * Owns the 5-card formula wizard's state and pure business logic
 * (style/shape/flour-blend mutation, live formula/proof calculation,
 * Queue-for-Later, Save/Update Recipe). Delegates persistence to the
 * injected DAOs (Room's counterpart to SwiftData's `modelContext`).
 *
 * **`Haptics` calls are deliberately absent from every action below** —
 * per [com.BreadIQ.myapp.core.Haptics]'s own doc comment, firing one
 * needs a `Context`, so the Composable screen (not this ViewModel) fires
 * the haptic immediately after calling the action whose iOS counterpart
 * fired one at the same point (`calculate()`: light impact;
 * `handleQueueBake()`/`handleSaveRecipe()`/`handleUpdateRecipe()`:
 * success notification).
 *
 * **Scope departures carried over from the iOS source, or new to this
 * port — not silent:**
 * - `bulkBatchMode`/`batchWeightLbs`/`mixerQuarts` omitted (iOS's own
 *   departure — no UI anywhere in the source ever sets `bulkBatchMode`).
 * - `isSourdough` hardcoded `false` in [ProofTimeInput] (iOS's own
 *   departure — no reachable UI control for it).
 * - `handleOpenScheduleModal()`/Schedule Bake's actual submit still
 *   lives on the Schedule screen's own `ScheduleViewModel`, not here —
 *   this ViewModel only builds the plan the Schedule screen needs
 *   ([buildBakePlan]), matching the iOS source's own `currentQueuedBakePlan()`
 *   / `ScheduleModal` split. `handleShareRecipe()` (needs
 *   `RecipeXLSXExporter`), `autoSaveImportedRecipe()` (Import, deferred)
 *   are left unported — each needs a dependency outside this session's
 *   scope. `handleQueueBake()`/`handleStartBake()` are fully wired
 *   below, now that [BakeSessionEngine]/[BakeStepAssembler] both exist.
 */
class CalculatorViewModel(
    private val recipeDao: RecipeDao,
    private val queuedBakeDao: QueuedBakeDao,
    private val ingredientPriceOverrideDao: IngredientPriceOverrideDao,
    private val bakeSessionDao: BakeSessionDao,
    temperatureUnitStore: TemperatureUnitStore,
    /** Only used by [shareRecipe] (writing the exported `.xlsx` to cache + building its `FileProvider` `Uri`) — an application [Context], never a leak-risk Activity one, same as every other app-Context-holding object in this codebase. */
    private val appContext: Context,
    /** The shared, Activity-scoped [SubscriptionViewModel] — see [CalculatorUiState.userTier]'s own doc comment. */
    private val subscriptionViewModel: SubscriptionViewModel,
    /**
     * `GET /api/reference-prices`, for [CalculatorUiState.serverReferencePrices].
     * **A fresh, independent instance — not shared with
     * [IngredientCostsViewModel]'s own, separate
     * [com.BreadIQ.myapp.core.IngredientCostSyncing] instance/fetch.**
     * Matches a real, deliberate duplication in the iOS source: the
     * Calculator screen and the Ingredient Costs screen each
     * independently fetch this same public endpoint, with their own
     * local state — not a gap to consolidate, see [CalculatorViewModelFactory]'s
     * own doc comment.
     */
    private val ingredientCostSyncService: IngredientCostSyncing = UnconfiguredIngredientCostSyncService(),
    /** `GET /api/import/staged/:token` — the browser-extension deep-link handoff. See [fetchStagedImport]. */
    private val importStagingFetcher: ImportStagingFetching = UnconfiguredImportStagingFetcher,
    /** `GET /api/import/staged` (list) — the Chrome-extension companion's pending-imports inbox. See [refreshPendingStagedImports]. */
    private val importInboxFetcher: ImportInboxFetching = UnconfiguredImportInboxFetcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState(temperatureUnit = temperatureUnitStore.unit.value))
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            temperatureUnitStore.unit.collect { unit ->
                update { it.copy(temperatureUnit = unit) }
            }
        }
        viewModelScope.launch {
            recipeDao.observeAll().collect { entities ->
                update { it.copy(recipes = entities.map { entity -> entity.toDomain() }) }
            }
        }
        viewModelScope.launch {
            queuedBakeDao.observeQueueTab().collect { rows ->
                update { it.copy(queuedBakeCount = rows.size) }
            }
        }
        viewModelScope.launch {
            ingredientPriceOverrideDao.observeAll().collect { overrides ->
                update { it.copy(customIngredientPrices = overrides.associate { o -> o.ingredientKey to o.pricePerGram }) }
            }
        }
        viewModelScope.launch {
            bakeSessionDao.observeAll().collect { rows ->
                update { it.copy(sessions = rows.map { row -> row.toDomain() }) }
            }
        }
        // GET /api/reference-prices, once — populates the previously-dead
        // serverReferencePrices field so CostAnalysisCard's calcBatchCost
        // call actually reflects live server prices, not just the
        // bundled catalog. See this ViewModel's own constructor doc
        // comment on why this is a separate fetch from IngredientCostsViewModel's.
        viewModelScope.launch {
            val fetch = ingredientCostSyncService.fetchReferencePrices()
            if (fetch != null) update { it.copy(serverReferencePrices = fetch.prices) }
        }
        viewModelScope.launch {
            subscriptionViewModel.uiState.collect { subState ->
                val tier = subState.tierInfo?.tier?.let { rawValue -> BakeUserTier.entries.firstOrNull { it.rawValue == rawValue } }
                update { it.copy(userTier = tier ?: BakeUserTier.FREE) }
            }
        }
    }

    /** General-purpose field mutation for controls with no logic beyond "set this one value" (sliders, steppers, toggles, text fields). */
    fun update(transform: (CalculatorUiState) -> CalculatorUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun goToCard(index: Int) = update { it.copy(cardIndex = index.coerceIn(0, 4)) }
    fun nextCard() = update { it.copy(cardIndex = (it.cardIndex + 1).coerceAtMost(4)) }
    fun previousCard() = update { it.copy(cardIndex = (it.cardIndex - 1).coerceAtLeast(0)) }

    /** `showUpgradeAlert()`. The source's alert offers "See Plans" (→ the subscription paywall) alongside "Maybe later" — `CalculatorScreen.kt`'s own alert now shows both real buttons, matching the source exactly. */
    fun showUpgradeAlert(title: String, body: String) = update { it.copy(upgradePromptTitle = title, upgradePromptBody = body) }

    // MARK: - Actions

    fun selectStyle(style: BreadStyleDef) {
        update { s ->
            val newShapes = LoafShapeCatalog.all.filter { it.styles.contains(style.value) }
            val defaultShape = newShapes.firstOrNull { it.value == style.defaultShape } ?: newShapes.firstOrNull()

            var sweetenerType = s.sweetenerType
            var sweetenerPct = s.sweetenerPct
            if (style.defaultSugar != null) {
                sweetenerType = style.defaultSugar
                sweetenerPct = style.defaultSugarPct ?: 4.0
            } else if (style.value != "soft_roll") {
                sweetenerType = null
            }

            var eggsPercent = s.eggsPercent
            var milkPercent = s.milkPercent
            var butterPercent = s.butterPercent
            var liquidType = s.liquidType
            if (style.value == "brioche") {
                eggsPercent = 40.0; milkPercent = 21.0; butterPercent = 60.0; liquidType = "milk"
            }
            if (style.value == "english_muffin") {
                milkPercent = 33.8; butterPercent = 8.0
            }
            if (style.value == "soft_roll") {
                liquidType = "water"
            }

            s.copy(
                selectedStyle = style,
                selectedShapeValue = defaultShape?.value ?: s.selectedShapeValue,
                hydration = style.hydrationIdeal,
                fat = style.defaultFat ?: 0.0,
                salt = style.defaultSalt ?: 2.0,
                yeast = style.yeastIdeal,
                isSpeedRun = false,
                originalYeast = null,
                flourBlend = style.defaultFlourBlend ?: listOf(FlourBlendEntry(type = "bread", percent = 100.0)),
                sweetenerType = sweetenerType,
                sweetenerPct = sweetenerPct,
                eggsPercent = eggsPercent,
                milkPercent = milkPercent,
                butterPercent = butterPercent,
                liquidType = liquidType,
                dairyDisplayName = null,
                formulaResult = null,
                proofResult = null,
            )
        }
    }

    /**
     * "Start Over" — new, no iOS source precedent (the ported
     * `calculator.tsx` had no equivalent; every reset there was implicit
     * via a full remount). Restores every field to its true
     * out-of-the-box default — matching [CalculatorUiState]'s own
     * defaults — rather than reusing [selectStyle], which only resets
     * the subset of fields that are actually style-derived. Jumps back
     * to Card 0.
     */
    fun resetToDefaults() {
        update {
            CalculatorUiState(
                temperatureUnit = it.temperatureUnit, userTier = it.userTier, recipes = it.recipes, queuedBakeCount = it.queuedBakeCount,
                customIngredientPrices = it.customIngredientPrices, serverReferencePrices = it.serverReferencePrices,
                pendingStagedImports = it.pendingStagedImports,
            )
        }
    }

    fun applyFlourTemplate(template: FlourBlendTemplate) {
        update { s ->
            val adj = CalculatorFormatting.computeHydrationAdj(template.blend)
            s.copy(flourBlend = template.blend, hydration = (s.selectedStyle.hydrationIdeal + adj).coerceIn(50.0, 100.0))
        }
    }

    fun handleSpeedRunToggle(on: Boolean) {
        update { s ->
            if (on) {
                s.copy(
                    originalYeast = s.yeast,
                    yeast = minOf(2.0, (s.yeast * 2 * 10).swiftRounded() / 10),
                    waterTempF = 88.0,
                    usePrefermant = false,
                    useColdRetard = false,
                    isSpeedRun = true,
                )
            } else {
                s.copy(
                    yeast = s.originalYeast ?: s.yeast,
                    originalYeast = null,
                    waterTempF = 75.0,
                    isSpeedRun = false,
                )
            }
        }
    }

    fun updateFlourType(idx: Int, newType: String) {
        update { s ->
            if (idx !in s.flourBlend.indices) return@update s
            s.copy(flourBlend = s.flourBlend.mapIndexed { i, e -> if (i == idx) e.copy(type = newType) else e })
        }
    }

    fun updateFlourPercent(idx: Int, pct: Double) {
        update { s ->
            if (idx !in s.flourBlend.indices) return@update s
            val updated = s.flourBlend.mapIndexed { i, e -> if (i == idx) e.copy(percent = pct) else e }.toMutableList()
            if (idx != 0 && updated.size > 1) {
                val secSum = updated.drop(1).sumOf { it.percent }
                updated[0] = updated[0].copy(percent = maxOf(1.0, 100 - secSum))
            }
            s.copy(flourBlend = updated)
        }
    }

    fun addFlour() {
        update { s ->
            if (s.flourBlend.size >= 5) return@update s
            val used = s.flourBlend.map { it.type }.toSet()
            val nextType = com.BreadIQ.myapp.model.calculatorFlourTypes.firstOrNull { it.value !in used }?.value ?: "whole_wheat"
            val defaultPct = if (s.flourBlend.size == 1) 20.0 else 10.0
            val secSum = s.flourBlend.drop(1).sumOf { it.percent } + defaultPct
            val primary = maxOf(1.0, 100 - secSum)
            val updated = s.flourBlend.toMutableList()
            updated[0] = updated[0].copy(percent = primary)
            updated.add(FlourBlendEntry(type = nextType, percent = defaultPct))
            s.copy(flourBlend = updated)
        }
    }

    fun removeFlour(idx: Int) {
        update { s ->
            if (idx == 0 || s.flourBlend.size <= 1 || idx !in s.flourBlend.indices) return@update s
            val updated = s.flourBlend.toMutableList()
            updated.removeAt(idx)
            val secSum = updated.drop(1).sumOf { it.percent }
            updated[0] = updated[0].copy(percent = maxOf(1.0, 100 - secSum))
            s.copy(flourBlend = updated)
        }
    }

    fun handlePrefermentTypeChange(key: String) {
        update { s ->
            val info = prefermentTypes[key]
            s.copy(
                prefermentType = key,
                prefermentFlourPct = info?.flourPercentSuggested ?: s.prefermentFlourPct,
                prefermentHydration = info?.hydrationIdeal ?: s.prefermentHydration,
            )
        }
    }

    /** Fire `Haptics.impact(context, HapticImpactStyle.LIGHT)` from the Composable right after calling this — see this class's own doc comment. */
    fun calculate() {
        val s = _uiState.value
        update { it.copy(loading = true, formulaResult = null, proofResult = null, savedId = null) }

        val isBrioche = s.selectedStyle.value == "brioche"
        val isEnglishMuffin = s.selectedStyle.value == "english_muffin"
        val isSoftRoll = s.selectedStyle.value == "soft_roll"
        val isBaguette = s.selectedStyle.value == "baguette"
        val isFocaccia = s.selectedStyle.value == "focaccia"

        val milkPercentForFormula: Double? = when {
            isEnglishMuffin -> s.milkPercent
            isBrioche -> if (s.liquidType == "milk") s.milkPercent else null
            isSoftRoll && s.liquidType == "milk" -> (s.hydration / 0.87 * 10).swiftRounded() / 10
            else -> null
        }
        val dairyDisplayNameForFormula: String? = when {
            isBrioche -> if (s.liquidType == "milk") s.dairyDisplayName else null
            isSoftRoll && s.liquidType == "milk" -> s.dairyDisplayName
            else -> null
        }

        val formulaInput = FormulaInput(
            loafStyle = s.selectedStyle.value, numLoaves = s.numLoaves.toInt(),
            hydrationPercent = s.hydration, fatPercent = s.fat, saltPercent = s.salt, yeastPercent = s.yeast,
            usePrefermant = s.usePrefermant,
            prefermentType = if (s.usePrefermant) s.prefermentType else null,
            prefermentFlourPercent = if (s.usePrefermant) s.prefermentFlourPct else null,
            prefermentHydration = if (s.usePrefermant) s.prefermentHydration else null,
            diastaticMaltPercent = if (s.maltPct > 0) s.maltPct else null,
            flourBlend = if (s.flourBlend.size > 1) s.flourBlend else null,
            sweetenerType = s.sweetenerType,
            sweetenerPercent = if (s.sweetenerType != null) s.sweetenerPct else null,
            sizeModifier = if (isBaguette) s.baguetteMod else if (isFocaccia) s.focacciaScale else null,
            relativeHumidity = if (s.isPremium && s.isHumidityMode) s.relativeHumidity.toInt() else null,
            eggsPercent = if (isBrioche) s.eggsPercent else null,
            milkPercent = milkPercentForFormula,
            dairyDisplayName = dairyDisplayNameForFormula,
            butterPercent = if (isBrioche || isEnglishMuffin) s.butterPercent else null,
            yeastType = s.yeastType,
        )

        val fResult = FormulaCalculator.calculate(formulaInput)

        val effectiveLoaves = s.numLoaves.toInt()
        val proofInput = ProofTimeInput(
            hydrationPercent = s.hydration, yeastPercent = s.yeast, waterTempF = s.waterTempF, ambientTempF = s.ambientTempF,
            fermentationType = if (s.useColdRetard) "cold" else "straight", usePrefermant = s.usePrefermant, isSourdough = false,
            coldRetardHours = if (s.useColdRetard) s.coldRetardHours else null,
            coldRetardTempF = if (s.useColdRetard) s.coldRetardTempF else null,
            finalProofTempF = s.finalProofTempF, diastaticMaltPercent = if (s.maltPct > 0) s.maltPct else null, saltPercent = s.salt,
            sweetenerType = s.sweetenerType, sweetenerPercent = if (s.sweetenerType != null) s.sweetenerPct else null,
            breadStyle = s.selectedStyle.value, loafStyle = s.selectedShapeValue,
            baguetteSizeModifier = if (isBaguette) s.baguetteMod else null, isSpeedRun = s.isSpeedRun,
            relativeHumidity = if (s.isPremium && s.isHumidityMode) s.relativeHumidity.toInt() else null,
            numLoaves = effectiveLoaves, doughWeightPerPiece = fResult.totalDoughWeight / effectiveLoaves,
            flourBlend = if (s.flourBlend.size > 1) s.flourBlend else null,
            fatPercent = s.fat + (if (isBrioche || isEnglishMuffin) s.butterPercent else 0.0),
            totalDoughWeightG = fResult.totalDoughWeight,
        )
        val math = ProofTimeCalculator.calculate(proofInput)
        val pResult = ProofStageNarrator.narrate(proofInput, math, s.temperatureUnit)

        update { it.copy(formulaResult = fResult, proofResult = pResult, loading = false) }

        // IMPORT_REVIEW_SPEC.md §5's mandatory (not optional) auto-save —
        // fires on the FIRST successful calculation for an import-
        // originated session, not at the review screen itself, since
        // fermentation/environment inputs (which affect the saved
        // proofMinutes etc.) haven't been set yet at that point.
        // `s.isImportSession` (captured at the top of this function,
        // before any update{} calls) reflects applyImportReviewOutcome's
        // own flip of this flag — nothing else changes it in between.
        if (s.isImportSession) {
            autoSaveImportedRecipe()
            update { it.copy(isImportSession = false) }
        }
    }

    private fun rhDirection(s: CalculatorUiState): String? =
        if (!s.isHumidityMode) null else if (s.relativeHumidity >= 65) "high" else if (s.relativeHumidity <= 35) "low" else null

    private fun buildRecipe(
        s: CalculatorUiState, formulaResult: FormulaResult, id: Int, name: String,
        importSourceURL: String? = null, importSourceName: String? = null, formatNote: String? = null,
    ): Recipe {
        val rhDir = rhDirection(s)
        val standardWater = if (rhDir != null)
            (((formulaResult.flourWeight * s.hydration / 100) - (formulaResult.sweetenerWaterWeight ?: 0.0)) * 10).swiftRounded() / 10
        else null

        return Recipe(
            id = id, userId = "", name = name,
            loafStyle = s.selectedStyle.value, numLoaves = s.numLoaves.toInt(), hydrationPercent = s.hydration, fatPercent = s.fat,
            flourWeight = formulaResult.flourWeight, waterWeight = formulaResult.waterWeight, fatWeight = formulaResult.fatWeight,
            saltWeight = formulaResult.saltWeight, yeastWeight = formulaResult.yeastWeight, yeastPercent = s.yeast,
            yeastType = s.yeastType, loafShape = s.selectedShapeValue,
            preFermentType = if (s.usePrefermant) s.prefermentType else null,
            preFermentFlourWeight = if (s.usePrefermant) formulaResult.preferment?.flourWeight else null,
            preFermentWaterWeight = if (s.usePrefermant) formulaResult.preferment?.waterWeight else null,
            preFermentYeastWeight = if (s.usePrefermant) formulaResult.preferment?.yeastWeight else null,
            fermentationType = if (s.useColdRetard) "cold" else "straight",
            proofMinutes = s.proofResult?.totalMinutes?.toDouble(),
            flourBlend = if (s.flourBlend.size > 1) s.flourBlend else null, sweetenerType = s.sweetenerType,
            sweetenerWeight = if (s.sweetenerType != null) formulaResult.sweetenerWeight else null,
            humidityRh = if (rhDir != null) s.relativeHumidity.toInt() else null, humidityDirection = rhDir, humidityAdjusted = rhDir != null,
            waterWeightUnadjusted = standardWater,
            importSourceURL = importSourceURL, importSourceName = importSourceName, formatNote = formatNote,
        )
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after calling this — see this class's own doc comment. Backend sync (`syncRecipeCreate`) is not ported this session; the recipe is saved locally only. */
    fun handleSaveRecipe() {
        val s = _uiState.value
        val formulaResult = s.formulaResult ?: return
        val trimmedName = s.recipeName.trim()
        if (trimmedName.isEmpty()) return

        update { it.copy(saving = true) }
        val rhDir = rhDirection(s)
        val saveName = if (rhDir != null) "$trimmedName — Humidity Adjusted (${s.relativeHumidity.toInt()}% RH)" else trimmedName
        val recipe = buildRecipe(s, formulaResult, CalculatorFormatting.nextLocalRecipeId(s.recipes), saveName)

        viewModelScope.launch {
            recipeDao.upsert(recipe.toEntity())
            update { it.copy(savedId = recipe.id, saving = false) }
        }
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after calling this. Backend sync (`syncRecipeUpdate`) is not ported this session. */
    fun handleUpdateRecipe() {
        val s = _uiState.value
        val formulaResult = s.formulaResult ?: return
        val loadedId = s.loadedFromRecipeId ?: return
        val existing = s.recipes.firstOrNull { it.id == loadedId } ?: return

        update { it.copy(saving = true) }
        val trimmedName = s.recipeName.trim()
        val name = if (trimmedName.isEmpty()) s.loadedFromRecipeName else trimmedName
        val updated = buildRecipe(s, formulaResult, existing.id, name)

        viewModelScope.launch {
            recipeDao.upsert(updated.toEntity())
            update { it.copy(savedId = updated.id, loadedFromRecipeId = null, saving = false) }
        }
    }

    // MARK: - Import (browser-extension deep link -> ImportReviewScreen)

    /**
     * `fetchPendingImportToken()` — `GET /api/import/staged/:token`, then
     * [CalculatorImportApplier.map]'s pure style mapping. Supersedes the
     * old "fetch, silently apply, show a banner" flow entirely, per
     * `IMPORT_REVIEW_SPEC.md`'s own framing — nothing here writes into
     * this ViewModel's real formula state; it only populates [importReview][CalculatorUiState.importReview]
     * for `ImportReviewScreen` to render, then [applyImportReviewOutcome]
     * is the one place a confirmed import actually reaches real state.
     */
    fun fetchStagedImport(token: String) {
        update { it.copy(importFetching = true, importError = null, importReview = null) }
        viewModelScope.launch {
            when (val outcome = importStagingFetcher.fetchStagedImport(token)) {
                is StagedImportFetchOutcome.Success -> {
                    when (val mappingResult = CalculatorImportApplier.map(outcome.payload.ingredients)) {
                        is CalculatorImportMappingResult.Success -> update {
                            it.copy(importFetching = false, importReview = outcome.payload to mappingResult.mapping)
                        }
                        is CalculatorImportMappingResult.Failure -> update {
                            it.copy(importFetching = false, importError = "No flour found in this import — cannot populate calculator.")
                        }
                    }
                }
                is StagedImportFetchOutcome.Failure -> update {
                    it.copy(importFetching = false, importError = outcome.error.message)
                }
            }
        }
    }

    /** "Start from Scratch" on `ImportReviewScreen` — discards the fetched review with zero trace left in real calculator state. */
    fun clearImportReview() = update { it.copy(importReview = null) }

    /**
     * `refreshPendingStagedImports()` — polls the Chrome-extension
     * companion's inbox. Called on first composition and every time
     * `CalculatorScreen` returns to the foreground (`LifecycleEventEffect`/
     * `ON_RESUME`), matching `RootView`'s own `.task` + `scenePhase ==
     * .active` pair. Silent, best-effort on failure — an empty/stale list
     * is a fine fallback for a background refresh, never surfaced to the
     * user (matches the source's own `guard case .success(...) else {
     * return }`).
     */
    suspend fun refreshPendingStagedImports() {
        when (val outcome = importInboxFetcher.fetchPendingImports()) {
            is ImportInboxFetchOutcome.Success -> update { it.copy(pendingStagedImports = outcome.items) }
            is ImportInboxFetchOutcome.Failure -> Unit
        }
    }

    /**
     * Picking a row from `PendingImportsListScreen` — routes through the
     * EXISTING single-token [fetchStagedImport] pipeline unchanged, same
     * pipeline a Safari deep link already drives. Removed from
     * [CalculatorUiState.pendingStagedImports] optimistically, matching
     * the source's own `AppRouter.selectStagedImport` (remove-then-fetch,
     * not fetch-then-remove).
     */
    fun selectStagedImport(token: String) {
        update { it.copy(pendingStagedImports = it.pendingStagedImports.filterNot { item -> item.token == token }) }
        fetchStagedImport(token)
    }

    /**
     * `ImportReviewScreen`'s `onContinue` handler — the only place a
     * staged import's fields actually reach this ViewModel's real state,
     * matching `IMPORT_REVIEW_SPEC.md` §2's "no silent style-forcing"
     * decision: everything below only runs after the user has explicitly
     * reviewed and confirmed on that screen, never automatically. Calls
     * [selectStyle] first so style-derived defaults [ImportReviewOutcome]
     * doesn't carry are still correct, then overwrites every field
     * [selectStyle] just set with the outcome's real values. Jumps
     * straight to Card 3 (Environment), skipping 0-2 entirely, per spec
     * §4's flow diagram.
     */
    fun applyImportReviewOutcome(outcome: ImportReviewOutcome, payload: StagedImportPayload) {
        BreadStyleCatalog.all.firstOrNull { it.value == outcome.styleValue }?.let { selectStyle(it) }
        update { s ->
            // Dairy-import-to-milk-slot wiring — only for the two styles
            // whose formula actually reads milkPercent through a "milk"
            // liquidType (see FormulaInput's own milkPercent branch in
            // calculate()). An import with no dairy ingredient
            // (outcome.milkPercent == null) leaves selectStyle's own
            // defaults (just applied above) untouched.
            val milkApplies = outcome.milkPercent != null && (outcome.styleValue == "brioche" || outcome.styleValue == "soft_roll")
            s.copy(
                selectedShapeValue = outcome.shapeValue,
                numLoaves = outcome.numLoaves,
                hydration = outcome.hydration,
                fat = outcome.fat,
                salt = outcome.salt,
                yeast = outcome.yeast,
                yeastType = outcome.yeastType,
                sweetenerType = outcome.sweetenerType,
                sweetenerPct = outcome.sweetenerPct,
                milkPercent = if (milkApplies) outcome.milkPercent!! else s.milkPercent,
                liquidType = if (milkApplies) "milk" else s.liquidType,
                dairyDisplayName = if (milkApplies) outcome.dairyDisplayName else s.dairyDisplayName,
                flourBlend = outcome.flourBlend,
                isHumidityMode = outcome.isHumidityMode,
                relativeHumidity = outcome.relativeHumidity,
                usePrefermant = outcome.usePrefermant,
                prefermentType = outcome.prefermentType,
                useColdRetard = outcome.useColdRetard,
                coldRetardHours = outcome.coldRetardHours,
                isImportSession = true,
                pendingImportSourceURL = payload.sourceURL,
                pendingImportSourceName = ImportReviewFormatting.sourceDomain(payload.sourceURL),
                pendingFormatNote = outcome.formatNote.ifEmpty { null },
                recipeName = payload.recipeName?.takeIf { it.isNotEmpty() } ?: "Imported Recipe",
                importReview = null,
                cardIndex = 3,
            )
        }
    }

    /**
     * `IMPORT_REVIEW_SPEC.md` §5's mandatory auto-save — fires on the
     * first successful calculation for an import-originated session (see
     * [calculate]'s own call site), not at the review screen itself,
     * since fermentation/environment inputs haven't been set yet at that
     * point. Reuses [buildRecipe] exactly, plus the three pending
     * import-only fields, with one deliberate difference from a plain
     * "always save": **respects the existing Basic/Premium paywall**,
     * the same gate [handleSaveRecipe]'s own manual save already uses —
     * a free-tier import still calculates and applies to the calculator
     * normally, it just doesn't persist. Local-only save, matching
     * [handleSaveRecipe]'s own current scope boundary — no backend sync
     * call here either, for the same reason.
     */
    private fun autoSaveImportedRecipe() {
        val s = _uiState.value
        val formulaResult = s.formulaResult ?: return
        if (!s.isBasicOrPremium) {
            showUpgradeAlert(
                "Imported Recipe Not Saved",
                "This import was calculated but not saved — saving recipes requires a Basic or Premium subscription. Upgrade to save up to 10 recipes with Basic, or 50 with Premium.",
            )
            return
        }

        val rhDir = rhDirection(s)
        val baseName = s.recipeName.trim().ifEmpty { "Imported Recipe" }
        val saveName = if (rhDir != null) "$baseName — Humidity Adjusted (${s.relativeHumidity.toInt()}% RH)" else baseName
        val recipe = buildRecipe(
            s, formulaResult, CalculatorFormatting.nextLocalRecipeId(s.recipes), saveName,
            importSourceURL = s.pendingImportSourceURL, importSourceName = s.pendingImportSourceName, formatNote = s.pendingFormatNote,
        )

        viewModelScope.launch {
            recipeDao.upsert(recipe.toEntity())
            update { it.copy(savedId = recipe.id) }
        }
    }

    /**
     * "Load into Calculator" — new, no iOS source precedent. The iOS
     * app's own `RecipesScreen.swift` sets `AppRouter.pendingRecipe` and
     * switches tabs, but `CalculatorScreen.swift` never actually reads
     * `pendingRecipe` anywhere (confirmed by grepping the whole file) —
     * a real, unfinished handoff in the source itself, not just a gap in
     * this port. This is the missing consumption side, built fresh for
     * this port since there's nothing to transcribe.
     *
     * Approved directly: populates every field [Recipe] actually stores,
     * then immediately calls [calculate] and jumps to Card 4 so the user
     * sees the same formula the recipe was saved with right away, rather
     * than landing on a blank Card 0 that requires paging through and
     * tapping Calculate manually.
     *
     * Baker's percentages [Recipe] doesn't store directly (salt,
     * sweetener, pre-ferment flour/hydration) are recovered from its
     * stored gram weights — the inverse of [buildRecipe]'s own
     * weight-from-percentage math. Fields [Recipe] never stores at all
     * (egg/milk/butter percentages, malt, SpeedRun, cold-retard duration/
     * temp, water/kitchen/final-proof temps, pretzel bath type) are left
     * at whatever [selectStyle] resets them to for the matched style —
     * the same "recipe doesn't retain everything" limitation the source
     * schema already has, not something this port introduces.
     */
    fun loadFromRecipe(recipeId: Int) {
        viewModelScope.launch {
            val recipe = recipeDao.getById(recipeId)?.toDomain() ?: return@launch

            val style = BreadStyleCatalog.all.firstOrNull { it.value == recipe.loafStyle } ?: BreadStyleCatalog.all[0]
            selectStyle(style)

            fun pctOf(weight: Double?, base: Double): Double? {
                if (weight == null || base <= 0) return null
                return (weight / base * 100 * 10).swiftRounded() / 10
            }

            update { s ->
                val flourWeight = recipe.flourWeight
                s.copy(
                    selectedShapeValue = recipe.loafShape ?: s.selectedShapeValue,
                    numLoaves = recipe.numLoaves.toDouble(),
                    hydration = recipe.hydrationPercent,
                    fat = recipe.fatPercent,
                    salt = pctOf(recipe.saltWeight, flourWeight) ?: s.salt,
                    yeast = recipe.yeastPercent,
                    yeastType = recipe.yeastType ?: s.yeastType,
                    flourBlend = recipe.flourBlend?.takeIf { it.isNotEmpty() } ?: s.flourBlend,
                    sweetenerType = recipe.sweetenerType,
                    sweetenerPct = pctOf(recipe.sweetenerWeight, flourWeight) ?: s.sweetenerPct,
                    usePrefermant = recipe.preFermentType != null,
                    prefermentType = recipe.preFermentType ?: s.prefermentType,
                    prefermentFlourPct = pctOf(recipe.preFermentFlourWeight, flourWeight) ?: s.prefermentFlourPct,
                    prefermentHydration = pctOf(recipe.preFermentWaterWeight, recipe.preFermentFlourWeight ?: 0.0) ?: s.prefermentHydration,
                    useColdRetard = recipe.fermentationType == "cold",
                    isHumidityMode = recipe.humidityAdjusted,
                    relativeHumidity = recipe.humidityRh?.toDouble() ?: s.relativeHumidity,
                    recipeName = "",
                    savedId = null,
                    loadedFromRecipeId = recipe.id,
                    loadedFromRecipeName = recipe.name,
                    cardIndex = 4,
                )
            }

            calculate()
        }
    }

    /**
     * Builds the [QueuedBake] the current calculator state would queue —
     * matches the iOS source's `currentQueuedBakePlan()`. [buildBakePlan]
     * below wraps this same result for the Schedule screen, matching how
     * the source's own `currentQueuedBakePlan()` feeds BOTH
     * `handleQueueBake()` and `handleOpenScheduleModal()` from one
     * shared plan-building function.
     */
    private fun currentQueuedBake(s: CalculatorUiState): QueuedBake? {
        val proofResult = s.proofResult ?: return null
        val steps = proofResult.stages.map { QueuedBakeStepPlan(label = it.name, description = it.description, durationMinutes = it.durationMinutes) }
        val config = QueuedBakeConfig(
            numLoaves = s.numLoaves.toInt(), hydration = s.hydration, fat = s.fat, salt = s.salt, yeast = s.yeast, yeastType = s.yeastType,
            flourBlend = s.flourBlend, sweetenerType = s.sweetenerType, sweetenerPct = s.sweetenerPct,
            usePrefermant = s.usePrefermant, prefermentType = s.prefermentType, isSpeedRun = s.isSpeedRun,
            isHumidityMode = s.isHumidityMode, relativeHumidity = s.relativeHumidity, shapeName = s.currentShapeName,
        )
        return QueuedBake(name = "${s.selectedStyle.label} — ${s.currentShapeName}", style = s.selectedStyle.label, ovenTempF = s.selectedStyle.ovenTempF.low, steps = steps, config = config)
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after this sets `queueSuccessShown`. */
    fun handleQueueBake() {
        val s = _uiState.value
        val queued = currentQueuedBake(s) ?: return

        val maxQueued = mapOf(BakeUserTier.FREE to 0, BakeUserTier.BASIC to 1, BakeUserTier.PREMIUM to 3)
        val max = maxQueued[s.userTier] ?: 0
        if (s.queuedBakeCount >= max) {
            update { it.copy(queueError = "You've reached your limit of $max active or scheduled bake${if (max == 1) "" else "s"}. Start, complete, or remove one first.") }
            return
        }

        viewModelScope.launch {
            queuedBakeDao.upsertQueuedBakeWithConfig(queued.toEntity(), queued.config.toEntity(queued.id))
            update { it.copy(queueSuccessShown = true) }
        }
    }

    /** Builds the plan the Schedule screen needs for "Schedule Bake" — the same simple, proof-stage-based steps [handleQueueBake] stores, per [currentQueuedBake]'s own doc comment. Null when there's no current formula/proof result to schedule. */
    fun buildBakePlan(): RawScheduledBakePlan? {
        val queued = currentQueuedBake(_uiState.value) ?: return null
        return RawScheduledBakePlan(name = queued.name, style = queued.style, ovenTempF = queued.ovenTempF, steps = queued.steps, config = queued.config)
    }

    /**
     * `handleStartBake()` — unlike [handleQueueBake]/[buildBakePlan],
     * this builds the FULL, richly-detailed step list via
     * [BakeStepAssembler.assemble] (recipe card, ingredient-line
     * breakdowns, per-style mix/bake text) rather than the simple
     * proof-stage-only steps a queued/scheduled bake stores — matching
     * the iOS source's own `assembledSteps()`, a genuinely different
     * (richer) step list than `currentQueuedBakePlan()` produces, not
     * an inconsistency to unify.
     */
    fun handleStartBake() {
        val s = _uiState.value
        val formulaResult = s.formulaResult ?: return
        val proofResult = s.proofResult ?: return

        update { it.copy(startingBake = true) }

        val assemblyInput = BakeStepAssemblyInput(
            formulaResult = formulaResult, proofResult = proofResult, style = s.selectedStyle, flourBlend = s.flourBlend,
            yeastType = s.yeastType, sweetenerType = s.sweetenerType, usePrefermant = s.usePrefermant,
            prefermentType = s.prefermentType, prefermentFlourPercent = s.prefermentFlourPct, liquidType = s.liquidType,
            pretzelBathType = s.pretzelBathType, selectedShapeValue = s.selectedShapeValue, temperatureUnit = s.temperatureUnit,
        )
        val rawSteps = BakeStepAssembler.assemble(assemblyInput)

        val result = BakeSessionEngine.startBake(
            name = "${s.selectedStyle.label} — ${s.currentShapeName}", style = s.selectedStyle.label, steps = rawSteps,
            ovenTempF = s.selectedStyle.ovenTempF.low, isSpeedRun = s.isSpeedRun, tier = s.userTier, existingSessions = s.sessions,
        )
        update { it.copy(startingBake = false) }

        when (result) {
            is BakeStartResult.Failure -> update { it.copy(startError = result.failure.message) }
            is BakeStartResult.Success -> {
                val session = result.session
                viewModelScope.launch {
                    bakeSessionDao.upsertSessionWithSteps(session.toEntity(), session.steps.map { it.toEntity(session.id) })
                    BakeNotificationScheduler.afterStart(session)
                    update { it.copy(startedSessionId = session.id) }
                }
            }
        }
    }

    fun clearStartedSessionId() = update { it.copy(startedSessionId = null) }

    /**
     * `handleShareRecipe()` — builds a real styled `.xlsx` document
     * ([RecipeXLSXExporter]) and shares it as a file. The Premium gate
     * itself is the same [showUpgradeAlert] call/copy already used
     * elsewhere in this ViewModel.
     *
     * Writes to [Context.getCacheDir] (not
     * `FileManager.default.temporaryDirectory` the way the source does —
     * the Android counterpart is the app's cache directory, which is
     * exactly what this app's new `FileProvider` `<cache-path>` exposes)
     * and surfaces the resulting content [Uri] via
     * [CalculatorUiState.shareFileUri] for the Composable to launch an
     * `ACTION_SEND` chooser with, then clear back to `null` — this
     * ViewModel has no Activity to present a share sheet from itself,
     * same Context/Activity boundary every other picker/share-needing
     * feature in this app already established.
     *
     * Silently no-ops on any build/write failure, matching the source's
     * own `guard let data = try? ... else { return }` /
     * `guard (try? data.write(to:)) != nil else { return }` shape.
     */
    fun shareRecipe() {
        val s = _uiState.value
        if (!s.isPremium) {
            showUpgradeAlert("Recipe Export", "Export and share your full recipe — weights, baker's percentages, and proof timeline. Available on Premium.")
            return
        }
        val formulaResult = s.formulaResult ?: return
        val proofResult = s.proofResult ?: return

        val exportContext = RecipeXLSXExportContext(
            formulaResult = formulaResult, proofResult = proofResult, recipeName = s.recipeName,
            styleValue = s.selectedStyle.value, styleLabel = s.selectedStyle.label,
            shapeValue = s.selectedShapeValue, shapeLabel = s.currentShapeName, numLoaves = s.numLoaves.toInt(),
            hydrationPercent = s.hydration, flourBlend = s.flourBlend,
            usePrefermant = s.usePrefermant, prefermentType = s.prefermentType,
            yeastLabel = s.yeastMeta.label, yeastFactor = s.yeastMeta.factor, sweetenerLabel = s.sweetMeta?.label,
            isHumidityMode = s.isHumidityMode, relativeHumidity = s.relativeHumidity,
        )

        viewModelScope.launch {
            val uri = try {
                withContext(Dispatchers.IO) {
                    val bytes = RecipeXLSXExporter.build(exportContext)
                    val filename = RecipeXLSXExporter.filename(s.selectedStyle.label)
                    val file = File(appContext.cacheDir, filename)
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                }
            } catch (e: Exception) {
                null
            }
            if (uri != null) update { it.copy(shareFileUri = uri) }
        }
    }

    fun clearShareFileUri() = update { it.copy(shareFileUri = null) }
}

/**
 * Builds a [CalculatorViewModel] backed by the app's shared Room database
 * and [TemperatureUnitStore] — same plain-`ViewModelProvider.Factory`
 * shape as `AuthViewModelFactory`.
 */
/**
 * [subscriptionViewModel] is the app's single, Activity-scoped
 * [SubscriptionViewModel] instance (`MainActivity`'s own `by viewModels {}`)
 * — threaded in explicitly by the caller (`MainActivity.kt`'s
 * `BreadIQApp` composable) rather than constructed fresh here, since a
 * new [ViewModelProvider.Factory] is built at every `CalculatorScreen`/
 * `NutritionAnalysisScreen`/`AutolyseGuidanceScreen` nav-route call site
 * and none of them should ever end up with their own independent
 * `SubscriptionViewModel`.
 *
 * [temperatureUnitStore] is likewise the app's single shared instance
 * (also constructed once in `MainActivity`, threaded through `BreadIQApp`)
 * rather than a fresh `TemperatureUnitStore(prefs)` per factory call —
 * the same one `SettingsScreen`'s temperature-unit row writes to, so a
 * change there reaches every live `CalculatorViewModel` immediately (see
 * [TemperatureUnitStore]'s own doc comment).
 *
 * The [com.BreadIQ.myapp.core.IngredientCostSyncing] instance built here
 * is a fresh [com.BreadIQ.myapp.data.BackendIngredientCostSyncService]
 * every call, **deliberately not threaded from `MainActivity`/shared
 * with `IngredientCostsViewModelFactory`** — matches a real, intentional
 * duplication in the iOS source (two independent screens, two
 * independent fetches of the same public endpoint, no shared cache), not
 * a gap to consolidate. See [CalculatorViewModel]'s own constructor doc
 * comment.
 */
class CalculatorViewModelFactory(
    private val context: Context,
    private val subscriptionViewModel: SubscriptionViewModel,
    private val temperatureUnitStore: TemperatureUnitStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val db = DatabaseProvider.getInstance(appContext)
        val backendClient = BackendApiClient(accessTokenProvider = {
            SupabaseAuthService(SupabaseClientProvider.getInstance(appContext)).currentAccessToken()
        })
        @Suppress("UNCHECKED_CAST")
        return CalculatorViewModel(
            recipeDao = db.recipeDao(),
            queuedBakeDao = db.queuedBakeDao(),
            ingredientPriceOverrideDao = db.ingredientPriceOverrideDao(),
            bakeSessionDao = db.bakeSessionDao(),
            temperatureUnitStore = temperatureUnitStore,
            appContext = appContext,
            subscriptionViewModel = subscriptionViewModel,
            ingredientCostSyncService = BackendIngredientCostSyncService(backendClient),
            importStagingFetcher = BackendImportStagingFetcher(backendClient),
            importInboxFetcher = BackendImportInboxFetcher(backendClient),
        ) as T
    }
}
