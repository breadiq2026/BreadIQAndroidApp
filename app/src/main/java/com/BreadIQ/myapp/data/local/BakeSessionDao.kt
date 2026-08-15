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

    /**
     * Narrow patch of just the three notification-id columns on a single
     * step — [com.BreadIQ.myapp.core.BakeNotificationScheduler]'s
     * counterpart of the iOS source mutating those fields directly on
     * the already-in-memory SwiftData model and calling
     * `modelContext.save()`. By the time this runs, the ViewModel call
     * site has already persisted the rest of the step's row via
     * [upsertSessionWithSteps] — this only needs to patch the ids
     * scheduling just produced, not rewrite the whole row.
     */
    @Query("UPDATE bake_steps SET notificationId = :notificationId, prepNotifId = :prepNotifId, coilFoldNotifIds = :coilFoldNotifIds WHERE id = :stepId")
    suspend fun updateStepNotificationIds(stepId: String, notificationId: String?, prepNotifId: String?, coilFoldNotifIds: List<String>?)

    /** Same shape as [updateStepNotificationIds], for the session-level oven-preheat notification id. */
    @Query("UPDATE bake_sessions SET ovenPreheatNotifId = :ovenPreheatNotifId WHERE id = :sessionId")
    suspend fun updateOvenPreheatNotifId(sessionId: String, ovenPreheatNotifId: String?)

    /** Replaces a session and its full step set atomically — the save path both `getById`/`observeAll` callers expect to read back consistently. */
    @Transaction
    suspend fun upsertSessionWithSteps(session: BakeSessionEntity, steps: List<BakeStepEntity>) {
        upsertSession(session)
        deleteStepsForSession(session.id)
        upsertSteps(steps)
    }
}
