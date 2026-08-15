package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakingSection
import com.BreadIQ.myapp.model.TechniqueSection
import kotlin.math.ceil

/** Exact hex-for-hex port of `excel-export.ts`'s palette (verified against the source directly, not approximated). */
object RecipeXLSXPalette {
    const val NAVY = "FF1B3A8C" // section headers bg, title text
    const val LIGHT_GRAY = "FFF0F0F0" // sub-headers bg, column-header row bg
    const val ALT_ROW = "FFF7F9FC" // alternating row background
    const val WHITE = "FFFFFFFF" // header text on navy
    const val AMBER = "FFFFE9D0" // Baker's Tip box background
    const val INNER_BORDER = "FFCCCCCC" // thin inner borders
    const val OUTER_BORDER = NAVY // medium outer table border
    const val TEXT = "FF1A1A2E" // default body text
    const val MUTED = "FF555577" // muted/secondary text
    const val AMBER_TEXT = "FF7B4F0A" // Baker's Tip text
    const val WARN_BG = "FFFFF7ED" // humidity warning banner bg
    const val WARN_TEXT = "FF92400E" // humidity warning banner text
    const val WARN_BORDER = "FFFB923C" // humidity warning banner border
}

object RecipeXLSXLayout {
    /** Column A/B/C widths (26/14/70 char-widths) and column count — fixed for this document, not a general-purpose parameter. */
    const val COLS = 3
    val columnWidths: List<Double> = listOf(26.0, 14.0, 70.0)
}

/**
 * Row/section builders — a Kotlin port of `excel-export.ts`'s
 * `sectionHeader`/`subHeader`/`kvRow`/`dataRow`/`techBlock`/`bakingBlock`/
 * `applyTableBorders`/`rowH` helpers, operating on [XLSXWorkbook]'s
 * primitives ([XLSXWorksheet]/[XLSXStyle]). Each row builder takes the
 * row it should write at and returns the next free row, matching the
 * reference's own mutable-`r`-cursor pattern.
 */
object RecipeXLSXBuilders {

    /**
     * `rowH()` — row-height-from-text-length estimator. The reference's
     * own comment ("~80 chars per wrapped line") disagrees with its own
     * default parameter value (70, not 80) — ported the actual default,
     * not the comment, matching this codebase's standing practice of
     * porting real behavior over what a comment claims it does.
     */
    fun rowH(text: String, min: Int = 16, colWidth: Int = 70): Double =
        maxOf(min, (ceil(text.length.toDouble() / colWidth)).toInt() * 14 + 4).toDouble()

    private fun thinBorder(): XLSXBorderSide = XLSXBorderSide(XLSXBorderStyleKind.THIN, RecipeXLSXPalette.INNER_BORDER)
    private fun mediumBorder(): XLSXBorderSide = XLSXBorderSide(XLSXBorderStyleKind.MEDIUM, RecipeXLSXPalette.OUTER_BORDER)

    /**
     * Outer perimeter of `[r1...r2] x [c1...c2]` gets a medium navy
     * border, every interior grid line gets a thin gray border — the one
     * structural pattern the reference reuses for every table/sub-table.
     * Applied AFTER a table's cells are already written (mutates their
     * existing style in place), matching the reference's own call order.
     */
    fun applyTableBorders(ws: XLSXWorksheet, r1: Int, r2: Int, c1: Int = 1, c2: Int = RecipeXLSXLayout.COLS) {
        if (r1 > r2 || c1 > c2) return
        for (r in r1..r2) {
            for (c in c1..c2) {
                val cells = ws.cellsByRow[r] ?: continue
                val idx = cells.indexOfFirst { it.col == c }
                if (idx < 0) continue
                val style = cells[idx].style
                val newStyle = style.copy(
                    border = XLSXBorder(
                        top = if (r == r1) mediumBorder() else thinBorder(),
                        bottom = if (r == r2) mediumBorder() else thinBorder(),
                        left = if (c == c1) mediumBorder() else thinBorder(),
                        right = if (c == c2) mediumBorder() else thinBorder(),
                    ),
                )
                cells[idx] = cells[idx].copy(style = newStyle)
            }
        }
    }

    fun sectionHeader(ws: XLSXWorksheet, row: Int, text: String): Int {
        ws.setCell(
            row = row, col = 1, string = text,
            style = XLSXStyle(
                font = XLSXFont(size = 11.0, bold = true, argb = RecipeXLSXPalette.WHITE),
                fillArgb = RecipeXLSXPalette.NAVY, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.CENTER, indent = 1,
            ),
        )
        ws.merge(startRow = row, endRow = row, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, 22.0)
        return row + 1
    }

    fun subHeader(ws: XLSXWorksheet, row: Int, text: String): Int {
        ws.setCell(
            row = row, col = 1, string = text,
            style = XLSXStyle(
                font = XLSXFont(size = 10.0, bold = true, argb = RecipeXLSXPalette.NAVY),
                fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.CENTER, indent = 1,
            ),
        )
        ws.merge(startRow = row, endRow = row, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, 18.0)
        return row + 1
    }

