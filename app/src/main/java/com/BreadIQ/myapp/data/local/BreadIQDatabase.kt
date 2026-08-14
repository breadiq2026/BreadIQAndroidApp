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
 * "Try Again"/"Erase & Start Fresh") has no equivalent here yet — a
 * deliberate scope decision, not an oversight.** SwiftData's
 * `ModelContainer(for:configurations:)` validates and opens the store
 * *eagerly*, at construction time, so a corrupted store or failed
 * migration surfaces immediately as a thrown error iOS can catch right
 * at app launch and branch its root view on. Room's `.build()` is
 * *lazy* — it returns a working `BreadIQDatabase` reference immediately
 * regardless of the on-disk file's condition; the underlying SQLite
 * connection isn't actually opened until the first real query, and any
 * corruption/migration failure throws there instead, on a background
 * thread, from whatever call site happened to trigger it. There is no
 * single "did construction succeed" point to gate a root view on the
 * way `BreadIQApp.swift` does. Since this step wires up persistence
 * only — no screen queries this database yet (see PORTING_PLAN.md) —
 * building a recovery screen now would mean designing it against a
 * failure point this app can't yet produce or test. Revisit once a real
 * screen/repository makes the first actual query and a genuine failure
 * mode (corruption, a missing migration) needs somewhere to surface to.
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
