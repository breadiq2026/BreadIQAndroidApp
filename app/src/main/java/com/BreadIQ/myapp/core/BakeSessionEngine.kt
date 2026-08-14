package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeStep
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.StepStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Input for [BakeSessionEngine.startBake] — mirrors the iOS source's
 * `RawStep`, trimmed to the fields `startBake` actually reads.
 */
data class RawBakeStep(
    val label: String,
    val description: String,
    val durationMinutes: Int,
    val noTimer: Boolean = false,
    val manualStart: Boolean = false,
)

/**
 * [BakeUserTier] is already ported (`model/BakeUserTier.kt`, split out
 * early during the Calculator session since Calculator's own tier
 * gating needed it before this engine existed) — confirmed it matches
 * the iOS source's `free`/`basic`/`premium` cases exactly, reused as-is
 * here rather than re-declared.
 */
sealed class BakeStartFailure {
    data object TierNotSupported : BakeStartFailure()
    data class ActiveBakeLimitReached(val tier: BakeUserTier, val maxActiveBakes: Int) : BakeStartFailure()

    val message: String
        get() = when (this) {
            is TierNotSupported -> "Active Bake is a Basic or Premium feature. Upgrade to start tracking bakes."
            is ActiveBakeLimitReached -> if (tier == BakeUserTier.BASIC) {
                "Basic plan supports 1 active bake. Upgrade to Premium for 3 simultaneous bakes."
            } else {
                "You already have $maxActiveBakes bakes running. Complete or abandon one before starting another."
            }
        }
}

/** Kotlin counterpart of the iOS source's `Result<BakeSession, BakeStartFailure>`. */
sealed class BakeStartResult {
    data class Success(val session: BakeSession) : BakeStartResult()
    data class Failure(val failure: BakeStartFailure) : BakeStartResult()
}

/** Paired with a boolean the same way the source's `@discardableResult` `Bool` return works — see [BakeSessionEngine.reconcile]. */
data class ReconcileResult(val session: BakeSession, val changed: Boolean)

/**
 * Ported from the iOS app's `Core/BakeSessionEngine.swift` — the active-
 * bake tracker's core state-machine transitions: [startBake],
 * [advanceStep], [pauseBake], [resumeBake], [startStepTimer],
 * [extendStep], [abandonBake], and the wall-clock catch-up logic
 * ([reconcile]).
 *
 * **Scope boundary, deliberate, matching the iOS source exactly**:
 * actual notification scheduling (permission checks, calling into
 * Android's `NotificationManager`, canceling by id) is a later, native-
 * integration-specific porting item, not this one. But two pieces of
 * genuinely pure domain logic that live inside that scheduling code ARE
 * ported here, since a future notification-scheduling step will need
 * them and they're fully testable independent of any notification API:
 * [ovenPreheatFireTime] (when to fire the preheat reminder) and
 * [wantsCoilFolds]/[coilFoldFireTimes] (which steps get mid-bulk fold
 * reminders, and when).
 *
 * **API shape, adapted for Kotlin rather than transliterated**: the iOS
 * source's transitions mutate a SwiftData `@Model` class in place
 * (reference semantics — `cur.status = .completed`, `session.currentStepIndex
 * = nextIndex`, etc.), since `BakeSession`/`BakeStep` are reference
 * types there. This port's domain models are plain immutable `data class`es
 * (the same convention every other state holder in this app already
 * uses — `CalculatorUiState`, `AuthUiState`, ...), so every transition
 * here takes a [BakeSession] and returns a NEW [BakeSession] (built via
 * `.copy()`) reflecting the same change, rather than mutating anything.
 * [abandonBake] is the one function that already had an array-in/array-out
 * shape in the source (kept identical) — every other function's
 * single-session-in/single-session-out shape is this port's own,
 * mechanical adaptation of the source's single-session-in/mutate-in-place
 * shape, not a source-shape port.
 *
 * **Two real bugs the iOS source found in itself and already fixed
 * there** — [reconcile]'s own doc comment carries the full write-up
 * forward; this port simply implements the already-corrected logic
 * (chaining a cascading step's `scheduledEndAt` off the step that just
 * "ended" rather than off `now`, and checking `noTimer || manualStart`
 * — not `noTimer` alone — before auto-starting a newly-activated step's
 * timer).
 */
