package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.core.BakeSessionEngine
import com.BreadIQ.myapp.core.BakeStartResult
import com.BreadIQ.myapp.core.RawBakeStep
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.QueuedBakeDao
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.data.local.toEntity
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.QueuedBake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QueueUiState(
    val queue: List<QueuedBake> = emptyList(),
    val sessions: List<BakeSession> = emptyList(),
    val startingId: String? = null,
    val startError: String? = null,
    /** Consumed once by the screen's own nav handoff to Bake Detail — see `MainActivity`'s wiring for the pattern this matches (`pendingRecipeId`). */
    val startedSessionId: String? = null,
    /**
     * Mirrors the source's `@Environment(SubscriptionStore.self)`-derived
     * `userTier`. Hardcoded to [BakeUserTier.FREE] — same stub as
     * `CalculatorUiState.userTier`, for the same reason (no
     * `SubscriptionStore` port exists yet).
     */
    val userTier: BakeUserTier = BakeUserTier.FREE,
)

val QueueUiState.activeBakeCount: Int
    get() = sessions.count { it.status == BakeStatus.ACTIVE || it.status == BakeStatus.PAUSED }

/**
 * `maxQueuedBakes`/`MAX_QUEUED` — kept as its own table, not merged with
 * [BakeSessionEngine.maxActiveBakes], despite identical values (free: 0,
 * basic: 1, premium: 3). The iOS source declares these as two
 * independent constants in two different files — queue capacity and
 * active-bake capacity are conceptually distinct limits that only
 * coincidentally match today; merging them would silently couple two
 * business rules the source itself keeps independent.
 */
private val maxQueued: Map<BakeUserTier, Int> = mapOf(BakeUserTier.FREE to 0, BakeUserTier.BASIC to 1, BakeUserTier.PREMIUM to 3)
val QueueUiState.maxQueuedBakes: Int get() = maxQueued[userTier] ?: 0

/**
 * Ported from the iOS app's `Screens/QueueScreen.swift`.
 *
 * **Data source: observed Room queries, not a shared context/store** —
 * same dissolution pattern already established for
 * [com.BreadIQ.myapp.viewmodel.RecipesViewModel].
 *
 * **`remove`/`startNow` are real, local Room operations today** — the
 * one thing genuinely deferred is notification scheduling inside
 * `startNow` itself, which is [BakeNotificationScheduler]'s own
 * documented scope boundary, not something this screen adds.
 */
class QueueViewModel(
    private val queuedBakeDao: QueuedBakeDao,
    private val bakeSessionDao: BakeSessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            queuedBakeDao.observeQueueTab().collect { rows ->
                _uiState.value = _uiState.value.copy(queue = rows.map { it.toDomain() })
            }
        }
        viewModelScope.launch {
            bakeSessionDao.observeAll().collect { rows ->
                _uiState.value = _uiState.value.copy(sessions = rows.map { it.toDomain() })
            }
        }
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after a successful start — see `Haptics.kt`'s own doc comment for why this ViewModel can't fire it itself. */
    fun startNow(bake: QueuedBake) {
        val s = _uiState.value
        _uiState.value = s.copy(startingId = bake.id)

        val rawSteps = bake.steps.map { RawBakeStep(label = it.label, description = it.description, durationMinutes = it.durationMinutes) }
        val result = BakeSessionEngine.startBake(
            name = bake.name, style = bake.style, steps = rawSteps, ovenTempF = bake.ovenTempF,
            isSpeedRun = bake.config.isSpeedRun, tier = s.userTier, existingSessions = s.sessions,
        )
        _uiState.value = _uiState.value.copy(startingId = null)

        when (result) {
            is BakeStartResult.Failure -> _uiState.value = _uiState.value.copy(startError = result.failure.message)
            is BakeStartResult.Success -> {
                val session = result.session
                viewModelScope.launch {
                    bakeSessionDao.upsertSessionWithSteps(session.toEntity(), session.steps.map { it.toEntity(session.id) })
                    queuedBakeDao.deleteById(bake.id)
                    BakeNotificationScheduler.afterStart(session)
                    _uiState.value = _uiState.value.copy(startedSessionId = session.id)
                }
            }
        }
    }

    fun remove(bake: QueuedBake) {
        viewModelScope.launch { queuedBakeDao.deleteById(bake.id) }
    }

    fun clearStartError() {
        _uiState.value = _uiState.value.copy(startError = null)
    }

    fun clearStartedSessionId() {
        _uiState.value = _uiState.value.copy(startedSessionId = null)
    }
}

class QueueViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return QueueViewModel(queuedBakeDao = db.queuedBakeDao(), bakeSessionDao = db.bakeSessionDao()) as T
    }
}
