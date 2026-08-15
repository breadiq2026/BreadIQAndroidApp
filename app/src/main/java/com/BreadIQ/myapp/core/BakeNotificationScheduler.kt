package com.BreadIQ.myapp.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.BreadIQ.myapp.data.local.BakeSessionDao
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * A snapshot of a [BakeStep]'s notification ids, captured BEFORE a
 * [BakeSessionEngine] action runs — every engine action already resets
 * these fields to `null`/empty as part of its own state transition, so
 * by the time a caller can react to the mutated session, the OLD ids
 * are already gone. Callers capture this snapshot first, then cancel it
 * after calling the engine.
 */
data class BakeStepNotificationSnapshot(
    val notificationId: String? = null,
    val prepNotifId: String? = null,
    val coilFoldNotifIds: List<String>? = null,
)

/**
 * Ported from the iOS app's `Core/BakeNotificationScheduler.swift` — the
 * real scheduling implementation behind the boundary `BakeSessionEngine`/
 * `ScheduledBakePlanner` deliberately left unimplemented (see those
 * files' own "Scope boundary, deliberate" doc comments). The two pure
 * decision pieces it already ported (`ovenPreheatFireTime`,
 * `wantsCoilFolds`/`coilFoldFireTimes`) and the copy lookups
 * ([BakeStepContentLookup]) are reused as-is, not reimplemented.
 *
 * **Every call site across Queue/Current Bake/Bake Detail/Calculator
 * still calls exactly the same four functions** (`afterStart`, `cancel`,
 * `syncAfterMutation`, `cancelEverything`) with the same signatures as
 * the stub this replaces — none of them needed to change.
 *
 * **`AlarmManager` + a `BroadcastReceiver`, not `WorkManager`.** The
 * source deliberately upgraded from a relative "fire in N seconds"
 * trigger to `UNCalendarNotificationTrigger` with an absolute
 * `DateComponents` time specifically to avoid OS-scheduling-delay drift
 * (see the source's own doc comment). `WorkManager`'s minimum-latency
 * model and Doze-mode batching would reintroduce exactly that drift —
 * `AlarmManager.setExactAndAllowWhileIdle` (via [BakeNotificationReceiver])
 * is the Android primitive that actually matches "an absolute, precise,
 * OS-scheduled local notification." A notification channel is still
 * needed either way — created once, in `BreadIQApplication.onCreate()`.
 *
 * **`init(context)` holds a plain application [Context] field, not
 * constructor injection** — this object is reached from four ViewModels
 * that don't otherwise carry a `Context` (only their factories do, to
 * build a [DatabaseProvider] instance — see e.g. `QueueViewModel`'s own
 * factory), the same boundary `Haptics.kt` already established for why
 * Context-needing calls get fired from the Composable layer instead. A
 * notification *scheduler*, unlike haptics, has no Composable call site
 * to fire from — so it holds an application Context itself, set once at
 * launch from `BreadIQApplication.onCreate()`, the same eager-singleton-
 * init shape [DatabaseProvider] already uses there.
 *
 * **The `POST_NOTIFICATIONS` runtime permission is requested once, early,
 * from `MainActivity`** — not lazily "right before the first schedule
 * call" the way the source's `requestAuthorization()` is, because
 * `UNUserNotificationCenter.requestAuthorization` can be invoked from
 * anywhere (no live UI needed), while Android's equivalent runtime
 * permission can only be requested through an `ActivityResultLauncher`
 * owned by a live `Activity` — which, per the same Context-boundary
 * reasoning above, none of this app's ViewModels have. Every real
 * `schedule()` call below still checks [hasNotificationPermission] first
 * and silently skips if it's not granted, matching the source's own
 * `guard await requestAuthorization() else { return }` short-circuit —
 * this object just can't be the one to *ask* for it.
 *
 * **`SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.** `USE_EXACT_ALARM`
 * is auto-granted on API 33+ but Play Store policy restricts it to
 * alarm-clock/calendar-category apps — BreadIQ doesn't qualify.
 * `SCHEDULE_EXACT_ALARM` has no in-app system dialog on API 33+ (only a
 * Settings deep link, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` — see
 * `MainActivity`'s own call site), and the grant can be revoked by the
 * user at any time afterward, so [canScheduleExactAlarms] is checked
 * fresh on every real schedule call and falls back to an inexact
 * `AlarmManager.set` rather than dropping the notification outright —
 * a graceful-degradation path the source has no equivalent for (iOS's
 * `UNCalendarNotificationTrigger` has no "not permitted" state at all).
 *
 * **Known gap, intentionally not solved this session: `AlarmManager`
 * alarms do not survive a device reboot** (unlike iOS local
 * notifications, which do). A `BOOT_COMPLETED` receiver that
 * reconstructs every active session's pending notification content/fire
 * times from Room and reschedules them would close this gap, but is
 * real additional scope — TODO for a future session, not built here.
 *
 * **`ScheduledBakePlanner`'s `startReminderNotifId`/`startTimeNotifId`
 * stay unused/`null`** — verified directly against
 * `ScheduledBakePlanner.swift`, which never actually calls its own
 * `scheduleLocalNotification` for those either; its own doc comment
 * flags this as deliberately out of scope on iOS too, not an Android
 * gap. Nothing to port here.
 *
 * **`sweepOrphanedNotifications` (the source's startup/foreground
 * orphan-cleanup sweep, run alongside `RootView`'s
 * `reconcileAllSessions`) is not ported** — its call site
 * (bake-session reconciliation at app-shell startup) doesn't exist on
 * Android yet either (see `MainActivity`'s own doc comment on the
 * narrower auth-gating step this app currently has). Port alongside
 * that reconciliation work when it lands, not before.
 */
