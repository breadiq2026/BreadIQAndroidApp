package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    /** Set once scheduling succeeds — consumed once by the screen's own confirmation + dismiss flow. */
    val scheduled: ScheduledBake? = null,
    /** Hardcoded to [BakeUserTier.FREE] — same stub as `CalculatorUiState.userTier`, no `SubscriptionStore` port exists yet. */
    val userTier: BakeUserTier = BakeUserTier.FREE,
)

/**
 * Ported from the iOS app's `Screens/ScheduleModal.swift` — the
 * scheduling data-entry/validation logic. Real `activeBakesCount`/
 * `existingScheduledCount` observed from Room, feeding the same
 * [ScheduledBakePlanner.scheduleBake] gate the source uses.
 *
 * **Calendar event creation stays out of scope**, per direct
 * instruction — matches [BakeNotificationScheduler]'s own deferred
 * boundary. The source's own post-schedule "Bake Scheduled — Open
 * Calendar?" confirmation exists specifically to work around a real
 * SwiftUI `.sheet` + `.alert` dismissal-timing bug (an alert attached to
 * a view that's simultaneously dismissing as a sheet doesn't reliably
 * present) — that failure mode doesn't exist in Compose Navigation
 * (this screen is a normal nav destination, not a `.sheet`), so the
 * confirmation is shown directly by this screen itself rather than
 * being handed back to the caller to present, and simplified to a
 * single "OK" dismiss (no calendar prompt, since calendar creation
 * itself isn't wired up to prompt for).
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
}

class ScheduleViewModelFactory(private val context: Context, private val plan: RawScheduledBakePlan) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return ScheduleViewModel(plan = plan, bakeSessionDao = db.bakeSessionDao(), scheduledBakeDao = db.scheduledBakeDao()) as T
    }
}
