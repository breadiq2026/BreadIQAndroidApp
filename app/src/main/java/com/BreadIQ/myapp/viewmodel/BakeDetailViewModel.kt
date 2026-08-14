package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.BakeDetailFormatting
import com.BreadIQ.myapp.core.BakeNotificationScheduler
import com.BreadIQ.myapp.core.BakeSessionEngine
import com.BreadIQ.myapp.core.BakeStepNotificationSnapshot
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.data.local.toDomain
import com.BreadIQ.myapp.data.local.toEntity
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class BakeDetailUiState(
    val session: BakeSession? = null,
    val earlyCompletionLabel: String? = null,
    val earlyCompletionRemainingText: String? = null,
    val showAbandonConfirm: Boolean = false,
    val showFullTimeline: Boolean = false,
    val lastPulsedSecond: Int? = null,
    val warnedStepId: String? = null,
    /** Set once the session is abandoned — the screen observes this and navigates back. */
    val dismissed: Boolean = false,
)

/** What [BakeDetailViewModel.handleTick] found this tick — the Composable fires these (needs a `Context`, see `Haptics.kt`'s own doc comment). */
data class TickHaptics(val impact: HapticImpactStyle? = null, val warning: Boolean = false)

/**
 * Ported from the iOS app's `Screens/BakeDetailScreen.swift` — the live
 * active-bake tracker, the highest-risk screen in the app per the iOS
 * port's own roadmap note.
 *
 * **`session` is derived from the same observed [BakeSessionDao.observeAll]
 * query every other bake screen uses, filtered to [sessionId]** —
 * mirrors the source's own `@Query private var allSessions: [BakeSession]`
 * then `allSessions.first { $0.id == sessionId }`, not a separate
 * single-row query. This keeps this screen's session in sync with
 * mutations made anywhere else in the app the instant Room emits, with
 * no extra plumbing.
 *
 * **All "now"-dependent derived values (`stepRemainingMs`, `elapsedMs`,
 * arc progress/color, etc.) are computed in the Composable, not here** —
 * matches the iOS source's own structure ([BakeDetailFormatting] calls
 * live in the View's own body functions, not in a store), and keeps
 * this ViewModel from needing to re-tick its own state every second
 * independent of the UI's own tick loop.
 */
