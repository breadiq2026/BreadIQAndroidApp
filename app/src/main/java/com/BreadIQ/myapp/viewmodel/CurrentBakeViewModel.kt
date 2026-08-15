package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.core.BakeSessionEngine
import com.BreadIQ.myapp.core.BakeStartResult
import com.BreadIQ.myapp.core.CalendarEventScheduler
import com.BreadIQ.myapp.core.RawBakeStep
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.ScheduledBakeDao
import com.BreadIQ.myapp.data.local.observeAllAsDomain
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.data.local.toEntity
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.ScheduledBake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CurrentBakeUiState(
    val sessions: List<BakeSession> = emptyList(),
    val scheduledBakes: List<ScheduledBake> = emptyList(),
    val startingId: String? = null,
    val startError: String? = null,
    /** Consumed once by the screen's own nav handoff to Bake Detail. */
    val startedSessionId: String? = null,
    /** Hardcoded to [BakeUserTier.FREE] — same stub as `CalculatorUiState.userTier`, no `SubscriptionStore` port exists yet. */
    val userTier: BakeUserTier = BakeUserTier.FREE,
)

val CurrentBakeUiState.active: List<BakeSession> get() = sessions.filter { it.status == BakeStatus.ACTIVE || it.status == BakeStatus.PAUSED }
val CurrentBakeUiState.completed: List<BakeSession> get() = sessions.filter { it.status == BakeStatus.COMPLETED }
val CurrentBakeUiState.combinedCount: Int get() = active.size + scheduledBakes.size
val CurrentBakeUiState.maxActiveBakes: Int get() = BakeSessionEngine.maxActiveBakes[userTier] ?: 0
val CurrentBakeUiState.isEmptyState: Boolean get() = active.isEmpty() && completed.isEmpty() && scheduledBakes.isEmpty()

/** `userTier === "premium" ? … : userTier === "basic" ? … : null`. */
fun tierLabel(tier: BakeUserTier, combinedCount: Int, maxActiveBakes: Int): String? = when (tier) {
    BakeUserTier.PREMIUM -> "$combinedCount / $maxActiveBakes bakes active or scheduled"
    BakeUserTier.BASIC -> "$combinedCount / $maxActiveBakes bake active or scheduled"
    BakeUserTier.FREE -> null
}

val CurrentBakeUiState.tierLabel: String? get() = tierLabel(userTier, combinedCount, maxActiveBakes)

/**
 * Ported from the iOS app's `Screens/CurrentBakeScreen.swift`.
 *
 * **Data source: observed Room queries, not a shared context/store** —
 * same dissolution pattern as [com.BreadIQ.myapp.viewmodel.QueueViewModel]/
 * [com.BreadIQ.myapp.viewmodel.RecipesViewModel].
 *
 * **No parent-level "tick" source needed** — each card that needs one
 * (`BakeCard`, `ScheduledBakeCard`) already self-ticks via its own
 * `LaunchedEffect`, matching the iOS port's own already-made design call
 * (confirmed there against the real already-built code, not assumed) —
 * a screen-level tick here would be pure redundant busywork.
 *
 * **Deleting a scheduled bake's owned `QueuedBake`/config is a single
 * `ScheduledBakeDao.deleteById` call, not the iOS source's explicit
 * leaf-first delete order.** The iOS source deletes `queueItem.config`,
 * then `queueItem`, then the `ScheduledBake` itself explicitly, working
 * around a real SwiftData assertion-failure crash confirmed via a
 * TestFlight crash log when relying on its own nested-relationship
 * cascade. Room's `ON DELETE CASCADE` (declared directly on
 * `QueuedBakeEntity.scheduledBakeId`'s foreign key, cascading again from
 * there to its own `QueuedBakeConfigEntity`) is a SQL-level guarantee
 * with no equivalent failure mode — deleting the `ScheduledBakeEntity`
 * row correctly cascades through both owned rows in one call. This is
 * an accurate port of the OBSERVABLE behavior (both owned rows are
 * gone), not a literal port of the workaround a different persistence
 * framework's bug required.
 */
