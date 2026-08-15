package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.ProofTimeResult

/**
 * Everything `CalculatorViewModel.shareRecipe()` has in scope at its call
 * site, bundled into one plain data class so [RecipeXLSXExporter.build]
 * can stay a free function testable without a live ViewModel — the same
 * shape/reasoning as the source's own doc comment on this type.
 */
data class RecipeXLSXExportContext(
    val formulaResult: FormulaResult,
    val proofResult: ProofTimeResult,
    val recipeName: String,
    val styleValue: String,
    val styleLabel: String,
    val shapeValue: String,
    val shapeLabel: String,
    val numLoaves: Int,
    val hydrationPercent: Double,
    val flourBlend: List<FlourBlendEntry>,
    val usePrefermant: Boolean,
    val prefermentType: String,
    val yeastLabel: String,
    val yeastFactor: Double,
    val sweetenerLabel: String? = null,
    val isHumidityMode: Boolean,
    val relativeHumidity: Double,
)

/**
 * Builds the `.xlsx` bytes for "Share Recipe" — the ingredient-row
 * enumeration mirrors the source's own `buildShareText` order and
 * conditionals exactly (flour blend expansion, water, salt,
 * fat-if-positive, yeast, sweetener-if-present, malt/egg/milk/
 * butter-if-present), reading percentages from
 * [FormulaResult.bakerPercentages] (already computed) rather than
 * re-deriving them, since that's the single source of truth this app's
 * own numeric formula already produces.
 */
object RecipeXLSXExporter {

    fun build(context: RecipeXLSXExportContext): ByteArray = RecipeXLSXBuilder.build(input(context))

