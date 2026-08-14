package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.Recipe

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift`'s
 * `CalculatorFormatting` enum — small pure helpers specific to the
 * Calculator screen, not already covered by [FormulaCalculator]/
 * [CostEstimator].
 */
object CalculatorFormatting {

    /**
     * `computeHydrationAdj()`. **Preserved exactly as verified against
     * the iOS source, including a real comment/code mismatch carried
     * over from there**: the source's own comment claims "round to
     * nearest 0.1", but `Math.round(raw / 10) * 10 / 10` algebraically
     * simplifies to just `Math.round(raw / 10)` — a whole number, not a
     * tenth. The *comment* is wrong, not the behavior; ported the actual
     * arithmetic, not what the comment claims it does.
     */
    fun computeHydrationAdj(blend: List<FlourBlendEntry>): Double {
        val adjMap = mapOf(
            "bread" to 0.0, "00" to -2.0, "semolina" to 15.0, "whole_wheat" to 40.0,
            "rye" to 40.0, "spelt" to -15.0, "einkorn" to -15.0,
        )
        val raw = blend.sumOf { (it.percent / 100) * (adjMap[it.type] ?: 0.0) }
        return (raw / 10).swiftRounded()
    }

    fun formatTime(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    /**
     * No local-recipe-id precedent existed before this screen on iOS —
     * `RecipesScreen` there only ever reads/deletes server-sourced
     * `Recipe` rows. Negative synthetic ids (approved directly on the
     * iOS side, carried over here): guaranteed never to collide with a
     * real server-assigned id (always positive), and an obviously-fake
     * range if it ever surfaces in a debug view. Monotonically
     * decreasing, never reused.
     */
    fun nextLocalRecipeId(existing: List<Recipe>): Int {
        val existingNegative = existing.map { it.id }.filter { it < 0 }
        return (existingNegative.minOrNull() ?: 0) - 1
    }
}