object BakeSessionEngine {

    val maxActiveBakes: Map<BakeUserTier, Int> = mapOf(BakeUserTier.FREE to 0, BakeUserTier.BASIC to 1, BakeUserTier.PREMIUM to 3)

    private const val COIL_FOLD_BULK_MIN_MINUTES = 60
    private val coilFoldStyles: Set<String> = setOf("artisan", "ciabatta")

    /** Replaces the step with matching [BakeStep.id], leaving every other step untouched. The mechanical building block every transition below uses in place of the source's in-place field mutation. */
    private fun BakeSession.replacingStep(step: BakeStep): BakeSession =
        copy(steps = steps.map { if (it.id == step.id) step else it })

    // MARK: - startBake

    fun startBake(
        name: String,
        style: String,
        steps: List<RawBakeStep>,
        ovenTempF: Double = 450.0,
        isSpeedRun: Boolean = false,
        tier: BakeUserTier,
        existingSessions: List<BakeSession>,
        now: Instant = Instant.now(),
    ): BakeStartResult {
        val maxBakes = maxActiveBakes[tier] ?: 0
        val activeCount = existingSessions.count { it.status != BakeStatus.COMPLETED }

        if (maxBakes == 0) return BakeStartResult.Failure(BakeStartFailure.TierNotSupported)
        if (activeCount >= maxBakes) return BakeStartResult.Failure(BakeStartFailure.ActiveBakeLimitReached(tier, maxBakes))

        val builtSteps = steps.mapIndexed { index, raw ->
            val isFirst = index == 0
            val scheduledEndAt = if (isFirst && !raw.noTimer && !raw.manualStart) now.plus(Duration.ofMinutes(raw.durationMinutes.toLong())) else null
            BakeStep(
                id = UUID.randomUUID().toString(),
                label = raw.label,
                description = raw.description.ifEmpty { BakeStepContentLookup.stepDescription(raw.label) },
                durationMinutes = raw.durationMinutes,
                order = index,
                noTimer = raw.noTimer,
                manualStart = raw.manualStart,
                scheduledEndAt = scheduledEndAt,
                status = if (isFirst) StepStatus.ACTIVE else StepStatus.PENDING,
            )
        }

        val session = BakeSession(
            name = name,
            style = style,
            isSpeedRun = isSpeedRun,
            startedAt = now,
            currentStepIndex = 0,
            status = BakeStatus.ACTIVE,
            ovenTempF = ovenTempF,
            steps = builtSteps,
        )
        return BakeStartResult.Success(session)
    }

    // MARK: - advanceStep

    /** No-op if the session isn't active. */
    fun advanceStep(session: BakeSession, now: Instant = Instant.now()): BakeSession {
        if (session.status != BakeStatus.ACTIVE || session.currentStepIndex >= session.steps.size) return session
        val ordered = session.orderedSteps
        val cur = ordered[session.currentStepIndex]
        val completedCur = cur.copy(status = StepStatus.COMPLETED, actualEndAt = now, notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList())

        val nextIndex = session.currentStepIndex + 1
        if (nextIndex >= session.steps.size) {
            // Session complete. currentStepIndex deliberately left
            // unchanged — still pointing at the now-completed last step
            // — matching the source exactly, which never increments it
            // in this branch.
            return session.replacingStep(completedCur).copy(status = BakeStatus.COMPLETED, ovenPreheatNotifId = null)
        }

        val next = ordered[nextIndex]
        val nextHasAutoTimer = !(next.manualStart || next.noTimer)
        val activatedNext = next.copy(
            status = StepStatus.ACTIVE,
            scheduledEndAt = if (nextHasAutoTimer) now.plus(Duration.ofMinutes(next.durationMinutes.toLong())) else null,
            notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList(),
        )

        return session.replacingStep(completedCur).replacingStep(activatedNext).copy(currentStepIndex = nextIndex)
    }

    // MARK: - pauseBake

