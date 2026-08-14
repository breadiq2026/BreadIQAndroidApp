package com.BreadIQ.myapp.model

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Ported from the iOS app's `Models/QueuedBake.swift`.
 *
 * A single step's static plan info within a queued (not-yet-started) bake —
 * just label/description/duration, no live timer/status fields (those only
 * exist once a bake is promoted to a running [BakeSession]).
 *
 * `@Serializable` — see [FlourBlendEntry]'s own doc comment; same reason
 * (Room's `QueuedBakeEntity` TypeConverter JSON-encodes the plan list into
 * one column).
 */
@Serializable
data class QueuedBakeStepPlan(
    val label: String,
    val description: String,
    val durationMinutes: Int,
)

/**
 * A bake the user has planned but not yet started — a lightweight backlog
 * entry distinct from a live [BakeSession] or a time-scheduled
 * [ScheduledBake]. Originally ported from `types/bake.ts`'s `QueuedBake` in
 * the source Expo app.
 */
data class QueuedBake(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val style: String,
    val ovenTempF: Double,
    val createdAt: Instant = Instant.now(),
    val steps: List<QueuedBakeStepPlan> = emptyList(),
    val config: QueuedBakeConfig,
)
