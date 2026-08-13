package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/ProofTimeResult.swift`.
 *
 * One phase of the fermentation timeline (e.g. "Bulk Fermentation",
 * "Shape & Rest", "Final Proof"), with the human-readable prose generated
 * server-side by the style-conditional branches in `calcProofTime()`.
 */
data class ProofStage(
    val name: String,
    val durationMinutes: Int,
    val description: String,
)

/**
 * Direct output shape of the backend's `calcProofTime()`
 * (`api-server/src/routes/calculator.ts`, per the iOS port's verification
 * against its actual `return { ... }` statement).
 *
 * Same transience as [FormulaResult]: recomputed fresh on every calculator
 * change, never independently persisted. When a recipe is saved, only a
 * single summarized number — `totalMinutes` — gets copied into
 * `Recipe.proofMinutes`; `stages`, `bulkFermentMinutes`,
 * `finalProofMinutes`, etc. aren't stored anywhere. Modeled as a plain
 * value type for the same reason as [FormulaResult]: no persistent
 * identity, and value-type fixture-testing is what the eventual
 * `ProofTimeCalculator` port needs.
 */
data class ProofTimeResult(
    val bulkFermentMinutes: Int,
    val finalProofMinutes: Int,
    val totalMinutes: Int,
    val coldFermentHours: Double? = null,
    val recoveryMinutes: Int? = null,
    val notes: String,
    val stages: List<ProofStage>,
)
