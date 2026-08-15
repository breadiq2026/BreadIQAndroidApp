package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.CalendarEventResult
import com.BreadIQ.myapp.core.CalendarEventScheduler
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.ScheduleBakeResult
import com.BreadIQ.myapp.core.ScheduledBakePlanner
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.ScheduledBakeDao
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.data.local.toEntity
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.ScheduledBake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ScheduleUiState(
    val activeBakesCount: Int = 0,
    val existingScheduledCount: Int = 0,
    val submitting: Boolean = false,
    val error: String? = null,
    /**
     * Set once scheduling succeeds — drives the screen's own "Bake
     * Scheduled" confirmation dialog ("No Thanks" / "Add to Calendar").
     * Cleared either immediately (dismiss / "No Thanks") or by
     * [addToCalendar] once it resolves (success or failure) — the
     * screen distinguishes those two outcomes via [calendarError] being
     * set or not, not via this field.
     */
    val scheduled: ScheduledBake? = null,
    /** True while [addToCalendar]'s coroutine is in flight — disables the confirmation dialog's buttons so a second tap can't fire a duplicate calendar write. */
    val addingToCalendar: Boolean = false,
    /** Set on a failed [addToCalendar] call — surfaced by the screen's own separate "Couldn't Add to Calendar" dialog, matching the source's distinct `calendarEventError` alert. */
    val calendarError: String? = null,
    /** Hardcoded to [BakeUserTier.FREE] — same stub as `CalculatorUiState.userTier`, no `SubscriptionStore` port exists yet. */
    val userTier: BakeUserTier = BakeUserTier.FREE,
)

/**
 * Ported from the iOS app's `Screens/ScheduleModal.swift` — the
 * scheduling data-entry/validation logic. Real `activeBakesCount`/
 * `existingScheduledCount` observed from Room, feeding the same
 * [ScheduledBakePlanner.scheduleBake] gate the source uses.
 *
 * **The source's own post-schedule "Bake Scheduled — Add to Calendar?"
 * confirmation is shown directly by this screen itself**, not handed
 * back to a caller the way `CalculatorScreen.swift`'s own
 * `scheduledConfirmation`/`addToCalendar` do it — that two-hop shape
 * exists there to work around a real SwiftUI `.sheet` + `.alert`
 * dismissal-timing bug (an alert attached to a view that's
 * simultaneously dismissing as a sheet doesn't reliably present), which
 * doesn't apply here (this screen is a normal Compose nav destination,
 * not a `.sheet`). [addToCalendar] below is this screen's own
 * counterpart of that same `addToCalendar(_:)` function, just called
 * locally instead of on a `ScheduledBake` handed back up.
 */
class ScheduleViewModel(
    private val plan: RawScheduledBakePlan,
    private val bakeSessionDao: BakeSessionDao,
    private val scheduledBakeDao: ScheduledBakeDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    val totalStepMinutes: Int = plan.steps.sumOf { it.durationMinutes }
    val defaultTargetFinishTime: Instant = ScheduledBakePlanner.defaultTargetFinishTime(totalStepMinutes)

    init {
        viewModelScope.launch {
            bakeSessionDao.observeAll().collect { rows ->
                val count = rows.map { it.toDomain() }.count { it.status == BakeStatus.ACTIVE || it.status == BakeStatus.PAUSED }
                _uiState.value = _uiState.value.copy(activeBakesCount = count)
            }
        }
        viewModelScope.launch {
            scheduledBakeDao.observeAll().collect { rows ->
                _uiState.value = _uiState.value.copy(existingScheduledCount = rows.size)
            }
        }
    }

    /** Fire `Haptics.impact(context, HapticImpactStyle.MEDIUM)` from the Composable right before calling this — see this class's own doc comment on the source's haptic-timing convention. */
    fun schedule(targetFinishTime: Instant) {
        val s = _uiState.value
        if (s.submitting) return
        _uiState.value = s.copy(submitting = true, error = null)

        when (val result = ScheduledBakePlanner.scheduleBake(plan, targetFinishTime, s.activeBakesCount, s.existingScheduledCount, s.userTier)) {
            is ScheduleBakeResult.Failure -> _uiState.value = _uiState.value.copy(submitting = false, error = result.failure.message)
            is ScheduleBakeResult.Success -> {
                val scheduled = result.scheduled
                viewModelScope.launch {
                    scheduledBakeDao.upsertScheduledBakeWithQueueItem(
                        scheduled.toEntity(),
                        scheduled.queueItem.toEntity(scheduledBakeId = scheduled.id),
                        scheduled.queueItem.config.toEntity(scheduled.queueItem.id),
                    )
                    // submitting intentionally left true — the screen is about to navigate away.
                    _uiState.value = _uiState.value.copy(scheduled = scheduled)
                }
            }
        }
    }

    /**
     * `addToCalendar(_:)`. The Compose call site (`ScheduleScreen.kt`'s
     * "Add to Calendar" button) is expected to have already ensured the
     * `READ_CALENDAR`/`WRITE_CALENDAR` permission dialog was shown if
     * needed before calling this — see [CalendarEventScheduler]'s own
     * doc comment for why that has to happen at the Composable layer.
     *
     * Clears [scheduled] on BOTH success and failure — the confirmation
     * dialog closes the instant this fires either way (matching the
     * source's own alert-button-tap-always-dismisses behavior); a
     * failure is then surfaced through the separate [calendarError]
     * dialog instead of by keeping the original one open.
     */
    fun addToCalendar(scheduled: ScheduledBake) {
        if (_uiState.value.addingToCalendar) return
        _uiState.value = _uiState.value.copy(addingToCalendar = true)
        viewModelScope.launch {
            when (val result = CalendarEventScheduler.addBakeEvent(scheduled.name, scheduled.startTime, scheduled.targetFinishTime)) {
                is CalendarEventResult.Success -> {
                    scheduledBakeDao.updateCalendarEventId(scheduled.id, result.eventId)
                    _uiState.value = _uiState.value.copy(addingToCalendar = false, scheduled = null)
                }
                is CalendarEventResult.Failure -> {
                    _uiState.value = _uiState.value.copy(addingToCalendar = false, scheduled = null, calendarError = result.failure.message)
                }
            }
        }
    }

    /** "No Thanks" / dismiss — closes the confirmation dialog without touching the calendar. */
    fun dismissScheduledConfirmation() {
        _uiState.value = _uiState.value.copy(scheduled = null)
    }

    fun clearCalendarError() {
        _uiState.value = _uiState.value.copy(calendarError = null)
    }
}

class ScheduleViewModelFactory(private val context: Context, private val plan: RawScheduledBakePlan) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return ScheduleViewModel(plan = plan, bakeSessionDao = db.bakeSessionDao(), scheduledBakeDao = db.scheduledBakeDao()) as T
    }
}
