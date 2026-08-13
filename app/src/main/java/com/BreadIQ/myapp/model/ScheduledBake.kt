package com.BreadIQ.myapp.model

import java.time.Instant
import java.util.UUID

/**
 * Ported from the iOS app's `Models/ScheduledBake.swift`.
 *
 * A bake the user has scheduled to start at a specific future time —
 * distinct from a live [BakeSession] or a not-yet-scheduled [QueuedBake].
 * Originally ported from `types/scheduled.ts`'s `ScheduledBake` in the
 * source Expo app.
 */
data class ScheduledBake(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val style: String,
    val targetFinishTime: Instant,
    val startTime: Instant,
    val totalStepMinutes: Int,

    /**
     * The source Expo app models this as a fixed 2-tuple
     * `[string|null, string|null]`; the iOS port splits it into two named
     * fields since the two slots have distinct meanings (not an
     * interchangeable pair) and Swift doesn't support optional tuples as
     * stored properties. Kept the same way here.
     */
    val startReminderNotifId: String? = null,
    val startTimeNotifId: String? = null,

    /**
     * Always null in the source app today — its calendar integration only
     * deep-links into the platform calendar via a URL scheme; it never
     * actually creates a real calendar event. Kept as a placeholder for a
     * real `CalendarContract` event id if a later phase upgrades to
     * genuine calendar-event creation (see PORTING_PLAN.md step 8).
     */
    val calendarEventId: String? = null,

    val createdAt: Instant = Instant.now(),

    /**
     * Owned snapshot, not a reference to a Queue-tab entry — the source
     * app builds a fresh `QueuedBake` (its own id/createdAt) when a bake
     * is scheduled, so this should cascade on delete like any other owned
     * child once real persistence (Room) is designed.
     */
    val queueItem: QueuedBake,
)
