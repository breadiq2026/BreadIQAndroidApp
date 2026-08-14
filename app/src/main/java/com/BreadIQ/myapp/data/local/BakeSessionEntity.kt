package com.BreadIQ.myapp.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeStep
import com.BreadIQ.myapp.model.StepStatus
import java.time.Instant

/**
 * Room persistence for [BakeSession] — replaces SwiftData's `@Model
 * final class BakeSession` (`Models/BakeSession.swift`).
 *
 * **Entity-plus-mapper, not entity-IS-the-domain-model** — the choice
 * this file (and every other entity in this package) makes, for a
 * structural reason more than a style preference: [BakeSession] is a
 * nested value type (`steps: List<BakeStep>` embedded directly on it),
 * matching how SwiftData exposes a `@Relationship` to-many as a plain
 * array property on the parent object. Room has no equivalent — a Room
 * `@Entity` can only hold scalar/converted columns, never an embedded
 * list of other entities; a one-to-many is always a separate table
 * joined at query time. So `BakeSessionEntity` below simply cannot have
 * a `steps` field the way the domain model does, and the mapping
 * direction only exists by combining a `BakeSessionEntity` with its
 * separately-queried `BakeStepEntity` rows (see [BakeSessionWithSteps]
 * and [toDomain] below). Once that split is already forced for the
 * relationship-holding entities, every entity in this package uses the
 * same entity/domain-model split for consistency, even the ones with no
 * relationship at all ([RecipeEntity], [IngredientPriceOverrideEntity]) —
 * one pattern to remember across the persistence layer, not two.
 */
@Entity(tableName = "bake_sessions")
data class BakeSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val style: String,
    val isSpeedRun: Boolean,
    val startedAt: Instant,
    val currentStepIndex: Int,
    val status: BakeStatus,
    val pausedAt: Instant?,
    val pausedDurationMs: Double,
    val ovenTempF: Double,
    val ovenPreheatNotifId: String?,
)

/**
 * Room persistence for [BakeStep] — replaces SwiftData's `@Model final
 * class BakeStep` (`Models/BakeStep.swift`).
 *
 * `sessionId` is the foreign key SwiftData's own `@Relationship(inverse:
 * \BakeStep.session)` implies but never stores as an app-visible column
 * (SwiftData manages that plumbing internally) — Room requires it
 * explicit. `onDelete = CASCADE` replicates
 * `@Relationship(deleteRule: .cascade, ...)` on `BakeSession.steps`:
 * deleting a `BakeSessionEntity` row deletes its `BakeStepEntity` rows
 * with it, enforced by SQLite itself (Room turns on `PRAGMA
 * foreign_keys` by default), not by application code remembering to
 * delete both.
 */
@Entity(
    tableName = "bake_steps",
    foreignKeys = [
        ForeignKey(
            entity = BakeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class BakeStepEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val label: String,
    val description: String,
    val durationMinutes: Int,
    val order: Int,
    val noTimer: Boolean,
    val manualStart: Boolean,
    val scheduledEndAt: Instant?,
    val actualEndAt: Instant?,
    val status: StepStatus,
    val notificationId: String?,
    val prepNotifId: String?,
    val coilFoldNotifIds: List<String>?,
)

/**
 * Room `@Relation` query result — a session joined with its steps. The
 * read-side counterpart to [BakeSessionEntity]/[BakeStepEntity]'s
 * separate tables; not itself a table.
 *
 * Room makes no ordering guarantee on a `@Relation` list (same
 * underlying issue [BakeStep.order]'s own doc comment already flags for
 * SwiftData's `@Relationship` arrays — a to-many join doesn't preserve
 * insertion order once round-tripped through a real store, Room's
 * relational one no more than SwiftData's graph-based one). [toDomain]
 * below sorts by `order` explicitly before handing back a [BakeSession],
 * so every consumer of the mapped domain object gets correctly-ordered
 * steps unconditionally — [BakeSession.orderedSteps] re-sorting the
 * result is then a harmless no-op, not a required extra step.
 */
data class BakeSessionWithSteps(
    @Embedded val session: BakeSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val steps: List<BakeStepEntity>,
)

fun BakeStepEntity.toDomain(): BakeStep = BakeStep(
    id = id,
    label = label,
    description = description,
    durationMinutes = durationMinutes,
    order = order,
    noTimer = noTimer,
    manualStart = manualStart,
    scheduledEndAt = scheduledEndAt,
    actualEndAt = actualEndAt,
    status = status,
    notificationId = notificationId,
    prepNotifId = prepNotifId,
    coilFoldNotifIds = coilFoldNotifIds,
)

fun BakeStep.toEntity(sessionId: String): BakeStepEntity = BakeStepEntity(
    id = id,
    sessionId = sessionId,
    label = label,
    description = description,
    durationMinutes = durationMinutes,
    order = order,
    noTimer = noTimer,
    manualStart = manualStart,
    scheduledEndAt = scheduledEndAt,
    actualEndAt = actualEndAt,
    status = status,
    notificationId = notificationId,
    prepNotifId = prepNotifId,
    coilFoldNotifIds = coilFoldNotifIds,
)

fun BakeSessionWithSteps.toDomain(): BakeSession = BakeSession(
    id = session.id,
    name = session.name,
    style = session.style,
    isSpeedRun = session.isSpeedRun,
    startedAt = session.startedAt,
    currentStepIndex = session.currentStepIndex,
    status = session.status,
    pausedAt = session.pausedAt,
    pausedDurationMs = session.pausedDurationMs,
    ovenTempF = session.ovenTempF,
    ovenPreheatNotifId = session.ovenPreheatNotifId,
    steps = steps.sortedBy { it.order }.map { it.toDomain() },
)

fun BakeSession.toEntity(): BakeSessionEntity = BakeSessionEntity(
    id = id,
    name = name,
    style = style,
    isSpeedRun = isSpeedRun,
    startedAt = startedAt,
    currentStepIndex = currentStepIndex,
    status = status,
    pausedAt = pausedAt,
    pausedDurationMs = pausedDurationMs,
    ovenTempF = ovenTempF,
    ovenPreheatNotifId = ovenPreheatNotifId,
)
