package com.BreadIQ.myapp.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal, hand-rolled `.xlsx` (OOXML/SpreadsheetML) writer — built for
 * `XLSX_EXPORT_SPEC.md`'s styled recipe export specifically, not as a
 * general-purpose spreadsheet API. The iOS source uses the ZIPFoundation
 * SPM package for the zip container (no mature, actively-maintained
 * pure-Swift `.xlsx` *writer* exists there, per that file's own research
 * note). **Android needs no equivalent third-party dependency at all** —
 * `java.util.zip.ZipOutputStream`/`ZipEntry` (standard JDK, always
 * available) does the same job; this is a real simplification the
 * platform gives for free, not a gap.
 *
 * **Every cell value is a pre-formatted `String`, never a raw numeric
 * OOXML type.** This app already formats every displayed weight/
 * percentage/time into a display string before it reaches the UI
 * (`ProofStageNarrator.jsNumber`, this export's own `fmtG`/`fmtPercent`
 * helpers) — reusing that same convention here avoids needing to track
 * `numFmtId`s at all. Alignment (e.g. right-aligning a weight column) is
 * a cell-style concern, independent of whether the underlying OOXML type
 * is `str`/`inlineStr` or `n`. The source models this as a single-case
 * `XLSXCellValue: Hashable { case string(String) }` enum for future
 * extensibility; ported here as a plain `text: String` field on
 * [XLSXCell] directly — genuinely one case today, no behavior
 * difference, one fewer wrapper to unwrap at every call site.
 *
 * **Uses inline strings (`t="inlineStr"`), not a `sharedStrings.xml`
 * dedup table.** A deliberate simplification: fully valid OOXML, avoids
 * a second dedup/index system on top of the style dedup below, and the
 * file-size cost is irrelevant at this document's scale (one sheet, a
 * few hundred cells).
 *
 * **Style dedup happens at the full-[XLSXStyle] level only, not per
 * font/fill/border sub-component.** Every unique [XLSXStyle] encountered
 * gets its own `<font>`/`<fill>`/`<border>` triple at a shared index,
 * even if two styles happen to share the same font. This is simpler and
 * still fully correct — nothing in the OOXML spec requires the
 * fonts/fills/borders arrays themselves to be deduped, only that each
 * `<xf>` references valid indices into them — and at this document's
 * scale (on the order of tens of distinct styles) the extra bytes from
 * not sub-deduping are negligible. [XLSXStyle] is a Kotlin `data class`
 * (structural `equals`/`hashCode`), the direct counterpart of the
 * source's `Hashable` struct — usable as a `Map` key the same way.
 */
enum class XLSXBorderStyleKind(val rawValue: String) { THIN("thin"), MEDIUM("medium") }

data class XLSXBorderSide(val style: XLSXBorderStyleKind, val argb: String)

data class XLSXBorder(
    val top: XLSXBorderSide? = null,
    val bottom: XLSXBorderSide? = null,
    val left: XLSXBorderSide? = null,
    val right: XLSXBorderSide? = null,
) {
    companion object {
        val NONE = XLSXBorder()
    }
}

data class XLSXFont(
    val name: String = "Arial",
    val size: Double = 10.0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val argb: String = "FF1A1A2E",
)

enum class XLSXHorizontalAlignment(val rawValue: String) { LEFT("left"), RIGHT("right"), CENTER("center") }
enum class XLSXVerticalAlignment(val rawValue: String) { TOP("top"), CENTER("center") }

data class XLSXStyle(
    val font: XLSXFont = XLSXFont(),
    /** `null` means no fill (cell background shows through) — OOXML's own `patternType="none"`. */
    val fillArgb: String? = null,
    val border: XLSXBorder = XLSXBorder.NONE,
    val horizontalAlignment: XLSXHorizontalAlignment = XLSXHorizontalAlignment.LEFT,
    val verticalAlignment: XLSXVerticalAlignment = XLSXVerticalAlignment.TOP,
    val wrapText: Boolean = false,
    /** OOXML indent units (roughly one character width each). */
    val indent: Int = 0,
)

data class XLSXCell(val col: Int, val text: String, val style: XLSXStyle)