class BakeDetailViewModel(
    private val sessionId: String,
    private val bakeSessionDao: BakeSessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BakeDetailUiState())
    val uiState: StateFlow<BakeDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bakeSessionDao.observeAll().collect { rows ->
                val session = rows.map { it.toDomain() }.firstOrNull { it.id == sessionId }
                _uiState.value = _uiState.value.copy(session = session)
            }
        }
    }

    fun setShowFullTimeline(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFullTimeline = show)
    }

    fun setShowAbandonConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAbandonConfirm = show)
    }

    fun dismissEarlyCompletionConfirm() {
        _uiState.value = _uiState.value.copy(earlyCompletionLabel = null, earlyCompletionRemainingText = null)
    }

    fun handleAdvance(now: Instant) {
        val session = _uiState.value.session ?: return
        val currentStep = session.orderedSteps.getOrNull(session.currentStepIndex)
        val isActive = session.status == BakeStatus.ACTIVE
        val isPaused = session.status == BakeStatus.PAUSED
        val stepRemainingMs = BakeDetailFormatting.stepRemainingMs(currentStep?.scheduledEndAt, isActive, isPaused, session.pausedAt, now)
        val stepExpired = BakeDetailFormatting.isStepExpired(stepRemainingMs)

        if (currentStep != null && BakeDetailFormatting.needsEarlyCompletionConfirm(stepExpired, currentStep.scheduledEndAt, currentStep.durationMinutes, now)) {
            val remainingMs = currentStep.scheduledEndAt?.let { maxOf(0.0, Duration.between(now, it).toMillis().toDouble()) } ?: 0.0
            _uiState.value = _uiState.value.copy(earlyCompletionLabel = currentStep.label, earlyCompletionRemainingText = BakeDetailFormatting.formatMs(remainingMs))
            return
        }
        performAdvance(session, now)
    }

    /** Fire `Haptics.notification(context, HapticNotificationType.SUCCESS)` from the Composable right after calling this — see `Haptics.kt`'s own doc comment. */
    fun confirmEarlyCompletion(now: Instant) {
        val session = _uiState.value.session ?: return
        _uiState.value = _uiState.value.copy(earlyCompletionLabel = null, earlyCompletionRemainingText = null)
        performAdvance(session, now)
    }

    private fun performAdvance(session: BakeSession, now: Instant) {
        val previousStep = session.orderedSteps.getOrNull(session.currentStepIndex)?.let { BakeNotificationScheduler.snapshot(it) } ?: BakeStepNotificationSnapshot()
        val previousOvenPreheatId = session.ovenPreheatNotifId
        val updated = BakeSessionEngine.advanceStep(session, now)
        viewModelScope.launch {
            bakeSessionDao.upsertSessionWithSteps(updated.toEntity(), updated.steps.map { it.toEntity(updated.id) })
            BakeNotificationScheduler.syncAfterMutation(updated, previousStep, previousOvenPreheatId)
        }
    }

    fun togglePauseResume() {
        val session = _uiState.value.session ?: return
        val previousStep = session.orderedSteps.getOrNull(session.currentStepIndex)?.let { BakeNotificationScheduler.snapshot(it) } ?: BakeStepNotificationSnapshot()
        val updated = if (session.status == BakeStatus.PAUSED) BakeSessionEngine.resumeBake(session) else BakeSessionEngine.pauseBake(session)
        viewModelScope.launch {
            bakeSessionDao.upsertSessionWithSteps(updated.toEntity(), updated.steps.map { it.toEntity(updated.id) })
            BakeNotificationScheduler.syncAfterMutation(updated, previousStep, session.ovenPreheatNotifId)
        }
    }

    fun startStepTimer() {
        val session = _uiState.value.session ?: return
        val updated = BakeSessionEngine.startStepTimer(session)
        viewModelScope.launch {
            bakeSessionDao.upsertSessionWithSteps(updated.toEntity(), updated.steps.map { it.toEntity(updated.id) })
            BakeNotificationScheduler.syncAfterMutation(updated, BakeStepNotificationSnapshot(), session.ovenPreheatNotifId)
        }
    }

    fun extendStep(extraMinutes: Int = 30) {
        val session = _uiState.value.session ?: return
        val previousStep = session.orderedSteps.getOrNull(session.currentStepIndex)?.let { BakeNotificationScheduler.snapshot(it) } ?: BakeStepNotificationSnapshot()
        val updated = BakeSessionEngine.extendStep(session, extraMinutes)
        viewModelScope.launch {
            bakeSessionDao.upsertSessionWithSteps(updated.toEntity(), updated.steps.map { it.toEntity(updated.id) })
            BakeNotificationScheduler.syncAfterMutation(updated, previousStep, session.ovenPreheatNotifId)
        }
    }

    /** Fire `Haptics.impact(context, HapticImpactStyle.HEAVY)` from the Composable right after calling this. */
    fun abandon() {
        val session = _uiState.value.session ?: return
        BakeNotificationScheduler.cancelEverything(session)
        viewModelScope.launch {
            bakeSessionDao.deleteById(session.id)
            _uiState.value = _uiState.value.copy(dismissed = true, showAbandonConfirm = false)
        }
    }

    /**
     * Fires once per second via the screen's own tick loop, matching
     * the source's two dependency-array-less effects (both literally
     * run "on every render," which for this screen means every tick).
     */
    fun handleTick(stepRemainingMs: Double?, isActive: Boolean, stepExpired: Boolean, currentStepId: String?): TickHaptics {
        val s = _uiState.value
        var impact: HapticImpactStyle? = null
        var warning = false

        BakeDetailFormatting.countdownPulse(stepRemainingMs, isActive, s.lastPulsedSecond)?.let { pulse ->
            _uiState.value = _uiState.value.copy(lastPulsedSecond = pulse.secLeft)
            impact = pulse.style
        }
        if (BakeDetailFormatting.shouldFireExpiryWarning(stepExpired, isActive, currentStepId, s.warnedStepId)) {
            _uiState.value = _uiState.value.copy(warnedStepId = currentStepId)
            warning = true
        }
        return TickHaptics(impact, warning)
    }
}

class BakeDetailViewModelFactory(private val context: Context, private val sessionId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = DatabaseProvider.getInstance(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return BakeDetailViewModel(sessionId = sessionId, bakeSessionDao = db.bakeSessionDao()) as T
    }
}