    /**
     * No-op if the session isn't active. Deliberately does NOT touch
     * the current step's `scheduledEndAt` — it's left pointing at the
     * original (now-stale) wall-clock target; [resumeBake] is what
     * recomputes it using `pausedAt`.
     */
    fun pauseBake(session: BakeSession, now: Instant = Instant.now()): BakeSession {
        if (session.status != BakeStatus.ACTIVE) return session
        return session.copy(status = BakeStatus.PAUSED, pausedAt = now)
    }

    // MARK: - resumeBake

    /** No-op if the session isn't paused (or `pausedAt` is somehow null despite the paused status — an invariant-violation guard the source has too). */
    fun resumeBake(session: BakeSession, now: Instant = Instant.now()): BakeSession {
        val pausedAt = session.pausedAt
        if (session.status != BakeStatus.PAUSED || pausedAt == null) return session

        val pausedMs = Duration.between(pausedAt, now).toMillis().toDouble()
        val totalPausedMs = session.pausedDurationMs + pausedMs

        var updated = session
        if (session.currentStepIndex < session.steps.size) {
            val cur = session.orderedSteps[session.currentStepIndex]
            val notYetStartedManual = cur.manualStart && cur.scheduledEndAt == null
            // noTimer steps, and manualStart steps not yet started, have
            // no running countdown to resume — leave them untouched.
            if (!(cur.noTimer || notYetStartedManual)) {
                val remainingMs = cur.scheduledEndAt?.let { scheduledEndAt ->
                    maxOf(60_000.0, Duration.between(pausedAt, scheduledEndAt).toMillis().toDouble())
                } ?: (cur.durationMinutes * 60_000.0) // Defensive fallback mirroring the source; not actually reachable in practice.
                val resumedCur = cur.copy(
                    scheduledEndAt = now.plusMillis(remainingMs.toLong()),
                    notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList(),
                )
                updated = updated.replacingStep(resumedCur)
            }
        }

        return updated.copy(status = BakeStatus.ACTIVE, pausedAt = null, pausedDurationMs = totalPausedMs)
    }

    // MARK: - abandonBake

    /** Removes a session by id from a list — see this object's own doc comment for why this one function keeps the source's array-based shape rather than taking a single session. */
    fun abandonBake(sessions: List<BakeSession>, sessionId: String): List<BakeSession> =
        sessions.filter { it.id != sessionId }

    // MARK: - startStepTimer

    /**
     * For `manualStart` steps: begins the countdown on user tap. No-op
     * if the session isn't active, there's no current step, the step is
     * `noTimer` (no countdown exists at all), or it already has a
     * `scheduledEndAt` (already running — don't restart it).
     */
    fun startStepTimer(session: BakeSession, now: Instant = Instant.now()): BakeSession {
        if (session.status != BakeStatus.ACTIVE || session.currentStepIndex >= session.steps.size) return session
        val cur = session.orderedSteps[session.currentStepIndex]
        if (cur.noTimer || cur.scheduledEndAt != null) return session

        val updatedCur = cur.copy(
            scheduledEndAt = now.plus(Duration.ofMinutes(cur.durationMinutes.toLong())),
            coilFoldNotifIds = emptyList(),
            // notificationId/prepNotifId deliberately left untouched
            // here, matching the source exactly.
        )
        return session.replacingStep(updatedCur)
    }

    // MARK: - extendStep

    /**
     * Adds [extraMinutes] to the current step's duration, extending
     * from its EXISTING `scheduledEndAt` — not restarting the clock
     * from `now`. No-op if the session isn't active, there's no current
     * step, it's `noTimer`, or it hasn't started yet (null `scheduledEndAt`).
     */
    fun extendStep(session: BakeSession, extraMinutes: Int, now: Instant = Instant.now()): BakeSession {
        if (session.status != BakeStatus.ACTIVE || session.currentStepIndex >= session.steps.size) return session
        val cur = session.orderedSteps[session.currentStepIndex]
        if (cur.noTimer) return session
        val scheduledEndAt = cur.scheduledEndAt ?: return session

        val updatedCur = cur.copy(
            durationMinutes = cur.durationMinutes + extraMinutes,
            scheduledEndAt = scheduledEndAt.plus(Duration.ofMinutes(extraMinutes.toLong())),
            notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList(),
        )
        return session.replacingStep(updatedCur)
    }

