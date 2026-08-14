package com.BreadIQ.myapp.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.QueuedBake
import com.BreadIQ.myapp.model.QueuedBakeConfig
import com.BreadIQ.myapp.model.QueuedBakeStepPlan
import java.time.Instant

/**
 * Room persistence for [QueuedBake] — replaces SwiftData's `@Model final
 * class QueuedBake` (`Models/QueuedBake.swift`).
 *
 * See [BakeSessionEntity]'s own doc comment for why this package uses an
 * entity-plus-mapper split throughout rather than annotating the domain
 * models directly.
 *
 * **`scheduledBakeId` inverts SwiftData's own relationship direction —
 * deliberately, for correct SQL cascade semantics, not a mistake.**
 * `ScheduledBake.swift` declares `@Relationship(deleteRule: .cascade)
 * var queueItem: QueuedBake` — i.e. *the parent (`ScheduledBake`) holds a
 * reference to the child it owns (`QueuedBake`)*, and deleting the
 * parent should cascade-delete that child. Standard SQL `ON DELETE
 * CASCADE` only fires in the other direction: deleting the *referenced*
 * row cascades to the row holding the foreign key, never the reverse.
 * Modeling this literally (an FK column on `ScheduledBakeEntity`
 * pointing at the `QueuedBakeEntity` it owns) would mean deleting the
 * `ScheduledBakeEntity` does NOT delete the `QueuedBakeEntity` it
 * pointed to — SQLite would just leave that row behind, or null out the
 * column if nullable. So the FK lives here instead, matching the
 * direction every other cascade in this schema already uses
 * (`BakeStepEntity.sessionId`, `QueuedBakeConfigEntity.queuedBakeId`):
 * the *owned* row carries the FK to its *owner*, and `ON DELETE CASCADE`
 * fires when the owner is deleted. The observable behavior — deleting a
 * scheduled bake also deletes the queued-bake snapshot it owns — is
 * identical either way; only the internal column placement differs.
 *
 * `null` for every Queue-tab entry the user created directly (the
 * ordinary case, unowned). Non-null only for a [ScheduledBake]'s owned
 * snapshot — see that entity's own doc comment for why a schedule builds
 * a fresh `QueuedBake` rather than referencing an existing Queue-tab row.
 */
@Entity(
    tableName = "queued_bakes",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledBakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledBakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduledBakeId")],
)
data class QueuedBakeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val style: String,
    val ovenTempF: Double,
    val createdAt: Instant,
    val steps: List<QueuedBakeStepPlan>,
    val scheduledBakeId: String? = null,
)

/**
 * Room persistence for [QueuedBakeConfig] — replaces SwiftData's `@Model
 * final class QueuedBakeConfig` (`Models/QueuedBakeConfig.swift`).
 *
 * **`queuedBakeId` is both the primary key and the foreign key** —
 * deliberate, not an oversight: the source relationship is genuinely
 * 1-to-1 (`QueuedBake.config: QueuedBakeConfig`, non-optional, and the
 * inverse is a single `QueuedBakeConfig.queuedBake: QueuedBake?`, never
 * a collection). Using the parent's own id as this table's primary key
 * makes "more than one config per queued bake" structurally
 * unrepresentable, rather than merely unintended, and avoids a
 * meaningless surrogate key for a row that never has an independent
 * identity of its own — matching SwiftData's own note that this class
 * exists as a full `@Model` mainly to mirror `types/bake.ts`'s shape,
 * not because it needed independent identity (see that file's own
 * `copy()` doc comment for the aliasing-bug history this exact 1-to-1
 * shape caused there). `ON DELETE CASCADE` replicates
 * `@Relationship(deleteRule: .cascade, inverse: \QueuedBakeConfig.queuedBake)`
 * on `QueuedBake.config`.
 */
@Entity(
    tableName = "queued_bake_configs",
    foreignKeys = [
        ForeignKey(
            entity = QueuedBakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["queuedBakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueuedBakeConfigEntity(
    @PrimaryKey val queuedBakeId: String,
    val numLoaves: Int,
    val hydration: Double,
    val fat: Double,
    val salt: Double,
    val yeast: Double,
    val yeastType: String,
    val flourBlend: List<FlourBlendEntry>,
    val sweetenerType: String?,
    val sweetenerPct: Double,
    val usePrefermant: Boolean,
    val prefermentType: String,
    val isSpeedRun: Boolean,
    val isHumidityMode: Boolean,
    val relativeHumidity: Double,
    val shapeName: String,
)

/** Room `@Relation` query result — a queued bake joined with its (always exactly one) config row. */
data class QueuedBakeWithConfig(
    @Embedded val queuedBake: QueuedBakeEntity,
    @Relation(parentColumn = "id", entityColumn = "queuedBakeId")
    val config: QueuedBakeConfigEntity?,
)

fun QueuedBakeConfigEntity.toDomain(): QueuedBakeConfig = QueuedBakeConfig(
    numLoaves = numLoaves,
    hydration = hydration,
    fat = fat,
    salt = salt,
    yeast = yeast,
    yeastType = yeastType,
    flourBlend = flourBlend,
    sweetenerType = sweetenerType,
    sweetenerPct = sweetenerPct,
    usePrefermant = usePrefermant,
    prefermentType = prefermentType,
    isSpeedRun = isSpeedRun,
    isHumidityMode = isHumidityMode,
    relativeHumidity = relativeHumidity,
    shapeName = shapeName,
)

fun QueuedBakeConfig.toEntity(queuedBakeId: String): QueuedBakeConfigEntity = QueuedBakeConfigEntity(
    queuedBakeId = queuedBakeId,
    numLoaves = numLoaves,
    hydration = hydration,
    fat = fat,
    salt = salt,
    yeast = yeast,
    yeastType = yeastType,
    flourBlend = flourBlend,
    sweetenerType = sweetenerType,
    sweetenerPct = sweetenerPct,
    usePrefermant = usePrefermant,
    prefermentType = prefermentType,
    isSpeedRun = isSpeedRun,
    isHumidityMode = isHumidityMode,
    relativeHumidity = relativeHumidity,
    shapeName = shapeName,
)

/**
 * `config` is required — throws if a `QueuedBakeEntity` row somehow has
 * no matching config row. That's real data corruption (the FK/cascade
 * above should make it structurally impossible in practice), and
 * matches [QueuedBake.config]'s own non-optional type: silently
 * fabricating a default config or dropping the row would hide the
 * corruption instead of surfacing it.
 */
fun QueuedBakeWithConfig.toDomain(): QueuedBake {
    val configEntity = requireNotNull(config) {
        "QueuedBakeEntity ${queuedBake.id} has no matching QueuedBakeConfigEntity row — data corruption."
    }
    return QueuedBake(
        id = queuedBake.id,
        name = queuedBake.name,
        style = queuedBake.style,
        ovenTempF = queuedBake.ovenTempF,
        createdAt = queuedBake.createdAt,
        steps = queuedBake.steps,
        config = configEntity.toDomain(),
    )
}

fun QueuedBake.toEntity(scheduledBakeId: String? = null): QueuedBakeEntity = QueuedBakeEntity(
    id = id,
    name = name,
    style = style,
    ovenTempF = ovenTempF,
    createdAt = createdAt,
    steps = steps,
    scheduledBakeId = scheduledBakeId,
)
