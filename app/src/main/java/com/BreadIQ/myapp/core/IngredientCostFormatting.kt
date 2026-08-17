package com.BreadIQ.myapp.core

import java.time.Duration
import java.time.Instant

/**
 * Ported from the iOS app's `Screens/IngredientCostsScreen.swift`'s
 * `IngredientCostFormatting` enum — pure $/lb ↔ $/g conversion and
 * validation helpers.
 */
object IngredientCostFormatting {
    /**
     * `453.592` g/lb — a bare literal here too, matching the source
     * (and [com.BreadIQ.myapp.core.IngredientDensityConverter]'s own
     * identical literal; the source itself has no shared named constant
     * for this either, so this stays consistent with that established
     * lack of one rather than inventing a new shared constant now).
     */
    private const val GRAMS_PER_POUND = 453.592

    fun formatPerLb(pricePerGram: Double): String = "$${"%.2f".format(pricePerGram * GRAMS_PER_POUND)}/lb"

    fun perGramFromPerLb(lbPrice: Double): Double = lbPrice / GRAMS_PER_POUND

    /** The bare number shown as the input's placeholder, no `$`/`/lb` (those are separate fixed glyphs either side of the text field itself). */
    fun placeholderPerLb(pricePerGram: Double): String = "%.2f".format(pricePerGram * GRAMS_PER_POUND)

    /** Must parse to a positive number or return `null` — invalid/non-positive input is rejected, not silently clamped. */
    fun parsePositivePrice(raw: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        return if (value > 0) value else null
    }

    /**
     * 30-day staleness threshold for the reference-price banner. Compares
     * raw elapsed seconds against `30 * 24 * 60 * 60`, matching the
     * source's own `timeIntervalSince` check exactly — NOT
     * `Duration.toDays() > 30`, which would truncate a genuinely-stale
     * 30.5-day gap down to 30 whole days and wrongly report "not stale".
     */
    fun isStale(refUpdatedAt: Instant?, now: Instant = Instant.now()): Boolean {
        if (refUpdatedAt == null) return false
        return Duration.between(refUpdatedAt, now).seconds > 30L * 24 * 60 * 60
    }
}
