package com.BreadIQ.myapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BakeSessionDao {
    @Transaction
    @Query("SELECT * FROM bake_sessions WHERE id = :id")
    suspend fun getById(id: String): BakeSessionWithSteps?

    @Transaction
    @Query("SELECT * FROM bake_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<BakeSessionWithSteps>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: BakeSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<BakeStepEntity>)

    /**
     * `upsertSteps` alone would leave a stale step behind if a session
     * update ever removes one — deletes every step this session
     * currently owns first, then inserts the new set fresh. Cheap: a
     * session's step count is small (single digits) and this only runs
     * on an explicit save, not per-tick UI updates.
     */
    @Query("DELETE FROM bake_steps WHERE sessionId = :sessionId")
    suspend fun deleteStepsForSession(sessionId: String)

    @Delete
    suspend fun deleteSession(session: BakeSessionEntity)

    @Query("DELETE FROM bake_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Replaces a session and its full step set atomically — the save path both `getById`/`observeAll` callers expect to read back consistently. */
    @Transaction
    suspend fun upsertSessionWithSteps(session: BakeSessionEntity, steps: List<BakeStepEntity>) {
        upsertSession(session)
        deleteStepsForSession(session.id)
        upsertSteps(steps)
    }
}
