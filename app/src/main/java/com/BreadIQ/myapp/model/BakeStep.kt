package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/BakeStep.swift`.
 *
 * One step within a `BakeSession` (e.g. "Bulk Fermentation", "Shape", "Bake").
 * Originally ported from `types/bake.ts`'s `BakeStep` in the source Expo app.
 */
enum class StepStatus(val rawValue: String) {
    PENDING("pending"),
    ACTIVE("active"),
    COMPLETED("completed"),
    /** Defined but never actually set in the source app — confirm before relying on it. */
    SKIPPED("skipped"),
}

/**
 * **`order` has no source-app counterpart — it exists in the iOS port only
 * to work around a SwiftData quirk** (a `@Relationship` to-many array does
 * not preserve insertion order once round-tripped through a real
 * persistent store there). This Kotlin port currently holds `steps` as a
 * plain `List<BakeStep>` on [BakeSession], which already preserves
 * insertion order with no such workaround needed — `order`/[BakeSession]'s
 * `orderedSteps` are kept anyway for field-for-field fidelity with the
 * iOS model and because Room (Phase 5 of the porting plan) may reintroduce
 * the same ordering problem once `steps` becomes a real one-to-many
 * relationship instead of an embedded list.
 */
data class BakeStep(
    val id: String,
    val label: String,
    val description: String,
    val durationMinutes: Int,
    val order: Int = 0,

    /** No countdown at all — user marks the step complete manually (e.g. "Scale Ingredients"). */
    val noTimer: Boolean = false,
    /** A countdown exists, but the user must explicitly start it. */
    val manualStart: Boolean = false,

    val scheduledEndAt: java.time.Instant? = null,
    val actualEndAt: java.time.Instant? = null,
    val status: StepStatus = StepStatus.PENDING,

    /** id of the scheduled "step complete" local notification. */
    val notificationId: String? = null,
    /** id of the T-5-minute prep notification fired before the step ends. */
    val prepNotifId: String? = null,
    /** ids for mid-step coil-fold reminder notifications (artisan/ciabatta bulk ferment only). */
    val coilFoldNotifIds: List<String>? = null,

    // Note: the iOS `@Model` class also carries a `session: BakeSession?`
    // back-reference — that existed purely to satisfy SwiftData's
    // `@Relationship(inverse:)` requirement on `BakeSession.steps`, not as
    // meaningful app data. A plain Kotlin value type doesn't need a
    // bidirectional relationship (and holding one here would make
    // `BakeStep`/`BakeSession` a reference cycle), so it's intentionally
    // left out — `BakeSession.steps` is the single owning direction.
)