    fun input(context: RecipeXLSXExportContext): RecipeXLSXInput {
        val f = context.formulaResult
        val bp = f.bakerPercentages

        val ingredients = mutableListOf<RecipeXLSXIngredientRow>()
        // Gated on the raw blend list size, NOT the count of positive-percent
        // entries — matches the source's own `context.flourBlend.count > 1`
        // exactly (the `where fb.percent > 0` filter only applies inside the
        // loop below, not to this gate).
        if (context.flourBlend.size > 1) {
            for (fb in context.flourBlend) {
                if (fb.percent <= 0) continue
                val grams = f.flourWeight * (fb.percent / 100)
                val label = f.flourBreakdown?.firstOrNull { it.type == fb.type }?.label ?: fb.type
                ingredients.add(RecipeXLSXIngredientRow(name = label, weightGrams = grams, bakersPercentText = pctText(fb.percent), indent = true))
            }
            ingredients.add(RecipeXLSXIngredientRow(name = "Flour (total)", weightGrams = f.flourWeight, bakersPercentText = "100%"))
        } else {
            ingredients.add(RecipeXLSXIngredientRow(name = "Flour", weightGrams = f.flourWeight, bakersPercentText = "100%"))
        }
        ingredients.add(RecipeXLSXIngredientRow(name = "Water", weightGrams = f.waterWeight, bakersPercentText = pctText(bp.water)))
        ingredients.add(RecipeXLSXIngredientRow(name = "Salt", weightGrams = f.saltWeight, bakersPercentText = pctText(bp.salt)))
        if (f.fatWeight > 0) {
            ingredients.add(RecipeXLSXIngredientRow(name = "Fat / Oil", weightGrams = f.fatWeight, bakersPercentText = pctText(bp.fat)))
        }
        ingredients.add(RecipeXLSXIngredientRow(name = "Yeast", weightGrams = f.yeastWeight, bakersPercentText = pctText(bp.yeast)))
        val sw = f.sweetenerWeight
        if (sw != null && sw > 0) {
            ingredients.add(RecipeXLSXIngredientRow(name = context.sweetenerLabel ?: "Sweetener", weightGrams = sw, bakersPercentText = pctText(bp.sweetener ?: 0.0)))
        }
        val malt = f.maltWeight
        if (malt != null && malt > 0) {
            ingredients.add(RecipeXLSXIngredientRow(name = "Diastatic Malt", weightGrams = malt, bakersPercentText = pctText(bp.malt ?: 0.0)))
        }
        val egg = f.eggWeight
        if (egg != null && egg > 0) {
            ingredients.add(RecipeXLSXIngredientRow(name = "Eggs", weightGrams = egg, bakersPercentText = pctText(bp.eggs ?: 0.0)))
        }
        val milk = f.milkWeight
        if (milk != null && milk > 0) {
            // `f.dairyDisplayName` — a 4th "Milk" display site not caught
            // by ROADMAP.md's original 3-site enumeration on iOS (that
            // note predates the XLSX exporter there too) — see
            // `FormulaResult.dairyDisplayName`'s own doc comment.
            ingredients.add(RecipeXLSXIngredientRow(name = f.dairyDisplayName ?: "Whole Milk", weightGrams = milk, bakersPercentText = pctText(bp.milk ?: 0.0)))
        }
        val butter = f.butterWeight
        if (butter != null && butter > 0) {
            ingredients.add(RecipeXLSXIngredientRow(name = "Butter", weightGrams = butter, bakersPercentText = pctText(bp.butter ?: 0.0)))
        }

        val humidityRh = if (context.isHumidityMode && (context.relativeHumidity >= 65 || context.relativeHumidity <= 35)) context.relativeHumidity.toInt() else null
        val humidityDirection = if (context.isHumidityMode) {
            if (context.relativeHumidity >= 65) "high" else if (context.relativeHumidity <= 35) "low" else null
        } else {
            null
        }

        val prefermentTypeLabel = if (context.usePrefermant) (if (context.prefermentType == "biga") "Biga" else "Poolish") else null
        val doughWeightPerPiece = if (context.numLoaves > 0) f.totalDoughWeight / context.numLoaves else null

        val autolyse = AutolyseCalculator.calculate(context.flourBlend)
        val autolyseBannerText: String? = if (autolyse.tier == AutolyseTier.STANDARD) {
            null
        } else {
            "🌾 HIGH WHOLE WHEAT / RYE FORMULA (${autolyse.combinedPercent.swiftRounded().toInt()}% combined) — " +
                "${if (autolyse.autolyseRequired) "Autolyse required" else "Autolyse recommended"}: ${autolyse.autolyseDurationMinutes} min, hydration ${autolyse.hydrationRangeLabel}. " +
                "Mixing: ${autolyse.mixingStyleLabel}, ${autolyse.kneadTimeLabel}. Bulk target: ${autolyse.bulkVolumeTargetLabel} " +
                "Cold retard: ${autolyse.coldRetardStrengthLabel.lowercase()} — ${autolyse.coldRetardGuidance} See the Method section below for step-by-step timing."
        }

        return RecipeXLSXInput(
            styleValue = context.styleValue, shapeValue = context.shapeValue,
            styleLabel = context.styleLabel, shapeLabel = context.shapeLabel,
            numLoaves = context.numLoaves, hydrationPercent = context.hydrationPercent,
            ingredients = ingredients, totalDoughWeight = f.totalDoughWeight, doughWeightPerPiece = doughWeightPerPiece,
            yeastLabel = context.yeastLabel, yeastFactor = context.yeastFactor,
            prefermentTypeLabel = prefermentTypeLabel, preferment = if (context.usePrefermant) f.preferment else null,
            flourBreakdown = f.flourBreakdown, sweetenerLabel = context.sweetenerLabel,
            proofStages = context.proofResult.stages, totalProofMinutes = context.proofResult.totalMinutes,
            humidityRh = humidityRh, humidityDirection = humidityDirection, autolyseBannerText = autolyseBannerText,
        )
    }

    /**
     * Matches the source's own `pct()` formatting (`%.1f%%`) — kept local
     * rather than reaching into an unrelated file, same "reimplemented
     * locally" convention this codebase already uses for its several
     * independent `jsNumber`/`fmtG` copies.
     */
    private fun pctText(p: Double): String = String.format("%.1f%%", p)

    /**
     * Sanitized filename matching the source's own
     * `BreadIQ_{StyleLabel}_Recipe.xlsx` pattern (non-alphanumeric
     * characters replaced with `_`).
     */
    fun filename(styleLabel: String): String {
        val sanitized = styleLabel.map { if (it.isLetter() || it.isDigit()) it else '_' }.joinToString("")
        return "BreadIQ_${sanitized}_Recipe.xlsx"
    }
}
