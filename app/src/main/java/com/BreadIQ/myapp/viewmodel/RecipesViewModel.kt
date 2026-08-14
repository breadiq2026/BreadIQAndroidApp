package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.RecipeDao
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecipesUiState(
    val recipes: List<Recipe> = emptyList(),
    val selectedRecipeId: Int? = null,
)

/**
 * Ported from the iOS app's `Screens/RecipesScreen.swift`.
 *
 * **Data source, a deliberate architectural departure from the source's
 * `GET /api/recipes` fetch, carried over from the iOS port: [RecipeDao.observeAll]
 * against local Room, not a network call.** `Recipe` is already
 * documented as "synced with the backend REST API" — the backend sync
 * itself (`BackendRecipeSyncService`/`recipeSyncService.fetchAll()`)
 * isn't ported this session, same deferral as Save Recipe's backend
 * half in the Calculator session. [refresh] is a real, callable
 * no-op-by-default suspend function specifically so that sync can be
 * dropped in later without touching this screen's call sites again.
 *
 * **Delete IS fully real today, unlike the fetch** — [RecipeDao.deleteById],
 * no server round-trip needed to make this screen's own delete button
 * work correctly against local state.
 */
class RecipesViewModel(private val recipeDao: RecipeDao) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recipeDao.observeAll().collect { entities ->
                _uiState.value = _uiState.value.copy(recipes = entities.map { it.toDomain() })
            }
        }
    }

    fun selectRecipe(id: Int?) {
        _uiState.value = _uiState.value.copy(selectedRecipeId = id)
    }

    fun delete(recipe: Recipe) {
        viewModelScope.launch {
            recipeDao.deleteById(recipe.id)
            _uiState.value = _uiState.value.copy(selectedRecipeId = null)
        }
        // Backend delete sync (`recipeSyncService.delete(id:)`) is not
        // ported this session — see this class's own doc comment. A
        // negative (never-synced) local id would 404 harmlessly there
        // anyway, matching the source's own fire-and-forget framing.
    }

    /**
     * `GET /api/recipes` sync hook — a callable no-op until
     * `BackendRecipeSyncService` is ported. Wired to the header refresh
     * button so that porting step has an obvious slot to drop into
     * rather than needing to touch this screen again later.
     */
    suspend fun refresh() {
        // Intentionally empty — see this class's own doc comment.
    }
}

class RecipesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return RecipesViewModel(recipeDao = db.recipeDao()) as T
    }
}