object BakeNotificationScheduler {

    private const val TAG = "BakeNotificationSched"

    const val CHANNEL_ID = "bake_progress"
    private const val CHANNEL_NAME = "Bake Progress"
    private const val CHANNEL_DESCRIPTION = "Step timers, prep reminders, and oven preheat alerts for an active bake."

    // Extras BakeNotificationReceiver reads back out at fire time.
    const val EXTRA_NOTIF_ID = "com.BreadIQ.myapp.EXTRA_NOTIF_ID"
    const val EXTRA_TITLE = "com.BreadIQ.myapp.EXTRA_TITLE"
    const val EXTRA_BODY = "com.BreadIQ.myapp.EXTRA_BODY"
    const val EXTRA_SESSION_ID = "com.BreadIQ.myapp.EXTRA_SESSION_ID"

    private lateinit var appContext: Context
    private val bakeSessionDao: BakeSessionDao by lazy { DatabaseProvider.getInstance(appContext).bakeSessionDao() }

    /**
     * Detached background scope for the genuinely-async scheduling work
     * (real `AlarmManager`/Room calls) — the counterpart of the source's
     * own detached `Task { ... }` inside `syncAfterMutation`/`afterStart`:
     * no call site (a ViewModel button handler) should block on this.
     */
    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Called once from `BreadIQApplication.onCreate()` — see this object's own doc comment. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Called once from `BreadIQApplication.onCreate()`, alongside [init] — must exist before the first real notification post (required on API 26+, which this app's `minSdk` already assumes unconditionally). */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CHANNEL_DESCRIPTION
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // MARK: - Low-level primitives (lib/notifications.ts)

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** See this object's own doc comment on why `SCHEDULE_EXACT_ALARM`/`canScheduleExactAlarms` rather than `USE_EXACT_ALARM`. Every app could always schedule exact alarms before API 31 — nothing to check there. */
    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * `scheduleLocalNotification()`. Returns `null` on failure (no
     * notification permission, or the OS refused the alarm), same
     * "never throws to the caller" shape as the source — but logs the
     * actual failure, matching the source's own upgrade from silently
     * swallowing it. `kind` identifies which of the four notification
     * types failed (step-complete/prep/coil-fold/oven-preheat), for the
     * log line only.
     */
    private fun schedule(kind: String, title: String, body: String, fireAt: Instant, sessionId: String, now: Instant): String? {
        if (!hasNotificationPermission()) return null

        val floorFireAt = maxOf(fireAt, now.plusSeconds(2))
        val identifier = UUID.randomUUID().toString()
        val requestCode = identifier.hashCode()

        val intent = Intent(appContext, BakeNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIF_ID, requestCode)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return try {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAtMillis = floorFireAt.toEpochMilli()
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // Exact-alarm permission not currently granted — degrade
                // to an inexact alarm rather than dropping the
                // notification outright. See this object's own doc
                // comment on why this path exists at all.
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            identifier
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule $kind notification for session $sessionId", e)
            null
        }
    }

    /**
     * `cancelScheduledNotification()`. Cancels both the pending alarm
     * (if it hasn't fired yet) and any already-shown notification (if it
     * has) — covering both sides of a fire-time race the source's
     * synchronous `removePendingNotificationRequests` never has to think
     * about, since a `UNUserNotificationCenter` pending request and an
     * already-delivered notification are cancelled through two entirely
     * separate source-level APIs there too, just both always called
     * together in practice. Synchronous — cancellation has no async form
     * on either platform.
     */
    fun cancel(id: String?) {
        if (id == null) return
        val requestCode = id.hashCode()

        val intent = Intent(appContext, BakeNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        NotificationManagerCompat.from(appContext).cancel(requestCode)
    }

    // MARK: - Step-level orchestration (scheduleStepNotifs / cancelStepNotifs)

    /** Pure — see [BakeStepNotificationSnapshot]'s own doc comment. */
    fun snapshot(step: BakeStep): BakeStepNotificationSnapshot =
        BakeStepNotificationSnapshot(step.notificationId, step.prepNotifId, step.coilFoldNotifIds)

    private fun cancelAll(snapshot: BakeStepNotificationSnapshot) {
        cancel(snapshot.notificationId)
        cancel(snapshot.prepNotifId)
        snapshot.coilFoldNotifIds?.forEach { cancel(it) }
    }

    /**
     * `scheduleStepNotifs()`. No-op unless the step actually has a
     * running timer and hasn't already been scheduled — every
     * `BakeSessionEngine` action already resets `notificationId` to
     * `null` exactly when the source would call this again (see
     * `BakeSessionEngine.startStepTimer`'s own doc comment), so
     * `notificationId == null` alone is a reliable signal here — no
     * per-call-site special-casing needed, matching the source.
     *
     * Persists the resulting ids straight onto the step's Room row
     * (`bakeSessionDao.updateStepNotificationIds`) — the Android
     * counterpart of the source mutating the SwiftData model in place
     * and then `modelContext.save()`-ing. Every ViewModel call site has
     * already persisted the rest of `session`'s steps by the time this
     * runs, so this only needs to patch the three notification-id
     * columns scheduling just produced.
     *
     * The source also takes an `isSpeedRun` parameter here that its own
     * body never actually reads — dropped rather than carried forward
     * as dead weight, since this is a private helper, not part of the
     * four-function public surface every call site depends on.
     */
    private suspend fun scheduleIfNeeded(step: BakeStep, sessionId: String, style: String, now: Instant) {
        val scheduledEndAt = step.scheduledEndAt ?: return
        if (step.notificationId != null) return
        if (!hasNotificationPermission()) return

        val completeCopy = BakeStepContentLookup.stepCompleteNotif(step.label)
        val notificationId = schedule("step-complete", completeCopy.title, completeCopy.body, scheduledEndAt, sessionId, now)

        var prepNotifId: String? = null
        if (step.durationMinutes >= 10) {
            val prepCopy = BakeStepContentLookup.stepPrepNotif(step.label, style)
            if (prepCopy != null) {
                val prepFireAt = scheduledEndAt.minusSeconds(5 * 60)
                if (prepFireAt.isAfter(now.plusSeconds(60))) {
                    prepNotifId = schedule("prep", prepCopy.title, prepCopy.body, prepFireAt, sessionId, now)
                }
            }
        }

        var coilFoldNotifIds: List<String>? = null
        if (BakeSessionEngine.wantsCoilFolds(step.label, step.durationMinutes, style)) {
            val foldCopy = BakeStepContentLookup.coilFoldNotif()
            coilFoldNotifIds = BakeSessionEngine.coilFoldFireTimes(scheduledEndAt, step.durationMinutes, now).mapNotNull { fireAt ->
                fireAt?.let { schedule("coil-fold", foldCopy.title, foldCopy.body, it, sessionId, now) }
            }
        }

        bakeSessionDao.updateStepNotificationIds(step.id, notificationId, prepNotifId, coilFoldNotifIds)
    }

    // MARK: - Oven preheat

    private suspend fun scheduleOvenPreheatIfNeeded(session: BakeSession, now: Instant) {
        val fireAt = BakeSessionEngine.ovenPreheatFireTime(session.orderedSteps, session.ovenTempF, now) ?: return
        if (!hasNotificationPermission()) return
        val copy = BakeStepContentLookup.ovenPreheatNotif(session.ovenTempF.toInt())
        val id = schedule("oven-preheat", copy.title, copy.body, fireAt, session.id, now)
        bakeSessionDao.updateOvenPreheatNotifId(session.id, id)
    }

    // MARK: - Shared call-site shapes

    /**
     * `startBake`'s own inline scheduling — no previous step to cancel,
     * the session is brand new. Runs on [schedulerScope], matching the
     * source's own detached `Task { ... }`.
     */
    fun afterStart(session: BakeSession) {
        val step = session.orderedSteps.getOrNull(session.currentStepIndex) ?: return
        val now = Instant.now()
        schedulerScope.launch {
            scheduleIfNeeded(step, session.id, session.style, now)
            scheduleOvenPreheatIfNeeded(session, now)
        }
    }

    /**
     * The shape every one of `advanceStep`/`pauseBake`/`resumeBake`/
     * `startStepTimer`/`extendStep` needs: cancel whatever the PREVIOUS
     * current step (captured before the engine call) had scheduled,
     * then — if the session is still active and its new current step
     * needs one — schedule fresh notifications for it. Cancellation runs
     * synchronously; scheduling runs on [schedulerScope].
     */
    fun syncAfterMutation(session: BakeSession, previousStep: BakeStepNotificationSnapshot, previousOvenPreheatId: String?) {
        cancelAll(previousStep)
        if (session.status == BakeStatus.COMPLETED) {
            cancel(previousOvenPreheatId)
            return
        }
        val step = session.orderedSteps.getOrNull(session.currentStepIndex) ?: return
        val now = Instant.now()
        schedulerScope.launch {
            scheduleIfNeeded(step, session.id, session.style, now)
        }
    }

    /**
     * `abandonBake`'s "cancel all pending notifications" loop — run
     * before the session is deleted, synchronously (cancellation has no
     * async form on either platform).
     */
    fun cancelEverything(session: BakeSession) {
        session.orderedSteps.forEach { cancelAll(snapshot(it)) }
        cancel(session.ovenPreheatNotifId)
    }
}
