package com.BreadIQ.myapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Replaces SwiftData's `ModelContainer` (`BreadIQApp.swift`'s
 * `makeModelContainer()`) — the same 7 persisted types, confirmed
 * against that function's own `Schema([...])` list: `BakeSession`,
 * `BakeStep`, `QueuedBake`, `QueuedBakeConfig`, `ScheduledBake`,
 * `Recipe`, `IngredientPriceOverride`.
 *
 * `version = 1` — this is a from-scratch schema (nothing has ever
 * shipped with a Room database in this app yet), so there's no
 * migration to write for it. Deliberately NOT calling
 * `fallbackToDestructiveMigration()` on the builder in
 * [DatabaseProvider] — that's a foot-gun to leave configured by default:
 * it silently wipes local data on any future schema change whose
 * migration was forgotten, rather than failing loudly
 * (`IllegalStateException: A migration from X to Y was required`) the
 * way Room does without it. Failing loudly is the right default for
 * local-first data (bake sessions, queued/scheduled bakes) that has no
 * server backup to fall back on.
 *
 * **`makeModelContainer()`'s error-recovery story (`DataStoreErrorScreen`,
 * "Try Again"/"Erase & Start Fresh") now has a real equivalent** —
 * `DatabaseProvider.openEagerly`/`.eraseLocalStore`, gated by
 * `MainActivity.kt`'s own `DbOpenState`. Room's `.build()` is still
 * *lazy* by nature — it returns a working `BreadIQDatabase` reference
 * immediately regardless of the on-disk file's condition; the
 * underlying SQLite connection isn't actually opened until the first
 * real query. `openEagerly` closes that gap by forcing the open at
 * launch, on purpose, the one place this app calls `openHelper.writableDatabase`
 * just to find out whether it would have thrown. See
 * [DatabaseProvider]'s own doc comment for the fuller writeup.
 */
@Database(
    entities = [
        BakeSessionEntity::class,
        BakeStepEntity::class,
        QueuedBakeEntity::class,
        QueuedBakeConfigEntity::class,
        ScheduledBakeEntity::class,
        RecipeEntity::class,
        IngredientPriceOverrideEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class BreadIQDatabase : RoomDatabase() {
    abstract fun bakeSessionDao(): BakeSessionDao
    abstract fun queuedBakeDao(): QueuedBakeDao
    abstract fun scheduledBakeDao(): ScheduledBakeDao
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientPriceOverrideDao(): IngredientPriceOverrideDao
}
