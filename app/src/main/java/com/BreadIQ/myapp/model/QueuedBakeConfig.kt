package com.BreadIQ.myapp.model

import kotlinx.serialization.Serializable

/**
 * Ported from the iOS app's `Models/QueuedBakeConfig.swift`.
 *
 * One flour's share of a blend, e.g. `{ type: "bread", percent: 80 }`.
 *
 * `@Serializable` — added for PORTING_PLAN.md step 5: Room's
 * `QueuedBakeConfigEntity`/`RecipeEntity` TypeConverters JSON-encode a
 * flour blend into a single column via kotlinx.serialization, the same
 * way SwiftData transparently serializes a `Codable` array into its own
 * column. Not needed by anything before Room; harmless for every other
 * existing use of this type.
 */
@Serializable
data class FlourBlendEntry(
    val type: String,
    val percent: Double,
)

/**
 * Snapshot of the calculator's formula inputs at the moment a bake was
 * queued, so it can be started later without re-entering the recipe.
 * Originally ported from `types/bake.ts`'s `QueuedBakeConfig` in the
 * source Expo app.
 */
data class QueuedBakeConfig(
    val numLoaves: Int,
    val hydration: Double,
    val fat: Double,
    val salt: Double,
    val yeast: Double,
    val yeastType: String,
    val flourBlend: List<FlourBlendEntry> = emptyList(),
    val sweetenerType: String? = null,
    val sweetenerPct: Double = 0.0,
    /**
     * Matches the source's spelling exactly (`usePrefermant`, not the
     * correctly-spelled `usePreferment`) — preserved for fidelity, same as
     * the iOS port.
     */
    val usePrefermant: Boolean = false,
    val prefermentType: String,
    val isSpeedRun: Boolean = false,
    val isHumidityMode: Boolean = false,
    val relativeHumidity: Double = 50.0,
    val shapeName: String,

    // Note: the iOS `@Model` class also carries a `queuedBake: QueuedBake?`
    // back-reference, needed only for SwiftData's inverse relationship —
    // left out here for the same reason as `BakeStep.session`, see that
    // file's comment.
) {
    // The iOS source hand-wrote a `copy()` method here specifically because
    // `@Model` classes are reference types with a required 1-to-1 inverse
    // relationship, so handing the SAME instance to two different
    // `QueuedBake`s was a real, silently-reachable data-corruption bug
    // there (a hard SwiftData crash, traced back to exactly this shape —
    // see the iOS file's doc comment for the full incident writeup).
    //
    // Kotlin's `data class` already synthesizes an equivalent `copy()` —
    // a fresh, independent instance with the same field values — for
    // free, so no manual method is needed here. Callers that need a new,
    // non-aliased `QueuedBakeConfig` should just call `.copy()`.
}
