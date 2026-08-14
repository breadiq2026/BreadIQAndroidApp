package com.BreadIQ.myapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.BreadIQ.myapp.model.QueuedBake
import com.BreadIQ.myapp.model.ScheduledBake
import java.time.Instant

/**
 * Room persistence for [ScheduledBake] — replaces SwiftData's `@Model
 * final class ScheduledBake` (`Models/ScheduledBake.swift`).
 *
 * Carries no foreign key to [QueuedBakeEntity] itself — see that file's
 * own doc comment on `scheduledBakeId` for why the FK lives on the owned
 * side (`QueuedBakeEntity`) instead, inverting SwiftData's relationship
 * direction for correct `ON DELETE CASCADE` semantics while preserving
 * the exact same observable delete behavior.
 *
 * Assembling a full [ScheduledBake] (which needs its owned [QueuedBake],
 * which itself needs its own [com.BreadIQ.myapp.model.QueuedBakeConfig])
 * is a 3-table join. Rather than a `@Relation` field nested inside
 * another `@Relation`-bearing POJO ([QueuedBakeWithConfig] itself already
 * being one) — a real but less common Room pattern, not worth reaching
 * for without being able to verify it end to end — `ScheduledBakeDao`
 * does this assembly as two explicit queries inside one `@Transaction`
 * instead: plainer to read, and just as correct.
 */
@Entity(tableName = "scheduled_bakes")
data class ScheduledBakeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val style: String,
    val targetFinishTime: Instant,
    val startTime: Instant,
    val totalStepMinutes: Int,
    val startReminderNotifId: String?,
    val startTimeNotifId: String?,
    val calendarEventId: String?,
    val createdAt: Instant,
)

fun ScheduledBakeEntity.toDomain(queueItem: QueuedBake): ScheduledBake = ScheduledBake(
    id = id,
    name = name,
    style = style,
    targetFinishTime = targetFinishTime,
    startTime = startTime,
    totalStepMinutes = totalStepMinutes,
    startReminderNotifId = startReminderNotifId,
    startTimeNotifId = startTimeNotifId,
    calendarEventId = calendarEventId,
    createdAt = createdAt,
    queueItem = queueItem,
)

fun ScheduledBake.toEntity(): ScheduledBakeEntity = ScheduledBakeEntity(
    id = id,
    name = name,
    style = style,
    targetFinishTime = targetFinishTime,
    startTime = startTime,
    totalStepMinutes = totalStepMinutes,
    startReminderNotifId = startReminderNotifId,
    startTimeNotifId = startTimeNotifId,
    calendarEventId = calendarEventId,
    createdAt = createdAt,
)
