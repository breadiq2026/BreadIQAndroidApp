package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.QueuedBake
import com.BreadIQ.myapp.model.QueuedBakeConfig
import com.BreadIQ.myapp.model.QueuedBakeStepPlan
import com.BreadIQ.myapp.model.ScheduledBake
import java.time.Duration
import java.time.Instant

/**
 * Input for [ScheduledBakePlanner.scheduleBake] — mirrors the iOS
 * source's `RawScheduledBakePlan`.
 *
 * The iOS source gives this an `Identifiable` conformance with a
 * defaulted `id` specifically to fix a real SwiftUI `.sheet(item:)` vs
 * `.sheet(isPresented:)` race (a blank-sheet bug on first presentation —
 * see that struct's own doc comment for the full root-cause writeup).
 * That bug class doesn't exist in Compose Navigation (this port's own
 * Schedule route reads its plan from a plain remembered value set right
 * before navigating, not a SwiftUI `.sheet` binding two separate optional
 * states together), so no `id` field is carried over here — nothing in
 * this port ever needs to identify a plan independent of the bake it
 * describes.
 */
data class RawScheduledBakePlan(
    val name: String,
    val style: String,
    val ovenTempF: Double,
    val steps: List<QueuedBakeStepPlan>,
    val config: QueuedBakeConfig,
)

sealed class ScheduleBakeFailure {
    data object TierNotSupported : ScheduleBakeFailure()
    data class LimitReached(val max: Int) : ScheduleBakeFailure()
    data object NotEnoughLeadTime : ScheduleBakeFailure()
    data object SchedulingWindowTooFar : ScheduleBakeFailure()

    val message: String
        get() = when (this) {
            // Deliberately a DIFFERENT string from BakeStartFailure.TierNotSupported
            // ("Active Bake is a Basic or Premium feature...") — the
            // iOS source confirms these are two genuinely separate
            // user-facing messages for two separate gated features, not
            // one message reused.
            is TierNotSupported -> "Scheduling requires Basic or Premium."
            is LimitReached -> "You've reached your limit of $max active or scheduled bake${if (max == 1) "" else "s"}. Start, complete, or remove one first."
            is NotEnoughLeadTime -> "Not enough lead time. Choose a later finish time to allow for the full bake timeline."
            is SchedulingWindowTooFar -> "Maximum scheduling window is 7 days out."
        }
}

/** Kotlin counterpart of the iOS source's `Result<ScheduledBake, ScheduleBakeFailure>`. */
sealed class ScheduleBakeResult {
    data class Success(val scheduled: ScheduledBake) : ScheduleBakeResult()
    data class Failure(val failure: ScheduleBakeFailure) : ScheduleBakeResult()
}

/**
 * Ported from the iOS app's `Core/ScheduledBakePlanner.swift` — the
 * planning/validation math behind scheduling a bake for a future start
 * time, plus the default-target-time and live-validity helpers the
 * Schedule screen's date picker needs.
 *
 * **A real duplication in the iOS source, unified there rather than
 * re-duplicated — carried forward as already-unified here too**: the
 * source found its own `COOLING_MINUTES`/`startTime`/too-soon/too-far
 * formulas independently declared in two places (the authoritative
 * submit-time gate and a live UI preview), diffed byte-identical, and
 * consolidated into this one object. [isTooSoon]/[isTooFar] serve the
 * live-preview half; [scheduleBake] is the authoritative submit-time
 * gate — both built on the same [startTime], so they can't independently
 * drift.
 *
 * Notification scheduling (the two local-notification calls in the iOS
 * source's own `scheduleBake`-equivalent) stays out of scope, same
 * boundary as every other native concern this session —
 * `startReminderNotifId`/`startTimeNotifId` are left null on the
 * returned [ScheduledBake] for that future step to populate.
 */
object ScheduledBakePlanner {

    const val coolingMinutes: Int = 60
    const val leadTimeMinutes: Int = 5
    const val maxSchedulingWindowDays: Int = 7

