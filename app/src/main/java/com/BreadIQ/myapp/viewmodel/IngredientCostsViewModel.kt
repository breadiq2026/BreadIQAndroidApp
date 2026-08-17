package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.IngredientCostFormatting
import com.BreadIQ.myapp.core.IngredientCostSyncing
import com.BreadIQ.myapp.core.UnconfiguredIngredientCostSyncService
import com.BreadIQ.myapp.data.BackendApiClient
import com.BreadIQ.myapp.data.BackendIngredientCostSyncService
import com.BreadIQ.myapp.data.SupabaseAuthService
import com.BreadIQ.myapp.data.SupabaseClientProvider
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.IngredientPriceOverrideDao
import com.BreadIQ.myapp.data.local.IngredientPriceOverrideEntity
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IngredientCostsUiState(
    /** `customPriceByKey` — collected live from [IngredientPriceOverrideDao.observeAll]. */
    val customPriceByKey: Map<String, Double> = emptyMap(),
    /** `serverReferencePrices` — its own separate fetch, see [IngredientCostsViewModel]'s own doc comment. */
    val serverReferencePrices: Map<String, Double> = emptyMap(),
    val refUpdatedAt: Instant? = null,
    val editValues: Map<String, String> = emptyMap(),
    val savingKeys: Set<String> = emptySet(),
    val deletingKeys: Set<String> = emptySet(),
    val showInvalidPriceAlert: Boolean = false,
)

/**
 * Ported from the iOS app's `Screens/IngredientCostsScreen.swift`'s own
 * `@State`/`@Query` mix — a real, dedicated `ViewModel` (unlike
 * `ConnectBrowserScreen`'s plain-composable-state shape), since this
 * screen needs reactive DB access ([IngredientPriceOverrideDao.observeAll])
 * plus async save/delete/network calls, the same class of need
 * [CalculatorViewModel]/[ImportViewModel] already have.
 *
 * **Owns its own, completely separate [IngredientCostSyncing]
 * instance/fetch — NOT shared with [CalculatorViewModel]'s own,
 * independent `GET /api/reference-prices` fetch.** Matches a real,
 * deliberate duplication in the iOS source (two screens, two
 * independent fetches of the same public endpoint, no shared cache or
 * instance) — see [CalculatorViewModelFactory]'s own doc comment for the
 * fuller writeup of why this isn't a gap to consolidate.
 *
 * Save/reset are real, immediate local [IngredientPriceOverrideDao]
 * mutations — not stubbed. The server sync ([IngredientCostSyncing.setOverride]/
 * [IngredientCostSyncing.deleteOverride]) is fired in a separate,
 * unawaited `viewModelScope.launch` alongside the local write, matching
 * the source's own "local write wins immediately, server sync trails
 * behind, no error surfaced from the background call" shape
 * (`Task { _ = await costSyncService.setOverride(...) }`).
 */
class IngredientCostsViewModel(
    private val dao: IngredientPriceOverrideDao,
    private val costSyncService: IngredientCostSyncing = UnconfiguredIngredientCostSyncService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngredientCostsUiState())
    val uiState: StateFlow<IngredientCostsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { overrides ->
                update { it.copy(customPriceByKey = overrides.associate { o -> o.ingredientKey to o.pricePerGram }) }
            }
        }
        viewModelScope.launch {
            val fetch = costSyncService.fetchReferencePrices()
            if (fetch != null) update { it.copy(serverReferencePrices = fetch.prices, refUpdatedAt = fetch.updatedAt) }
        }
        // `syncOverridesFromServer()` — GET /api/ingredient-costs once,
        // alongside the reference-price fetch above, upserting every
        // server-side override into the local Room table by key.
        viewModelScope.launch {
            costSyncService.fetchOverrides().onSuccess { serverOverrides ->
                serverOverrides.forEach { (key, pricePerGram) ->
                    dao.upsert(IngredientPriceOverrideEntity(ingredientKey = key, pricePerGram = pricePerGram))
                }
            }
        }
    }

    private fun update(transform: (IngredientCostsUiState) -> IngredientCostsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun setEditValue(key: String, value: String) = update { it.copy(editValues = it.editValues + (key to value)) }

    fun dismissInvalidPriceAlert() = update { it.copy(showInvalidPriceAlert = false) }

    /** `save(_:)`. Called both from the checkmark button and from a field losing focus while dirty (`ConnectBrowserScreen`-style auto-save-on-blur — see the screen's own doc comment). */
    fun save(key: String) {
        val raw = _uiState.value.editValues[key]
        if (raw.isNullOrEmpty()) return
        val lbPrice = IngredientCostFormatting.parsePositivePrice(raw)
        if (lbPrice == null) {
            update { it.copy(showInvalidPriceAlert = true) }
            return
        }
        val pricePerGram = IngredientCostFormatting.perGramFromPerLb(lbPrice)
        update { it.copy(savingKeys = it.savingKeys + key) }
        // Real, immediate local write.
        viewModelScope.launch {
            dao.upsert(IngredientPriceOverrideEntity(ingredientKey = key, pricePerGram = pricePerGram))
            update { it.copy(editValues = it.editValues - key, savingKeys = it.savingKeys - key) }
        }
        // Fire-and-forget server sync — a separate coroutine, not awaited
        // by the local-write path above, matching the source's own
        // detached `Task { ... }`.
        viewModelScope.launch {
            costSyncService.setOverride(key, pricePerGram)
        }
    }

    fun reset(key: String) {
        update { it.copy(deletingKeys = it.deletingKeys + key) }
        viewModelScope.launch {
            dao.deleteByKey(key)
            update { it.copy(editValues = it.editValues - key, deletingKeys = it.deletingKeys - key) }
        }
        viewModelScope.launch {
            costSyncService.deleteOverride(key)
        }
    }
}

/**
 * Builds an [IngredientCostsViewModel] backed by the app's shared Room
 * database and a real [BackendIngredientCostSyncService] — same
 * `context`-based construction shape [CalculatorViewModelFactory] uses.
 */
class IngredientCostsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val db = DatabaseProvider.getInstance(appContext)
        val backendClient = BackendApiClient(accessTokenProvider = {
            SupabaseAuthService(SupabaseClientProvider.getInstance(appContext)).currentAccessToken()
        })
        @Suppress("UNCHECKED_CAST")
        return IngredientCostsViewModel(
            dao = db.ingredientPriceOverrideDao(),
            costSyncService = BackendIngredientCostSyncService(backendClient),
        ) as T
    }
}
