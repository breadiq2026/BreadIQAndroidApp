package com.BreadIQ.myapp.core

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Ported from the iOS app's `Core/BakeDetailFormatting.swift` — pure
 * decision/formatting logic for the Bake Detail screen, independent of
 * the live-ticking view itself.
 *
 * **`stepExpired` is deliberately NOT ported as its own tracked flag.**
 * The source keeps a stateful boolean, reset/set via effects guarded so
 * a one-time haptic fires only once. Since `stepRemainingMs` is already
 * clamped to a minimum of 0, "expired" is fully recoverable as a pure
 * function of `stepRemainingMs == 0` — a freshly-activated step always
 * has a `scheduledEndAt` in the future (positive remaining), so this
 * naturally resets itself without an explicit reset effect.
 */
object BakeDetailFormatting {

    /** `formatMs()` — `1:05` under an hour, `1:05:30` at/over one hour. */
    fun formatMs(ms: Double): String {
        val total = ceil(max(0.0, ms) / 1000).toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${"%02d".format(m)}:${"%02d".format(s)}" else "$m:${"%02d".format(s)}"
    }

    /** `formatElapsedShort()` — e.g. "2h 15m" or "45m". */
    fun formatElapsedShort(ms: Double): String {
        val h = (ms / 3_600_000).toInt()
        val m = ((ms % 3_600_000.0) / 60_000).toInt()
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * `stepRemaining` — works during pause too, reading from `pausedAt`
     * instead of live `now`. `null` when the current step has no
     * `scheduledEndAt` at all (noTimer, or a manualStart step not yet started).
     */
    fun stepRemainingMs(scheduledEndAt: Instant?, isActive: Boolean, isPaused: Boolean, pausedAt: Instant?, now: Instant): Double? {
        scheduledEndAt ?: return null
        if (isActive) return max(0.0, Duration.between(now, scheduledEndAt).toMillis().toDouble())
        if (isPaused && pausedAt != null) return max(0.0, Duration.between(pausedAt, scheduledEndAt).toMillis().toDouble())
        return null
    }

    /** `elapsed` — total bake time, frozen once complete or paused. */
    fun elapsedMs(startedAt: Instant, pausedDurationMs: Double, isComplete: Boolean, lastStepActualEndAt: Instant?, isPaused: Boolean, pausedAt: Instant?, now: Instant): Double {
        if (isComplete) {
            val end = lastStepActualEndAt ?: now
            return Duration.between(startedAt, end).toMillis().toDouble()
        }
        if (isPaused && pausedAt != null) {
            return Duration.between(startedAt, pausedAt).toMillis().toDouble() - pausedDurationMs
        }
        return Duration.between(startedAt, now).toMillis().toDouble() - pausedDurationMs
    }

    fun isStepExpired(stepRemainingMs: Double?): Boolean = stepRemainingMs == 0.0

    /** `arcProgress` — 1.0 = just started/no timer, 0.0 = expired. */
    fun arcProgress(hasCurrentStep: Boolean, stepExpired: Boolean, stepRemainingMs: Double?, durationMinutes: Int): Double {
        if (!hasCurrentStep) return 0.0
        if (stepExpired) return 0.0
        val remaining = stepRemainingMs ?: return 1.0
        val totalMs = durationMinutes * 60_000.0
        if (totalMs <= 0) return 0.0
        return max(0.0, min(1.0, remaining / totalMs))
    }

    enum class ArcColorState { DONE, TRACK, CRITICAL, WARNING, NORMAL }

    /**
     * `arcColor` — semantic state, not a color, so this stays UI-
     * framework-independent and testable; the screen maps each case to
     * its actual color.
     */
    fun arcColorState(stepExpired: Boolean, isActive: Boolean, stepRemainingMs: Double?): ArcColorState {
        if (stepExpired) return ArcColorState.DONE
        if (!isActive || stepRemainingMs == null) return ArcColorState.TRACK
        if (stepRemainingMs <= 10_000) return ArcColorState.CRITICAL
        if (stepRemainingMs <= 30_000) return ArcColorState.WARNING
        return ArcColorState.NORMAL
    }

    fun advanceLabel(stepExpired: Boolean, nextStepLabel: String?): String {
        if (!stepExpired) return "Complete Step"
        return nextStepLabel?.let { "Begin $it" } ?: "Finish Bake"
    }

    fun displayDescription(stepExpired: Boolean, nextStepLabel: String?, nextStepDescription: String?, currentDescription: String?): String {
        if (!stepExpired) return currentDescription ?: ""
        val label = nextStepLabel ?: return "Every step is done. Your bake is complete."
        val desc = nextStepDescription ?: ""
        return desc.ifEmpty { "Up next: $label" }
    }

    fun displayStepName(stepExpired: Boolean, currentLabel: String?): String =
        if (stepExpired) "Step complete." else (currentLabel ?: "")

    enum class StepNameColorState { DONE, ACTIVE, MUTED }

    fun stepNameColorState(stepExpired: Boolean, isActive: Boolean): StepNameColorState {
        if (stepExpired) return StepNameColorState.DONE
        return if (isActive) StepNameColorState.ACTIVE else StepNameColorState.MUTED
    }

    /**
     * `handleAdvance`'s 75%-elapsed early-completion check — `true`
     * means show the "Complete Early?" confirmation instead of
     * advancing immediately.
     */
    fun needsEarlyCompletionConfirm(stepExpired: Boolean, scheduledEndAt: Instant?, durationMinutes: Int, now: Instant): Boolean {
        if (stepExpired || scheduledEndAt == null) return false
        val totalMs = durationMinutes * 60_000.0
        if (totalMs <= 0) return false
        val remainingMs = max(0.0, Duration.between(now, scheduledEndAt).toMillis().toDouble())
        val elapsedFraction = 1 - remainingMs / totalMs
        return elapsedFraction < 0.75
    }

    /**
     * The journey-timeline row's right-hand text: a checkmark for done
     * steps, a live countdown for the current active step, an em dash
     * for `noTimer` steps, otherwise the plain duration.
     */
    fun timelineRightText(isDone: Boolean, isCurrent: Boolean, isActive: Boolean, stepRemainingMs: Double?, noTimer: Boolean, durationMinutes: Int): String {
        if (isDone) return "✓"
        if (isCurrent && isActive && stepRemainingMs != null) return formatMs(stepRemainingMs)
        if (noTimer) return "—"
        return "${durationMinutes}m"
    }

    // MARK: - Haptic countdown pulses — `HAPTIC_THRESHOLDS`/the countdown tick

    val hapticThresholdSeconds: Set<Int> = setOf(30, 20, 10, 5, 3, 2, 1)

    data class CountdownPulse(val secLeft: Int, val style: HapticImpactStyle)

    /**
     * `secLeft <= 5 ? Medium : Light`, gated on the threshold set AND
     * "not already fired for this second" — [lastFiredSecond] is the
     * screen's own tracked state (there's no ref-equivalent that
     * survives across ticks other than that), reimplemented here as an
     * explicit parameter rather than internal mutable state.
     */
    fun countdownPulse(stepRemainingMs: Double?, isActive: Boolean, lastFiredSecond: Int?): CountdownPulse? {
        if (!isActive || stepRemainingMs == null || stepRemainingMs <= 0) return null
        val secLeft = ceil(stepRemainingMs / 1000).toInt()
        if (secLeft !in hapticThresholdSeconds || secLeft == lastFiredSecond) return null
        return CountdownPulse(secLeft, if (secLeft <= 5) HapticImpactStyle.MEDIUM else HapticImpactStyle.LIGHT)
    }

    // MARK: - Step-expiry warning haptic

    /**
     * `isActive && stepRemaining != null && stepRemaining <= 0 &&
     * !stepExpired` — the source's own stateful `stepExpired` flag only
     * exists to gate this exact "fire once" check. Reimplemented against
     * a `warnedStepId` the screen holds instead: comparing step identity
     * (not a boolean) additionally guards against re-firing when a NEW
     * step's countdown also happens to reach the expired state, which a
     * plain "already warned this session" flag wouldn't distinguish.
     */
    fun shouldFireExpiryWarning(stepExpired: Boolean, isActive: Boolean, currentStepId: String?, warnedStepId: String?): Boolean {
        if (!isActive || !stepExpired || currentStepId == null) return false
        return currentStepId != warnedStepId
    }
}
