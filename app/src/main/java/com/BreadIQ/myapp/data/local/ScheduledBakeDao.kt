package com.BreadIQ.myapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.BreadIQ.myapp.model.ScheduledBake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface ScheduledBakeDao {
    @Query("SELECT * FROM scheduled_bakes WHERE id = :id")
    suspend fun getEntityById(id: String): ScheduledBakeEntity?

    @Query("SELECT * FROM scheduled_bakes ORDER BY startTime ASC")
    fun observeAllEntities(): Flow<List<ScheduledBakeEntity>>

    @Transaction
    @Query("SELECT * FROM queued_bakes WHERE scheduledBakeId = :scheduledBakeId")
    suspend fun getOwnedQueueItem(scheduledBakeId: String): QueuedBakeWithConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduledBake(scheduledBake: ScheduledBakeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueItem(queueItem: QueuedBakeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueItemConfig(config: QueuedBakeConfigEntity)

    @Query("DELETE FROM scheduled_bakes WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Narrow patch of just the `calendarEventId` column — same "patch
     * the one id, don't rewrite the row" pattern as
     * `BakeSessionDao.updateStepNotificationIds`/`updateOvenPreheatNotifId`
     * from the notifications session. Written by
     * [com.BreadIQ.myapp.viewmodel.ScheduleViewModel.addToCalendar] after
     * [com.BreadIQ.myapp.core.CalendarEventScheduler.addBakeEvent]
     * succeeds — the Android counterpart of the source setting
     * `scheduled.calendarEventId` directly on its SwiftData model and
     * calling `modelContext.save()`.
     */
    @Query("UPDATE scheduled_bakes SET calendarEventId = :calendarEventId WHERE id = :id")
    suspend fun updateCalendarEventId(id: String, calendarEventId: String?)

    /**
     * Assembles the full [ScheduledBake] domain object — see
     * `ScheduledBakeEntity.kt`'s own doc comment for why this is two
     * explicit queries in one transaction rather than a nested `@Relation`.
     */
    @Transaction
    suspend fun getById(id: String): ScheduledBake? {
        val entity = getEntityById(id) ?: return null
        val queueItem = requireNotNull(getOwnedQueueItem(id)) {
            "ScheduledBakeEntity $id has no matching owned QueuedBakeEntity row — data corruption."
        }
        return entity.toDomain(queueItem = queueItem.toDomain())
    }

    fun observeAll(): Flow<List<ScheduledBakeEntity>> = observeAllEntities()

    /**
     * Saves a scheduled bake together with the [com.BreadIQ.myapp.model.QueuedBake]
     * snapshot it owns and that snapshot's own config — the full 3-row
     * write, atomically. Mirrors `ScheduledBakeEntity.toEntity()`'s own
     * note that this is always a *fresh* `QueuedBake` built at schedule
     * time, never a reference to an existing Queue-tab row.
     */
    @Transaction
    suspend fun upsertScheduledBakeWithQueueItem(
        scheduledBake: ScheduledBakeEntity,
        queueItem: QueuedBakeEntity,
        queueItemConfig: QueuedBakeConfigEntity,
    ) {
        upsertScheduledBake(scheduledBake)
        upsertQueueItem(queueItem)
        upsertQueueItemConfig(queueItemConfig)
    }
}

/** [ScheduledBakeDao.observeAllEntities] mapped to full domain objects — one extra query per row per emission, acceptable for a list that's realistically single-digit-sized (a user's upcoming scheduled bakes). */
fun ScheduledBakeDao.observeAllAsDomain(): Flow<List<ScheduledBake>> = observeAllEntities().map { entities ->
    entities.map { entity ->
        val queueItem = requireNotNull(getOwnedQueueItem(entity.id)) {
            "ScheduledBakeEntity ${entity.id} has no matching owned QueuedBakeEntity row — data corruption."
        }
        entity.toDomain(queueItem = queueItem.toDomain())
    }
}
