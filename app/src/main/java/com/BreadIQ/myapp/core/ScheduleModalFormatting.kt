package com.BreadIQ.myapp.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ported from the iOS app's `Core/ScheduleModalFormatting.swift` — pure
 * formatting/decision logic for the Schedule screen, independent of the
 * view itself. The actual finish-time/start-time math lives in
 * [ScheduledBakePlanner]; this covers just the display strings specific
 * to this screen.
 *
 * **Distinct from `ScheduledBakeCard`'s own date formatting** (see
 * `ui/components/ScheduledBakeCard.kt`) — verified against both iOS
 * source files: `ScheduledBakeCard.swift` has its own "Today at
 * X"/"Tomorrow at X"/relative formatter, while this screen's
 * `formatDateTime` is a plain weekday+month+day+time string with no
 * relative-day special-casing. Genuinely two different functions in two
 * different source components, not one duplicated — kept separate here too.
 */
object ScheduleModalFormatting {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun formatDateTime(instant: Instant): String = dateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m > 0) "${h}h ${m}m" else "${h}h"
    }

    /** `handleSchedule`'s post-success confirmation body text. */
    fun scheduledConfirmationMessage(bakeName: String, startTime: Instant, targetFinishTime: Instant): String =
        "\"$bakeName\" is set. Start your bake at ${formatTime(startTime)} and your bread will be ready by ${formatTime(targetFinishTime)}.\n\nOpen Calendar at your bake start time?"
}