    // MARK: - reconcile (wall-clock catch-up)

    /**
     * Auto-advances a session past its current step if that step's
     * scheduled end time has already passed — run on load and whenever
     * the app returns to foreground, to catch up on time that passed
     * while the app wasn't running. No-op if the session isn't active.
     *
     * **Two real bugs found in the iOS source, both already fixed
     * there, reproduced here in their fixed form (not the original
     * buggy one)**:
     *
     * 1. **Couldn't cascade through multiple elapsed steps in one
     *    call.** A naive version assigns each newly-activated step's
     *    `scheduledEndAt` as `now + durationMinutes` — always in the
     *    future relative to `now` by construction, so a `while` loop's
     *    own condition on the next pass always fails immediately, no
     *    matter how long the app was backgrounded. Fixed by chaining
     *    the next step's `scheduledEndAt` off the step that just
     *    "ended" (`scheduledEndAt.plus(...)`) instead of off `now` —
     *    reconstructing what the schedule would have looked like had
     *    the app been watching in real time the whole way through, so a
     *    next step that's ALSO already fully elapsed keeps a
     *    `scheduledEndAt <= now` and the loop correctly keeps
     *    cascading. A step that's only PARTIALLY elapsed by `now` still
     *    stops the loop there (its chained `scheduledEndAt` is in the
     *    future) — but with an accurate remaining time reduced by
     *    however much backgrounded time already ran into it, instead of
     *    a "bonus" fresh full-duration countdown restarting from the
     *    reopen moment.
     * 2. **A `manualStart` step reached via reconciliation got silently
     *    auto-started.** A naive version checks ONLY `noTimer` before
     *    assigning a fresh `scheduledEndAt` when activating the next
     *    step — unlike the identical-looking "activate next step" logic
     *    in [startBake] and [advanceStep], both of which correctly
     *    check `noTimer || manualStart`. Fixed by using the same
     *    `nextHasAutoTimer` check [advanceStep] already uses, so a
     *    `manualStart` step becoming "next" via wall-clock catch-up now
     *    correctly gets a null `scheduledEndAt` too — waiting for an
     *    explicit [startStepTimer] call, same as reaching it via a live
     *    [advanceStep].
     *
     * Stops if the newly-activated step has a null `scheduledEndAt` — a
     * `noTimer` step, or (after fix #2) a not-yet-started `manualStart`
     * step — since neither can be auto-advanced by wall clock alone;
     * both require explicit user action instead.
     *
     * Unlike [advanceStep] (a real-time user action, stamped with
     * `now`), completed steps here are stamped with their OWN
     * `scheduledEndAt` as `actualEndAt` — the app has no way to know
     * precisely when the step actually finished while it wasn't
     * running, only that its timer target already passed, so the
     * scheduled time is the best available approximation.
     */
    fun reconcile(session: BakeSession, now: Instant = Instant.now()): ReconcileResult {
        if (session.status != BakeStatus.ACTIVE) return ReconcileResult(session, false)

        var current = session
        var changed = false

        while (current.currentStepIndex < current.steps.size) {
            val cur = current.orderedSteps[current.currentStepIndex]
            val scheduledEndAt = cur.scheduledEndAt ?: break
            if (scheduledEndAt.isAfter(now)) break

            changed = true
            val completedCur = cur.copy(status = StepStatus.COMPLETED, actualEndAt = scheduledEndAt, notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList())
            current = current.replacingStep(completedCur)

            val nextIndex = current.currentStepIndex + 1
            if (nextIndex >= current.steps.size) {
                current = current.copy(status = BakeStatus.COMPLETED, ovenPreheatNotifId = null)
                return ReconcileResult(current, true)
            }

            val next = current.orderedSteps[nextIndex]
            val nextHasAutoTimer = !(next.noTimer || next.manualStart)
            val activatedNext = next.copy(
                status = StepStatus.ACTIVE,
                // Chained off the step that just ended, not off `now`
                // — see this function's own doc comment (finding #1).
                scheduledEndAt = if (nextHasAutoTimer) scheduledEndAt.plus(Duration.ofMinutes(next.durationMinutes.toLong())) else null,
                notificationId = null, prepNotifId = null, coilFoldNotifIds = emptyList(),
            )
            current = current.replacingStep(activatedNext).copy(currentStepIndex = nextIndex)
        }

        return ReconcileResult(current, changed)
    }