    /**
     * `MAX_SCHEDULED` — same values as [BakeSessionEngine.maxActiveBakes]
     * (0/1/3) but a philosophically distinct constant in the source,
     * kept as its own table here rather than reused, matching that
     * distinction.
     */
    val maxScheduledBakes: Map<BakeUserTier, Int> = mapOf(BakeUserTier.FREE to 0, BakeUserTier.BASIC to 1, BakeUserTier.PREMIUM to 3)

    // MARK: - Core formula (used by both the live preview and the authoritative gate)

    fun startTime(targetFinishTime: Instant, totalStepMinutes: Int): Instant =
        targetFinishTime.minus(Duration.ofMinutes((totalStepMinutes + coolingMinutes).toLong()))

    /** A sensible default finish time to pre-populate the picker with — bake time + cooling + an extra hour of buffer. */
    fun defaultTargetFinishTime(totalStepMinutes: Int, now: Instant = Instant.now()): Instant =
        now.plus(Duration.ofMinutes((totalStepMinutes + coolingMinutes + 60).toLong()))

    fun isTooSoon(targetFinishTime: Instant, totalStepMinutes: Int, now: Instant): Boolean =
        !startTime(targetFinishTime, totalStepMinutes).isAfter(now.plus(Duration.ofMinutes(leadTimeMinutes.toLong())))

    fun isTooFar(targetFinishTime: Instant, now: Instant): Boolean =
        targetFinishTime.isAfter(now.plus(Duration.ofDays(maxSchedulingWindowDays.toLong())))

    // MARK: - scheduleBake (addScheduledBake)

    fun scheduleBake(
        plan: RawScheduledBakePlan,
        targetFinishTime: Instant,
        activeBakesCount: Int,
        existingScheduledCount: Int,
        tier: BakeUserTier,
        now: Instant = Instant.now(),
    ): ScheduleBakeResult {
        if (tier == BakeUserTier.FREE) return ScheduleBakeResult.Failure(ScheduleBakeFailure.TierNotSupported)

        val max = maxScheduledBakes[tier] ?: 0
        if (existingScheduledCount + activeBakesCount >= max) {
            return ScheduleBakeResult.Failure(ScheduleBakeFailure.LimitReached(max))
        }

        val totalStepMinutes = plan.steps.sumOf { it.durationMinutes }

        if (isTooSoon(targetFinishTime, totalStepMinutes, now)) return ScheduleBakeResult.Failure(ScheduleBakeFailure.NotEnoughLeadTime)
        if (isTooFar(targetFinishTime, now)) return ScheduleBakeResult.Failure(ScheduleBakeFailure.SchedulingWindowTooFar)

        val start = startTime(targetFinishTime, totalStepMinutes)

        val queueItem = QueuedBake(
            name = plan.name,
            style = plan.style,
            ovenTempF = plan.ovenTempF,
            createdAt = now,
            steps = plan.steps,
            // A fresh copy is implicit here — `plan.config` is a plain
            // Kotlin `data class` value, not the shared-reference
            // SwiftData object the iOS source's own `.copy()` call
            // defensively guards against (see `QueuedBakeConfig`'s own
            // doc comment for that real, confirmed-in-production crash
            // history). No separate `.copy()` call is needed here for
            // the same reason it's a no-op safety net on the iOS side
            // now too — just belt-and-suspenders there, structurally
            // unnecessary here.
            config = plan.config,
        )

        val scheduled = ScheduledBake(
            name = plan.name,
            style = plan.style,
            targetFinishTime = targetFinishTime,
            startTime = start,
            totalStepMinutes = totalStepMinutes,
            createdAt = now,
            queueItem = queueItem,
        )
        return ScheduleBakeResult.Success(scheduled)
    }

    // MARK: - removeScheduledBake

    fun removeScheduledBake(scheduledBakes: List<ScheduledBake>, id: String): List<ScheduledBake> =
        scheduledBakes.filter { it.id != id }
}
