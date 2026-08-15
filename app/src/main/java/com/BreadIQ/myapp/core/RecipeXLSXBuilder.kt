package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBreakdownEntry
import com.BreadIQ.myapp.model.PrefermentResult
import com.BreadIQ.myapp.model.ProofStage
import kotlin.math.roundToInt

/**
 * One ingredient row for the "Formula — Ingredients" table (and the
 * pre-ferment/final-dough tables) — the XLSX-export-specific counterpart
 * to `calculator.tsx`'s `ExcelIngredient` (`name`/`weight`/`bakersPct`/
 * `indent`). `weightGrams` stays a `Double` here (not pre-formatted) so
 * [RecipeXLSXBuilder] can sum it for bold total rows; formatting into a
 * display string happens at the point each cell is actually written.
 */
data class RecipeXLSXIngredientRow(
    val name: String,
    val weightGrams: Double,
    val bakersPercentText: String,
    val indent: Boolean = false,
)

/**
 * Everything [RecipeXLSXBuilder.build] needs — the XLSX-export
 * counterpart to `excel-export.ts`'s `ExcelExportInput`. Reuses this
 * app's own [PrefermentResult]/[FlourBreakdownEntry]/[ProofStage] types
 * directly (`model/FormulaResult.kt`, `model/ProofTimeResult.kt`) rather
 * than re-modeling them, since they already mirror the reference's
 * two-level preferment/final-mix shape closely. Building this from
 * `CalculatorViewModel`'s real state at `shareRecipe()`'s call site is
 * [RecipeXLSXExporter]'s job, not this file's.
 */
data class RecipeXLSXInput(
    /**
     * Raw `BreadStyleDef.value`/`LoafShape.value` keys — feed the Method
     * section's [TechniqueGuideLookup] and the "Divide After Bulk" row.
     * Distinct from [styleLabel]/[shapeLabel] below, which are the
     * separately-looked-up display strings.
     */
    val styleValue: String,
    val shapeValue: String,
    val styleLabel: String,
    val shapeLabel: String,
    val numLoaves: Int,
    val hydrationPercent: Double,
    val ingredients: List<RecipeXLSXIngredientRow>,
    val totalDoughWeight: Double,
    val doughWeightPerPiece: Double? = null,
    val yeastLabel: String? = null,
    val yeastFactor: Double? = null,
    val prefermentTypeLabel: String? = null,
    val preferment: PrefermentResult? = null,
    val flourBreakdown: List<FlourBreakdownEntry>? = null,
    val sweetenerLabel: String? = null,
    val proofStages: List<ProofStage>? = null,
    val totalProofMinutes: Int? = null,
    val humidityRh: Int? = null,
    val humidityDirection: String? = null,
    /**
     * Pre-formatted banner text for high whole-wheat/rye blends — built
     * by [RecipeXLSXExporter.input] from [AutolyseCalculator.calculate],
     * `null` for standard-tier blends. Passed as ready-to-print text (not
     * a raw `AutolyseGuidance`) so this builder, like the rest of the
     * export pipeline, stays free of tier/label formatting decisions.
     */
    val autolyseBannerText: String? = null,
)

object RecipeXLSXBuilder {

    fun build(input: RecipeXLSXInput): ByteArray {
        val workbook = XLSXWorkbook()
        val sheet = workbook.addWorksheet("Recipe")
        populate(sheet, input)
        return workbook.build()
    }

    /**
     * Writes every section into `sheet`, separated from [build]'s zip
     * assembly specifically so tests can inspect the populated
     * [XLSXWorksheet]'s cell/style/merge state directly, without needing
     * to round-trip through real zip/XML bytes just to assert on content.
     */
    fun populate(sheet: XLSXWorksheet, input: RecipeXLSXInput) {
        RecipeXLSXLayout.columnWidths.forEachIndexed { i, width -> sheet.setColumnWidth(i + 1, width) }

        var r = 1
        r = title(sheet, r)
        r = humidityBanner(sheet, r, input.humidityRh, input.humidityDirection)
        r = autolyseBanner(sheet, r, input.autolyseBannerText)
        r = recipeParameters(sheet, r, input)
        r = formulaIngredients(sheet, r, input)
        r = prefermentAndFinalDough(sheet, r, input)
        r = method(sheet, r, input)
        r = productionSchedule(sheet, r, input)
        footer(sheet, r)
    }