    // MARK: - Oven preheat fire-time decision (pure; scheduling itself deferred)

    /**
     * Mirrors the source's `/\bBake\b|Score &? Load/i` regex exactly,
     * including a literal quirk: when no "&" is present, this actually
     * requires TWO adjacent spaces ("Score  Load"), not one, since
     * `"Score " + "&?" + " Load"` collapses to `"Score  Load"` when the
     * optional "&" doesn't match. A real step is always literally named
     * "Score & Load" (see [com.BreadIQ.myapp.model.BakeStepContent.stepComplete]),
     * which matches correctly via the "&" branch — so this quirk has no
     * practical effect on any real step label. Ported literally rather
     * than "fixed."
     */
    private fun isOvenStep(label: String): Boolean =
        Regex("""\bBake\b|Score &? Load""", RegexOption.IGNORE_CASE).containsMatchIn(label)

    /**
     * When (if at all) to fire the "preheat your oven" notification —
     * 45 minutes before the first step matching [isOvenStep], only if
     * that leaves at least 15 minutes of lead time from `now`. Returns
     * null if there's no such step, it's the very first step (no time
     * to preheat before it), or `ovenTempF <= 0`.
     */
    fun ovenPreheatFireTime(steps: List<BakeStep>, ovenTempF: Double, now: Instant): Instant? {
        val ovenStepIndex = steps.indexOfFirst { isOvenStep(it.label) }
        if (ovenStepIndex <= 0) return null
        if (ovenTempF <= 0) return null

        val priorMinutes = steps.subList(0, ovenStepIndex).sumOf { it.durationMinutes }
        val projectedOvenStart = now.plus(Duration.ofMinutes(priorMinutes.toLong()))
        val preheatFireAt = projectedOvenStart.minus(Duration.ofMinutes(45))

        return if (preheatFireAt.isAfter(now.plus(Duration.ofMinutes(15)))) preheatFireAt else null
    }

    // MARK: - Coil-fold fire-times decision (pure; scheduling itself deferred)

    /** Whether a step should get mid-bulk coil-fold reminders at all — only "Bulk Fermentation" steps at least 60 minutes long, for styles that genuinely prescribe coil folds (artisan, ciabatta). */
    fun wantsCoilFolds(stepLabel: String, durationMinutes: Int, style: String): Boolean =
        stepLabel == "Bulk Fermentation" && durationMinutes >= COIL_FOLD_BULK_MIN_MINUTES && coilFoldStyles.contains(style)

    /**
     * The three coil-fold fire times for a qualifying step, evenly
     * spaced at 1/4, 1/2, and 3/4 of the bulk duration — same formula
     * for SpeedRun and standard bakes, since scaling off the actual
     * (possibly compressed) bulk duration is exactly the point. Each
     * entry is `null` if its computed time doesn't leave at least 60
     * seconds of lead time from `now` — the list always has exactly 3
     * elements, preserving position even when an entry is skipped,
     * mirroring the source's own `coilFoldNotifIds: (string | null)[]` shape.
     */
    fun coilFoldFireTimes(stepScheduledEndAt: Instant, durationMinutes: Int, now: Instant): List<Instant?> {
        val stepStart = stepScheduledEndAt.minus(Duration.ofMinutes(durationMinutes.toLong()))
        val quarterSeconds = (durationMinutes / 4.0) * 60
        val offsets = listOf(quarterSeconds, quarterSeconds * 2, quarterSeconds * 3)
        return offsets.map { offset ->
            val fireAt = stepStart.plusSeconds(offset.toLong())
            if (fireAt.isAfter(now.plusSeconds(60))) fireAt else null
        }
    }
}
