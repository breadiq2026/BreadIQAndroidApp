package com.BreadIQ.myapp.model

import java.time.Instant

/**
 * Ported from the iOS app's `Models/Recipe.swift`.
 *
 * A saved formula, synced with the backend REST API.
 *
 * Field list and types are verified (per the iOS port) against the actual
 * server response — NOT inferred from client-side TypeScript usage. Ground
 * truth: `mapRecipe()` in `api-server/src/routes/recipes.ts` (the exact
 * JSON shape returned by GET/POST/PATCH /api/recipes) and the
 * `recipesTable` Drizzle schema in `lib/db/src/schema/recipes.ts`.
 *
 * `userId` is present in the wire response (the server spreads the full DB
 * row) but the client never reads it — kept here for fidelity to the
 * verified shape; likely droppable later since on-device storage is
 * already scoped to the signed-in user.
 */
data class Recipe(
    val id: Int,
    val userId: String,
    val name: String,
    val loafStyle: String,
    val numLoaves: Int = 1,
    val hydrationPercent: Double,
    val fatPercent: Double = 0.0,
    val flourWeight: Double,
    val waterWeight: Double,
    val fatWeight: Double = 0.0,
    val saltWeight: Double,
    val yeastWeight: Double,
    val yeastPercent: Double,
    val yeastType: String? = null,
    val loafShape: String? = null,
    val preFermentType: String? = null,
    val preFermentFlourWeight: Double? = null,
    val preFermentWaterWeight: Double? = null,
    val preFermentYeastWeight: Double? = null,
    val fermentationType: String = "straight",
    val proofMinutes: Double? = null,
    val notes: String? = null,
    val flourBlend: List<FlourBlendEntry>? = null,
    val sweetenerType: String? = null,
    val sweetenerWeight: Double? = null,
    val humidityRh: Int? = null,
    val humidityDirection: String? = null,
    val humidityAdjusted: Boolean = false,
    val waterWeightUnadjusted: Double? = null,
    val createdAt: Instant = Instant.now(),

    /**
     * `IMPORT_REVIEW_SPEC.md` §3's three new fields on the iOS side — a
     * recipe is "imported" iff `importSourceURL != null` (no separate
     * boolean; that would just be redundant state that can drift from the
     * URL field). **Deliberately client-only, matching the iOS port**: the
     * live `public.recipes` Supabase table has no matching columns, and
     * adding them would mean a real production migration plus editing the
     * separate `api-server` repo. These three fields are not part of the
     * server sync payload — an imported recipe still syncs everywhere else
     * normally, just without these three fields round-tripping (yet).
     */
    val importSourceURL: String? = null,
    val importSourceName: String? = null,
    val formatNote: String? = null,
)