    /** Column A = bold key label, columns B-C merged = wrapped value, background alternates via `alt`. */
    fun kvRow(ws: XLSXWorksheet, row: Int, key: String, value: String, alt: Boolean = false): Int {
        val bg = if (alt) RecipeXLSXPalette.ALT_ROW else null
        ws.setCell(row = row, col = 1, string = key, style = XLSXStyle(font = XLSXFont(bold = true), fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, indent = 1))
        ws.setCell(row = row, col = 2, string = value, style = XLSXStyle(fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true))
        ws.merge(startRow = row, endRow = row, startCol = 2, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, rowH(value))
        return row + 1
    }

    /** Light-gray bold column-header row (e.g. "Ingredient" / "Weight (g)" / "Baker's %") — A left-aligned, B/C right-aligned. */
    fun columnHeaderRow(ws: XLSXWorksheet, row: Int, a: String, b: String, c: String): Int {
        val font = XLSXFont(bold = true)
        ws.setCell(row = row, col = 1, string = a, style = XLSXStyle(font = font, fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.CENTER))
        ws.setCell(row = row, col = 2, string = b, style = XLSXStyle(font = font, fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.RIGHT, verticalAlignment = XLSXVerticalAlignment.CENTER))
        ws.setCell(row = row, col = 3, string = c, style = XLSXStyle(font = font, fillArgb = RecipeXLSXPalette.LIGHT_GRAY, horizontalAlignment = XLSXHorizontalAlignment.RIGHT, verticalAlignment = XLSXVerticalAlignment.CENTER))
        ws.setRowHeight(row, 18.0)
        return row + 1
    }

    /**
     * 3-column data row: A left-aligned, B/C right-aligned. `muted` colors
     * column C and indents column A to 3 (used for indented
     * sub-ingredient rows, e.g. per-flour-type lines under a blended
     * "Flour (Total)" row).
     */
    fun dataRow(ws: XLSXWorksheet, row: Int, a: String, b: String, c: String, alt: Boolean = false, bold: Boolean = false, muted: Boolean = false): Int {
        val bg = if (alt) RecipeXLSXPalette.ALT_ROW else null
        val font = XLSXFont(bold = bold)
        val cFont = if (muted) font.copy(argb = RecipeXLSXPalette.MUTED) else font
        ws.setCell(row = row, col = 1, string = a, style = XLSXStyle(font = font, fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, indent = if (muted) 3 else 0))
        ws.setCell(row = row, col = 2, string = b, style = XLSXStyle(font = font, fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.RIGHT, verticalAlignment = XLSXVerticalAlignment.TOP))
        ws.setCell(row = row, col = 3, string = c, style = XLSXStyle(font = cFont, fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.RIGHT, verticalAlignment = XLSXVerticalAlignment.TOP))
        ws.setRowHeight(row, 16.0)
        return row + 1
    }

    private fun stepRow(ws: XLSXWorksheet, row: Int, index: Int, text: String, alt: Boolean): Int {
        val bg = if (alt) RecipeXLSXPalette.ALT_ROW else null
        ws.setCell(row = row, col = 1, string = "Step ${index + 1}", style = XLSXStyle(font = XLSXFont(size = 9.0, bold = true, argb = RecipeXLSXPalette.NAVY), fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, indent = 1))
        ws.setCell(row = row, col = 2, string = text, style = XLSXStyle(font = XLSXFont(size = 9.0), fillArgb = bg, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true))
        ws.merge(startRow = row, endRow = row, startCol = 2, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, rowH(text, 16, 70))
        return row + 1
    }

    /** The amber "Baker's Tip" callout row shared by [techBlock]/[bakingBlock]. */
    private fun tipRow(ws: XLSXWorksheet, row: Int, text: String): Int {
        ws.setCell(
            row = row, col = 1, string = "Baker's Tip: $text",
            style = XLSXStyle(
                font = XLSXFont(size = 9.0, italic = true, argb = RecipeXLSXPalette.AMBER_TEXT),
                fillArgb = RecipeXLSXPalette.AMBER, horizontalAlignment = XLSXHorizontalAlignment.LEFT, verticalAlignment = XLSXVerticalAlignment.TOP, wrapText = true, indent = 1,
            ),
        )
        ws.merge(startRow = row, endRow = row, startCol = 1, endCol = RecipeXLSXLayout.COLS)
        ws.setRowHeight(row, rowH(text, 16, 70))
        return row + 1
    }

    /** Method `kvRow`, then one row per step, then the amber tip row. */
    fun techBlock(ws: XLSXWorksheet, row: Int, tech: TechniqueSection): Int {
        var r = kvRow(ws, row, "Method", tech.method)
        tech.steps.forEachIndexed { i, step ->
            r = stepRow(ws, r, i, step, i % 2 == 1)
        }
        r = tipRow(ws, r, tech.tip)
        return r
    }

    /** 5 fixed `kvRow`s (Oven Temp/Duration/Steam & Equipment/Scoring/Internal Temp), alternating bg, then the amber tip row. */
    fun bakingBlock(ws: XLSXWorksheet, row: Int, baking: BakingSection): Int {
        val fields = listOf(
            "Oven Temp" to baking.temp,
            "Duration" to baking.duration,
            "Steam / Equipment" to baking.steam,
            "Scoring" to baking.scoring,
            "Internal Temp" to baking.internalTemp,
        )
        var r = row
        fields.forEachIndexed { i, (key, value) ->
            r = kvRow(ws, r, key, value, alt = i % 2 == 1)
        }
        r = tipRow(ws, r, baking.tip)
        return r
    }
}
