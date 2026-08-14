package com.BreadIQ.myapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedBakeDao {
    @Transaction
    @Query("SELECT * FROM queued_bakes WHERE id = :id")
    suspend fun getById(id: String): QueuedBakeWithConfig?

    /** The Queue tab's own list — excludes any [QueuedBakeEntity] a [ScheduledBakeEntity] owns as its snapshot (`scheduledBakeId IS NULL`), matching the source app's "Queue tab" vs. "Scheduled" distinction. */
    @Transaction
    @Query("SELECT * FROM queued_bakes WHERE scheduledBakeId IS NULL ORDER BY createdAt DESC")
    fun observeQueueTab(): Flow<List<QueuedBakeWithConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueuedBake(queuedBake: QueuedBakeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: QueuedBakeConfigEntity)

    @Query("DELETE FROM queued_bakes WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Replaces a queued bake and its (always exactly one) config atomically. */
    @Transaction
    suspend fun upsertQueuedBakeWithConfig(queuedBake: QueuedBakeEntity, config: QueuedBakeConfigEntity) {
        upsertQueuedBake(queuedBake)
        upsertConfig(config)
    }
}