    // MARK: - Title (text-only — no embedded logo, per direct decision:
    // this app has no wordmark asset, only a square app icon, and
    // stretching it into the reference's wide 230x73 bounding box would
    // look distorted)

    private fun title(ws: XLSXWorksheet, row: Int): Int {
        var r = row
        ws.setCell(
            row = r, col = 1, string = "BreadIQ · Recipe Export",
            style = XLSXStyle(font = XLSXFont(size = 16.0, bold = true, argb = RecipeXLSXPalette.NAVY), horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.CENTER),
        )
        ws.merge(startRow = r, endRow = r, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(r, 28.0)
        r += 1

        val formatter = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
        ws.setCell(
            row = r, col = 1, string = "Generated: ${formatter.format(java.util.Date())}",
            style = XLSXStyle(font = XLSXFont(size = 9.0, argb = RecipeXLSXPalette.MUTED), horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP),
        )
        ws.merge(startRow = r, endRow = r, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(r, 14.0)
        r += 1
        return r + 1 // spacer
    }

    // MARK: - Humidity warning banner (gated on both fields present)

    private fun humidityBanner(ws: XLSXWorksheet, row: Int, humidityRh: Int?, humidityDirection: String?): Int {
        if (humidityRh == null || humidityDirection == null) return row
        val text = "⚠  HUMIDITY ADJUSTED FORMULA — Generated at $humidityRh% RH. Water weight and proof timeline have been calibrated for $humidityDirection humidity conditions. This formula will produce different results at significantly different humidity levels. To regenerate for current conditions, open BreadIQ and recalculate."
        ws.setCell(
            row = row, col = 1, string = text,
            style = XLSXStyle(
                font = XLSXFont(size = 9.0, bold = true, argb = RecipeXLSXPalette.WARN_TEXT),
                fillArgb = RecipeXLSXPalette.WARN_BG, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true,
            ),
        )
        ws.merge(startRow = row, endRow = row, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, RecipeXLSXBuilders.rowH(text, 36))
        applyManualBorder(ws, row, 1, XLSXBorderSide(XLSXBorderStyleKind.MEDIUM, RecipeXLSXPalette.WARN_BORDER))
        return row + 2 // the reference leaves 2 rows of gap after this section
    }

    // MARK: - Autolyse guidance banner (high whole-wheat/rye blends only)

    private fun autolyseBanner(ws: XLSXWorksheet, row: Int, text: String?): Int {
        if (text == null) return row
        ws.setCell(
            row = row, col = 1, string = text,
            style = XLSXStyle(
                font = XLSXFont(size = 9.0, bold = true, argb = RecipeXLSXPalette.WARN_TEXT),
                fillArgb = RecipeXLSXPalette.WARN_BG, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true,
            ),
        )
        ws.merge(startRow = row, endRow = row, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, RecipeXLSXBuilders.rowH(text, 36))
        applyManualBorder(ws, row, 1, XLSXBorderSide(XLSXBorderStyleKind.MEDIUM, RecipeXLSXPalette.WARN_BORDER))
        return row + 2
    }

    private fun applyManualBorder(ws: XLSXWorksheet, row: Int, col: Int, side: XLSXBorderSide) {
        val cells = ws.cellsByRow[row] ?: return
        val idx = cells.indexOfFirst { it.col == col }
        if (idx < 0) return
        val newStyle = cells[idx].style.copy(border = XLSXBorder(top = side, bottom = side, left = side, right = side))
        cells[idx] = cells[idx].copy(style = newStyle)
    }

    // MARK: - Recipe Parameters (always shown)

    private fun recipeParameters(ws: XLSXWorksheet, row: Int, input: RecipeXLSXInput): Int {
        val start = row
        var r = RecipeXLSXBuilders.sectionHeader(ws, row, "RECIPE PARAMETERS")
        r = RecipeXLSXBuilders.kvRow(ws, r, "Dough Style", input.styleLabel)
        r = RecipeXLSXBuilders.kvRow(ws, r, "Loaf Shape", input.shapeLabel, alt = true)
        r = RecipeXLSXBuilders.kvRow(ws, r, "Quantity", "${input.numLoaves} ${if (input.numLoaves == 1) "loaf" else "loaves"}")
        r = RecipeXLSXBuilders.kvRow(ws, r, "Hydration", "${fmtPercent(input.hydrationPercent)}%", alt = true)
        RecipeXLSXBuilders.applyTableBorders(ws, start, r - 1)
        return r + 1
    }

    // MARK: - Formula — Ingredients (always shown)

    private fun formulaIngredients(ws: XLSXWorksheet, row: Int, input: RecipeXLSXInput): Int {
        val start = row
        var r = RecipeXLSXBuilders.sectionHeader(ws, row, "FORMULA — INGREDIENTS")
        r = RecipeXLSXBuilders.columnHeaderRow(ws, r, "Ingredient", "Weight (g)", "Baker's %")
        input.ingredients.forEachIndexed { i, ing ->
            r = RecipeXLSXBuilders.dataRow(ws, r, ing.name, fmtG(ing.weightGrams), ing.bakersPercentText, alt = i % 2 == 1, muted = ing.indent)
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "Total Dough Weight", fmtG(input.totalDoughWeight), "", bold = true)
        RecipeXLSXBuilders.applyTableBorders(ws, start + 1, r - 1)
        return r + 1
    }

    // MARK: - Pre-Ferment Build + Final Dough (gated on preferment present)

    private fun prefermentAndFinalDough(ws: XLSXWorksheet, row: Int, input: RecipeXLSXInput): Int {
        val preferment = input.preferment ?: return row
        var r = row

        val pfLabel = input.prefermentTypeLabel ?: preferment.type.replaceFirstChar { it.uppercase() }
        val pfHydration = if (preferment.flourWeight > 0) (preferment.waterWeight / preferment.flourWeight * 100).roundToInt() else 0

        val pfStart = r
        r = RecipeXLSXBuilders.sectionHeader(ws, r, "PRE-FERMENT BUILD — ${pfLabel.uppercase()} — $pfHydration% hydration")
        r = RecipeXLSXBuilders.columnHeaderRow(ws, r, "Ingredient", "Weight (g)", "Baker's %")
        val pfFlours = (input.flourBreakdown ?: emptyList()).filter { (it.prefermentGrams ?: 0.0) > 0.05 }
        if (pfFlours.isEmpty()) {
            r = RecipeXLSXBuilders.dataRow(ws, r, "Flour", fmtG(preferment.flourWeight), "", alt = false)
        } else {
            pfFlours.forEachIndexed { i, flour ->
                r = RecipeXLSXBuilders.dataRow(ws, r, flour.label, fmtG(flour.prefermentGrams ?: 0.0), "", alt = i % 2 == 1)
            }
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "Water", fmtG(preferment.waterWeight), "", alt = true)
        if (preferment.yeastWeight > 0.05) {
            val label = input.yeastLabel?.let { "$it Yeast" } ?: "Yeast"
            val weight = input.yeastFactor?.let { preferment.yeastWeight * it } ?: preferment.yeastWeight
            r = RecipeXLSXBuilders.dataRow(ws, r, label, fmtG(weight), "")
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "Total $pfLabel", fmtG(preferment.totalWeight), "", bold = true)
        RecipeXLSXBuilders.applyTableBorders(ws, pfStart + 1, r - 1)
        r += 1

        val fdStart = r
        r = RecipeXLSXBuilders.sectionHeader(ws, r, "FINAL DOUGH")
        r = RecipeXLSXBuilders.columnHeaderRow(ws, r, "Ingredient", "Weight (g)", "Baker's %")
        val finalMix = preferment.finalMix
        val fdFlours = (input.flourBreakdown ?: emptyList()).filter { it.finalDoughGrams > 0.5 }
        var totalFlour = 0.0
        if (fdFlours.isEmpty()) {
            if (finalMix.flourWeight > 0.5) {
                r = RecipeXLSXBuilders.dataRow(ws, r, "Flour", fmtG(finalMix.flourWeight), "")
                totalFlour = finalMix.flourWeight
            }
        } else {
            fdFlours.forEachIndexed { i, flour ->
                r = RecipeXLSXBuilders.dataRow(ws, r, flour.label, fmtG(flour.finalDoughGrams), "", alt = i % 2 == 1)
                totalFlour += flour.finalDoughGrams
            }
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "Water (remaining)", fmtG(finalMix.waterWeight), "", alt = true)
        r = RecipeXLSXBuilders.dataRow(ws, r, "Salt", fmtG(finalMix.saltWeight), "")
        var runningTotal = totalFlour + finalMix.waterWeight + finalMix.saltWeight
        if (finalMix.fatWeight > 0.05) {
            r = RecipeXLSXBuilders.dataRow(ws, r, "Fat / Oil", fmtG(finalMix.fatWeight), "", alt = true)
            runningTotal += finalMix.fatWeight
        }
        var additionalYeastWeight = 0.0
        if (finalMix.yeastWeight > 0.05) {
            val label = input.yeastLabel?.let { "$it Yeast (additional)" } ?: "Yeast (additional)"
            additionalYeastWeight = input.yeastFactor?.let { finalMix.yeastWeight * it } ?: finalMix.yeastWeight
            r = RecipeXLSXBuilders.dataRow(ws, r, label, fmtG(additionalYeastWeight), "")
            runningTotal += additionalYeastWeight
        }
        val sweetenerWeight = finalMix.sweetenerWeight
        if (sweetenerWeight != null && sweetenerWeight > 0.05) {
            r = RecipeXLSXBuilders.dataRow(ws, r, input.sweetenerLabel ?: "Sweetener", fmtG(sweetenerWeight), "", alt = true)
            runningTotal += sweetenerWeight
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "$pfLabel (ripe, add whole)", fmtG(finalMix.prefermentWeight), "")
        runningTotal += finalMix.prefermentWeight
        r = RecipeXLSXBuilders.dataRow(ws, r, "Total Final Mix", fmtG(runningTotal), "", bold = true)
        RecipeXLSXBuilders.applyTableBorders(ws, fdStart + 1, r - 1)
        return r + 1
    }

    // MARK: - Method (always shown — 4 sub-sections)

    private fun method(ws: XLSXWorksheet, row: Int, input: RecipeXLSXInput): Int {
        var r = row
        val resolved = TechniqueGuideLookup.resolve(input.styleValue, input.shapeValue)

        val kneadStart = r
        r = RecipeXLSXBuilders.subHeader(ws, r, "MIXING / KNEADING")
        r = RecipeXLSXBuilders.techBlock(ws, r, resolved.kneading)
        RecipeXLSXBuilders.applyTableBorders(ws, kneadStart, r - 1)
        r += 1

        val shapeStart = r
        r = RecipeXLSXBuilders.subHeader(ws, r, "SHAPING")
        val doughWeightPerPiece = input.doughWeightPerPiece
        if (doughWeightPerPiece != null) {
            val divideInfo = TechniqueGuideLookup.divideAfterBulk(input.styleValue, input.shapeValue, input.numLoaves, doughWeightPerPiece)
            if (divideInfo != null) r = divideAfterBulkRow(ws, r, divideInfo)
        }
        r = RecipeXLSXBuilders.techBlock(ws, r, resolved.shaping)
        RecipeXLSXBuilders.applyTableBorders(ws, shapeStart, r - 1)
        r += 1

        val proofStart = r
        r = RecipeXLSXBuilders.subHeader(ws, r, "PROOFING")
        r = RecipeXLSXBuilders.techBlock(ws, r, resolved.proofing)
        RecipeXLSXBuilders.applyTableBorders(ws, proofStart, r - 1)
        r += 1

        val bakeStart = r
        r = RecipeXLSXBuilders.subHeader(ws, r, "BAKING")
        r = RecipeXLSXBuilders.bakingBlock(ws, r, resolved.baking)
        RecipeXLSXBuilders.applyTableBorders(ws, bakeStart, r - 1)
        return r + 1
    }

    // MARK: - Production Schedule (gated on non-empty stages)

    private fun productionSchedule(ws: XLSXWorksheet, row: Int, input: RecipeXLSXInput): Int {
        val stages = input.proofStages
        if (stages.isNullOrEmpty()) return row
        var r = row + 1 // 2 extra blank rows before it, one already left by the caller's spacer
        val start = r
        r = RecipeXLSXBuilders.sectionHeader(ws, r, "PRODUCTION SCHEDULE")
        r = RecipeXLSXBuilders.columnHeaderRow(ws, r, "Stage", "Duration", "Notes")
        stages.forEachIndexed { i, stage ->
            r = RecipeXLSXBuilders.dataRow(ws, r, stage.name, fmtDuration(stage.durationMinutes), stage.description, alt = i % 2 == 1, bold = false)
        }
        r = RecipeXLSXBuilders.dataRow(ws, r, "Total Time", fmtDuration(input.totalProofMinutes ?: 0), "", bold = true)
        RecipeXLSXBuilders.applyTableBorders(ws, start + 1, r - 1)
        return r + 1
    }

    // MARK: - Footer (always shown)

    private fun footer(ws: XLSXWorksheet, row: Int): Int {
        val r = row + 1 // 2 blank rows before it
        ws.setCell(
            row = r, col = 1, string = "Generated by BreadIQ — breadiq.io",
            style = XLSXStyle(font = XLSXFont(size = 8.0, italic = true, argb = RecipeXLSXPalette.MUTED), horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP),
        )
        ws.merge(startRow = r, endRow = r, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(r, 14.0)
        return r + 1
    }

    /**
     * The light-gray "Divide After Bulk"/"Portion After Bulk"/"This
     * Batch" callout row at the top of SHAPING — `excel-export.ts`'s own
     * bespoke styling (navy bold label, navy regular wrapped value, both
     * on light-gray), distinct from the shared `kvRow`/`dataRow` builders.
     */
    private fun divideAfterBulkRow(ws: XLSXWorksheet, row: Int, info: TechniqueGuideLookup.DivideAfterBulkInfo): Int {
        ws.setCell(row = row, col = 1, string = info.label, style = XLSXStyle(font = XLSXFont(size = 9.0, bold = true, argb = RecipeXLSXPalette.NAVY), fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, indent = 1))
        ws.setCell(row = row, col = 2, string = info.text, style = XLSXStyle(font = XLSXFont(size = 9.0, argb = RecipeXLSXPalette.NAVY), fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true))
        ws.merge(startRow = row, endRow = row, startCol = 2, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, RecipeXLSXBuilders.rowH(info.text, 18, 70))
        return row + 1
    }

    // MARK: - Formatting helpers (local, mirroring the source's own `fmtG`
    // convention rather than reaching into an unrelated file)

    private fun fmtG(g: Double): String {
        val rounded = (g * 10).swiftRounded() / 10
        val numeric = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
        return "${numeric}g"
    }

    private fun fmtPercent(p: Double): String = if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()

    private fun fmtDuration(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return if (m > 0) "${h}h ${m}m" else "${h}h"
    }
}
