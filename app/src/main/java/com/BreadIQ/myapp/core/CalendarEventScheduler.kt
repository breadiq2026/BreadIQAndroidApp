package com.BreadIQ.myapp.core

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** `CalendarEventError` — the two user-facing failure shapes [CalendarEventScheduler.addBakeEvent] can return. */
sealed class CalendarEventFailure(val message: String) {
    data object AccessDenied : CalendarEventFailure(
        "BreadIQ needs calendar access to add this bake. Enable it in Settings → BreadIQ → Calendars.",
    )
    data object NoCalendarAvailable : CalendarEventFailure(
        "Couldn't add this bake to your calendar. No calendar is available to add it to.",
    )
    data object SaveFailed : CalendarEventFailure("Couldn't add this bake to your calendar. Try again.")
}

/** `Result<String, CalendarEventError>` — a sealed class stands in for Swift's `Result`, same convention as [BakeStartResult]/[ScheduleBakeResult]. */
sealed class CalendarEventResult {
    data class Success(val eventId: String) : CalendarEventResult()
    data class Failure(val failure: CalendarEventFailure) : CalendarEventResult()
}

/**
 * Ported from the iOS app's `Core/CalendarEventScheduler.swift` — real
 * `CalendarContract`/`ContentResolver` event creation, the Android
 * analog of `EKEventStore`/`EKEvent`. Per that file's own doc comment
 * (worth repeating here): the original RN source never created a real
 * calendar event at all, just deep-linked into Calendar.app via a
 * `calshow:` URL; the iOS port's own real `EventKit` upgrade is what
 * this file ports, per the same direct instruction that authorized that
 * upgrade in the first place.
 *
 * **Holds a plain application [Context] field (`init`, called from
 * `BreadIQApplication.onCreate()`), the same shape [BakeNotificationScheduler]
 * already established** — this object is reached from two different call
 * sites (`ScheduleViewModel.addToCalendar`, `CurrentBakeViewModel.removeScheduled`),
 * neither of which otherwise carries a `Context`, and unlike a plain
 * user-tap-triggered haptic, calendar writes are async I/O worth
 * dispatching off the caller's own scope rather than firing directly
 * from a Composable.
 *
 * **The runtime `READ_CALENDAR`/`WRITE_CALENDAR` permission dialog is
 * requested from the Compose call site (`ScheduleScreen.kt`'s "Add to
 * Calendar" button), not from here** — same Context-boundary reasoning
 * as `BakeNotificationScheduler`'s own `POST_NOTIFICATIONS` request:
 * Android's permission dialog needs a live Activity's
 * `ActivityResultLauncher`, which this object doesn't have. Unlike
 * `POST_NOTIFICATIONS`, this permission is requested lazily, right at
 * the moment the user taps "Add to Calendar" — matching the source's
 * own lazy `requestAccess()` call inside `addBakeEvent` far more closely
 * than the notification session could manage, since this one has a
 * single, well-defined interactive call site to request from instead of
 * needing to fire at app launch for many possible later call sites.
 * [requestAccess] itself is just the permission *check* both here and
 * at that Compose call site share — matching the source's own shape
 * (`addBakeEvent`/`removeBakeEvent` both guard on it internally too),
 * just unable to be the thing that pops the system dialog on Android.
 */
object CalendarEventScheduler {

    private lateinit var appContext: Context

    /** Called once from `BreadIQApplication.onCreate()`, alongside `BakeNotificationScheduler.init`. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * `EKEventStore.requestFullAccessToEvents()`'s Android counterpart —
     * a permission *check*, not a request (see this object's own doc
     * comment for why the actual system dialog is triggered from
     * `ScheduleScreen.kt` instead). `addBakeEvent`/`removeBakeEvent` both
     * guard on this internally too, matching the source's own
     * `guard await requestAccess() else { ... }` shape at both call
     * sites, even though the interactive ask already happened upstream
     * for `addBakeEvent`'s case.
     */
    fun requestAccess(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Creates a real `CalendarContract.Events` row spanning
     * [startDate]..[endDate] (floored to at least 60 seconds, matching
     * the source's own `max(endDate, startDate.addingTimeInterval(60))`)
     * against the device's default calendar. `ContentResolver` calls are
     * blocking I/O — run on [Dispatchers.IO], the Android counterpart of
     * the source's own `async`/`await` EventKit calls.
     */
    suspend fun addBakeEvent(title: String, startDate: Instant, endDate: Instant): CalendarEventResult = withContext(Dispatchers.IO) {
        if (!requestAccess()) return@withContext CalendarEventResult.Failure(CalendarEventFailure.AccessDenied)

        val calendarId = defaultCalendarId() ?: return@withContext CalendarEventResult.Failure(CalendarEventFailure.NoCalendarAvailable)
        val floorEndDate = maxOf(endDate, startDate.plusSeconds(60))

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startDate.toEpochMilli())
            put(CalendarContract.Events.DTEND, floorEndDate.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        }

        try {
            val uri = appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val identifier = uri?.lastPathSegment
            if (identifier != null) CalendarEventResult.Success(identifier) else CalendarEventResult.Failure(CalendarEventFailure.SaveFailed)
        } catch (e: Exception) {
            CalendarEventResult.Failure(CalendarEventFailure.SaveFailed)
        }
    }

    /**
     * Best-effort cleanup — silently no-ops if access isn't currently
     * granted or the event's already gone (e.g. the user deleted it
     * manually from their calendar app), matching [addBakeEvent]'s own
     * "never crash/surface an error to the caller" posture for removal.
     */
    suspend fun removeBakeEvent(identifier: String) {
        withContext(Dispatchers.IO) {
            if (!requestAccess()) return@withContext
            val id = identifier.toLongOrNull() ?: return@withContext
            try {
                appContext.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            } catch (e: Exception) {
                // Best-effort — swallow, matching the source's `try? store.remove(...)`.
            }
        }
    }

    /**
     * `store.defaultCalendarForNewEvents ?? store.calendars(for: .event).first`
     * — prefers the device's primary calendar, falling back to the first
     * calendar that actually allows adding events to it (skips read-only
     * subscribed calendars, e.g. holiday calendars, which
     * `CALENDAR_ACCESS_LEVEL` reports below `CAL_ACCESS_CONTRIBUTOR`).
     * `null` if no writable calendar exists at all.
     */
    private fun defaultCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        appContext.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val primaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            var fallbackId: Long? = null
            while (cursor.moveToNext()) {
                if (cursor.getInt(accessIndex) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                val id = cursor.getLong(idIndex)
                if (fallbackId == null) fallbackId = id
                if (primaryIndex >= 0 && cursor.getInt(primaryIndex) != 0) return id
            }
            return fallbackId
        }
        return null
    }
}
