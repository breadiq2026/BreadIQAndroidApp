package com.BreadIQ.myapp.model

import java.time.Instant
import java.util.UUID

/**
 * Ported from the iOS app's `Models/BakeSession.swift`.
 *
 * A live/paused/completed bake session — the step-by-step timer state that
 * drives the active-bake tracker. Originally ported from `types/bake.ts`'s
 * `BakeSession` in the source Expo app.
 */
enum class BakeStatus(val rawValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETED("completed"),
}

data class BakeSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val style: String,
    val isSpeedRun: Boolean = false,
    val startedAt: Instant = Instant.now(),
    val currentStepIndex: Int = 0,
    val status: BakeStatus = BakeStatus.ACTIVE,

    /** Set when the session enters [BakeStatus.PAUSED]; null otherwise. */
    val pausedAt: Instant? = null,
    /**
     * Total time spent paused across possibly-multiple pause/resume cycles,
     * used to offset scheduled step end times when resuming.
     */
    val pausedDurationMs: Double = 0.0,

    val ovenTempF: Double,
    /** id of the scheduled "preheat the oven" local notification. */
    val ovenPreheatNotifId: String? = null,

    val steps: List<BakeStep> = emptyList(),
) {
    /**
     * `steps`, restored to real step order — see [BakeStep.order]'s doc
     * comment for why this is kept even though a plain Kotlin `List`
     * already preserves insertion order today. Every consumer that cares
     * about step sequence (not just membership/count) should read this
     * instead of `steps` directly, for parity with the iOS port.
     */
    val orderedSteps: List<BakeStep>
        get() = steps.sortedBy { it.order }
}