data class XLSXMergedRange(val startRow: Int, val endRow: Int, val startCol: Int, val endCol: Int)

class XLSXWorksheet(val name: String) {
    val cellsByRow: MutableMap<Int, MutableList<XLSXCell>> = mutableMapOf()
    val merges: MutableList<XLSXMergedRange> = mutableListOf()
    val columnWidths: MutableMap<Int, Double> = mutableMapOf()
    val rowHeights: MutableMap<Int, Double> = mutableMapOf()
    private var maxRow = 0
    private var maxCol = 0

    fun setCell(row: Int, col: Int, string: String, style: XLSXStyle) {
        cellsByRow.getOrPut(row) { mutableListOf() }.add(XLSXCell(col, string, style))
        maxRow = maxOf(maxRow, row)
        maxCol = maxOf(maxCol, col)
    }

    fun merge(startRow: Int, endRow: Int, startCol: Int, endCol: Int) {
        merges.add(XLSXMergedRange(startRow, endRow, startCol, endCol))
        maxRow = maxOf(maxRow, endRow)
        maxCol = maxOf(maxCol, endCol)
    }

    fun setColumnWidth(col: Int, width: Double) {
        columnWidths[col] = width
    }

    fun setRowHeight(row: Int, height: Double) {
        rowHeights[row] = height
    }

    val dimensionRef: String
        get() = "A1:${XLSXWorkbook.colLetter(maxOf(maxCol, 1))}${maxOf(maxRow, 1)}"
}

sealed class XLSXWorkbookError : Exception() {
    data object EmptyWorkbook : XLSXWorkbookError()
}

class XLSXWorkbook {
    private val worksheets: MutableList<XLSXWorksheet> = mutableListOf()

    fun addWorksheet(name: String): XLSXWorksheet {
        val ws = XLSXWorksheet(name)
        worksheets.add(ws)
        return ws
    }

    fun build(): ByteArray {
        if (worksheets.isEmpty()) throw XLSXWorkbookError.EmptyWorkbook

        val styleOrder = mutableListOf<XLSXStyle>()
        val styleIndex = mutableMapOf<XLSXStyle, Int>()
        fun index(style: XLSXStyle): Int = styleIndex.getOrPut(style) {
            val i = styleOrder.size
            styleOrder.add(style)
            i
        }
        // Pre-scan every cell so styles.xml's dedup table is complete before
        // any sheet XML (which references style indices) is generated.
        for (ws in worksheets) {
            for (cells in ws.cellsByRow.values) {
                for (cell in cells) index(cell.style)
            }
        }

        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zip ->
            addPart(zip, XLSXWorkbook.contentTypesXML(worksheets.size), "[Content_Types].xml")
            addPart(zip, XLSXWorkbook.rootRelsXML(), "_rels/.rels")
            addPart(zip, XLSXWorkbook.coreXML(), "docProps/core.xml")
            addPart(zip, XLSXWorkbook.appXML(worksheets.map { it.name }), "docProps/app.xml")
            addPart(zip, XLSXWorkbook.workbookXML(worksheets.map { it.name }), "xl/workbook.xml")
            addPart(zip, XLSXWorkbook.workbookRelsXML(worksheets.size), "xl/_rels/workbook.xml.rels")
            addPart(zip, XLSXWorkbook.stylesXML(styleOrder), "xl/styles.xml")
            worksheets.forEachIndexed { i, ws ->
                addPart(zip, XLSXWorkbook.sheetXML(ws, styleIndex), "xl/worksheets/sheet${i + 1}.xml")
            }
        }
        return outputStream.toByteArray()
    }

    private fun addPart(zip: ZipOutputStream, xml: String, path: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    companion object {
        // MARK: - Column letter conversion (1 -> A, 27 -> AA, ...)

        fun colLetter(col: Int): String {
            var n = col
            var letters = ""
            while (n > 0) {
                val rem = (n - 1) % 26
                letters = ('A' + rem) + letters
                n = (n - 1) / 26
            }
            return letters
        }

        fun cellRef(row: Int, col: Int): String = "${colLetter(col)}$row"

        // MARK: - XML escaping

        fun escapeXML(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
