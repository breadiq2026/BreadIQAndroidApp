package com.BreadIQ.myapp.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds/holds the single app-wide [BreadIQDatabase] — the Room
 * counterpart to `data/SupabaseClientProvider.kt`'s same
 * double-checked-locking singleton shape for [io.github.jan.supabase.SupabaseClient].
 * Called from `BreadIQApplication.onCreate()`, mirroring
 * `BreadIQApp.swift`'s `init()` calling `Self.makeModelContainer()` at
 * app launch — see [BreadIQDatabase]'s own doc comment for how Room's
 * lazy-open behavior makes that call structurally similar but not an
 * equivalent success/failure gate the way the SwiftData call is.
 *
 * **[openEagerly]/[eraseLocalStore] close that gap** — see their own doc
 * comments. `MainActivity.kt`'s own `DbOpenState` gating is what
 * actually uses them to reproduce `BreadIQApp.swift`'s `if let
 * modelContainer { ... } else { DataStoreErrorScreen(...) }` branch.
 */
object DatabaseProvider {
    private const val DATABASE_NAME = "breadiq.db"

    @Volatile
    private var instance: BreadIQDatabase? = null

    fun getInstance(context: Context): BreadIQDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /**
     * Forces Room's lazy `.build()` open to happen eagerly, once — the
     * direct analog of `makeModelContainer()`'s own try/catch around
     * `ModelContainer(for:configurations:)`. A bare [getInstance] call
     * does NOT trigger the real SQLite file I/O — Room only opens the
     * underlying connection lazily, on the first real query. Touching
     * [androidx.room.RoomDatabase.openHelper]'s `writableDatabase` is
     * what actually forces that open (and is where a corrupted store or
     * a missing/failed migration would throw), so this is the one call
     * this app makes for the sole purpose of finding out "did this
     * actually work" before anything else touches the database. Run on
     * [Dispatchers.IO], matching every other blocking-I/O call in this
     * app.
     */
    suspend fun openEagerly(context: Context): Result<BreadIQDatabase> = withContext(Dispatchers.IO) {
        try {
            val db = getInstance(context)
            db.openHelper.writableDatabase
            Result.success(db)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * "Erase & Start Fresh"'s real recovery action. `Context.deleteDatabase(name)`
     * already finds and removes the database file plus its
     * `-wal`/`-shm`/`-journal` siblings correctly — a real simplification
     * over the iOS source's `eraseLocalStoreAndRetry()`, which has to
     * manually reconstruct the store's file path and delete each sidecar
     * suffix in a loop, since SwiftData/`ModelConfiguration(url:)` has no
     * built-in "delete this named store" helper the way Android does.
     *
     * Clears the cached [instance] first — necessary, not optional:
     * without it, the next [getInstance] call after deleting the on-disk
     * file would just hand back the same (now pointing at a deleted
     * file, still-broken) cached reference instead of rebuilding fresh.
     * Callers should call [openEagerly] again immediately after this to
     * actually rebuild.
     */
    fun eraseLocalStore(context: Context) {
        synchronized(this) {
            instance = null
        }
        context.applicationContext.deleteDatabase(DATABASE_NAME)
    }

    private fun build(appContext: Context): BreadIQDatabase =
        Room.databaseBuilder(appContext, BreadIQDatabase::class.java, DATABASE_NAME)
            .build()
}
