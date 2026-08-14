package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStep

/**
 * A snapshot of a [BakeStep]'s notification ids, captured BEFORE a
 * [BakeSessionEngine] action runs — every engine action already resets
 * these fields to `null`/empty as part of its own state transition, so
 * by the time a caller can react to the mutated session, the OLD ids
 * are already gone. Callers capture this snapshot first, then would
 * cancel it after calling the engine — see [BakeNotificationScheduler]'s
 * own doc comment for why "would" rather than "does" this session.
 *
 * Pure data capture, no notification API involved — kept as a real,
 * working function ([BakeNotificationScheduler.snapshot]) rather than a
 * stub, unlike everything else in this file.
 */
data class BakeStepNotificationSnapshot(
    val notificationId: String? = null,
    val prepNotifId: String? = null,
    val coilFoldNotifIds: List<String>? = null,
)

/**
 * Stub for the iOS app's `Core/BakeNotificationScheduler.swift` — the
 * real `UNUserNotificationCenter` implementation behind the scheduling
 * boundary `BakeSessionEngine`/`ScheduledBakePlanner` deliberately left
 * unimplemented (see those files' own "Scope boundary, deliberate" doc
 * comments). Push notifications are their own later porting item (needs
 * `WorkManager` + notification channels — see PORTING_PLAN.md's
 * platform-integrations step), not this session's scope.
 *
 * **Every call site across Queue/Current Bake/Bake Detail is preserved,
 * not removed** — per direct instruction, the state mutation/navigation
 * logic surrounding each of these calls is real scope; only the actual
 * scheduling work these functions would otherwise do is stubbed to a
 * no-op. When real scheduling lands, these four functions are the only
 * things that need real bodies — no call site anywhere else in the app
 * should need to change.
 */
object BakeNotificationScheduler {

    /** Pure — see [BakeStepNotificationSnapshot]'s own doc comment. */
    fun snapshot(step: BakeStep): BakeStepNotificationSnapshot =
        BakeStepNotificationSnapshot(step.notificationId, step.prepNotifId, step.coilFoldNotifIds)

    /** Stub — would schedule the step-complete/prep/coil-fold/oven-preheat notifications for a freshly-started session. */
    fun afterStart(session: BakeSession) {
        // Intentionally empty — see this object's own doc comment.
    }

    /** Stub — would cancel a single scheduled local notification by id. */
    fun cancel(id: String?) {
        // Intentionally empty — see this object's own doc comment.
    }

    /** Stub — would cancel [previousStep]'s (and, if changed, [previousOvenPreheatId]'s) stale notifications, then schedule fresh ones for [session]'s new current step. The shape every pause/resume/advance/extend/start-timer call site needs. */
    fun syncAfterMutation(session: BakeSession, previousStep: BakeStepNotificationSnapshot, previousOvenPreheatId: String?) {
        // Intentionally empty — see this object's own doc comment.
    }

    /** Stub — would cancel every notification tied to [session] (abandon/complete). */
    fun cancelEverything(session: BakeSession) {
        // Intentionally empty — see this object's own doc comment.
    }
}