class CurrentBakeViewModel(
    private val bakeSessionDao: BakeSessionDao,
    private val scheduledBakeDao: ScheduledBakeDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrentBakeUiState())
    val uiState: StateFlow<CurrentBakeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bakeSessionDao.observeAll().collect { rows ->
                _uiState.value = _uiState.value.copy(sessions = rows.map { it.toDomain() })
            }
        }
        viewModelScope.launch {
            scheduledBakeDao.observeAllAsDomain().collect { bakes ->
                _uiState.value = _uiState.value.copy(scheduledBakes = bakes.sortedBy { it.startTime })
            }
        }
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after a successful start. */
    fun startScheduled(bake: ScheduledBake) {
        val s = _uiState.value
        _uiState.value = s.copy(startingId = bake.id)

        val rawSteps = bake.queueItem.steps.map { RawBakeStep(label = it.label, description = it.description, durationMinutes = it.durationMinutes) }
        val result = BakeSessionEngine.startBake(
            name = bake.name, style = bake.style, steps = rawSteps, ovenTempF = bake.queueItem.ovenTempF,
            isSpeedRun = bake.queueItem.config.isSpeedRun, tier = s.userTier, existingSessions = s.sessions,
        )
        _uiState.value = _uiState.value.copy(startingId = null)

        when (result) {
            is BakeStartResult.Failure -> _uiState.value = _uiState.value.copy(startError = result.failure.message)
            is BakeStartResult.Success -> {
                val session = result.session
                viewModelScope.launch {
                    bakeSessionDao.upsertSessionWithSteps(session.toEntity(), session.steps.map { it.toEntity(session.id) })
                    scheduledBakeDao.deleteById(bake.id)
                    BakeNotificationScheduler.afterStart(session)
                    _uiState.value = _uiState.value.copy(startedSessionId = session.id)
                }
            }
        }
    }

    /**
     * The dialog that triggers this promises "Push notifications will
     * be removed" — both `BakeNotificationScheduler.cancel` calls below
     * are real now (notifications session); `startReminderNotifId`/
     * `startTimeNotifId` are still harmless no-ops today regardless,
     * since nothing in the app populates them yet (see
     * `ScheduledBakePlanner`'s own doc comment — a real, pre-existing
     * iOS scope gap, not something this port is missing).
     *
     * Also removes the real `CalendarContract` event
     * `ScheduleViewModel.addToCalendar` may have created for this bake
     * (calendar-integration session) — this was previously the one
     * still-silent no-op here, leaving orphaned calendar events behind
     * on every cancel.
     */
    fun removeScheduled(bake: ScheduledBake) {
        BakeNotificationScheduler.cancel(bake.startReminderNotifId)
        BakeNotificationScheduler.cancel(bake.startTimeNotifId)
        viewModelScope.launch {
            bake.calendarEventId?.let { CalendarEventScheduler.removeBakeEvent(it) }
            scheduledBakeDao.deleteById(bake.id)
        }
    }

    fun togglePause(session: BakeSession) {
        val previousStep = session.orderedSteps.getOrNull(session.currentStepIndex)?.let { BakeNotificationScheduler.snapshot(it) }
        val updated = if (session.status == BakeStatus.PAUSED) BakeSessionEngine.resumeBake(session) else BakeSessionEngine.pauseBake(session)
        viewModelScope.launch {
            bakeSessionDao.upsertSessionWithSteps(updated.toEntity(), updated.steps.map { it.toEntity(updated.id) })
            if (previousStep != null) BakeNotificationScheduler.syncAfterMutation(updated, previousStep, session.ovenPreheatNotifId)
        }
    }

    /** Fire `Haptics.impact(context, HapticImpactStyle.HEAVY)` from the Composable right after calling this. */
    fun abandon(session: BakeSession) {
        BakeNotificationScheduler.cancelEverything(session)
        viewModelScope.launch { bakeSessionDao.deleteById(session.id) }
    }

    fun clearStartError() {
        _uiState.value = _uiState.value.copy(startError = null)
    }

    fun clearStartedSessionId() {
        _uiState.value = _uiState.value.copy(startedSessionId = null)
    }
}

class CurrentBakeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return CurrentBakeViewModel(bakeSessionDao = db.bakeSessionDao(), scheduledBakeDao = db.scheduledBakeDao()) as T
    }
}
