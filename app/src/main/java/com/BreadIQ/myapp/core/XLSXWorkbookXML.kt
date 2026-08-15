package com.BreadIQ.myapp.core

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The actual OOXML part bodies [XLSXWorkbook.build] assembles into the
 * zip container. Split from `XLSXWorkbook.kt` purely to keep that file's
 * model/dedup logic separate from this file's string templating — no
 * behavioral split, just organization, matching the source's own
 * `extension XLSXWorkbook { ... }` file split (Kotlin extension
 * functions on [XLSXWorkbook.Companion] are the direct equivalent —
 * callable as `XLSXWorkbook.contentTypesXML(...)` etc. from anywhere,
 * same call-site shape as the source's `static func`s).
 */
private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

// MARK: - [Content_Types].xml

fun XLSXWorkbook.Companion.contentTypesXML(sheetCount: Int): String {
    var overrides = "\n    <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "\n    <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
    for (i in 1..maxOf(sheetCount, 1)) {
        overrides += "\n    <Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
    }
    return XML_HEADER + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>$overrides
    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
    <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""
}

// MARK: - _rels/.rels

fun XLSXWorkbook.Companion.rootRelsXML(): String = XML_HEADER + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

// MARK: - docProps/core.xml, docProps/app.xml

fun XLSXWorkbook.Companion.coreXML(): String {
    // Matches Swift's `ISO8601DateFormatter().string(from: .now)` default
    // output (no fractional seconds, e.g. "2026-08-14T12:34:56Z") —
    // `Instant.toString()` produces exactly that shape once truncated to
    // whole seconds.
    val created = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    return XML_HEADER + """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <dc:creator>BreadIQ</dc:creator>
    <dc:title>Recipe Export</dc:title>
    <dcterms:created xsi:type="dcterms:W3CDTF">$created</dcterms:created>
</cp:coreProperties>"""
}

fun XLSXWorkbook.Companion.appXML(sheetNames: List<String>): String {
    val titles = sheetNames.joinToString("") { "<vt:lpstr>${XLSXWorkbook.escapeXML(it)}</vt:lpstr>" }
    return XML_HEADER + """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
    <Application>BreadIQ</Application>
    <TitlesOfParts>
        <vt:vector size="${sheetNames.size}" baseType="lpstr">$titles</vt:vector>
    </TitlesOfParts>
</Properties>"""
}

// MARK: - xl/workbook.xml, xl/_rels/workbook.xml.rels

fun XLSXWorkbook.Companion.workbookXML(sheetNames: List<String>): String {
    var sheetsXML = ""
    sheetNames.forEachIndexed { i, name ->
        sheetsXML += "\n        <sheet name=\"${XLSXWorkbook.escapeXML(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
    }
    return XML_HEADER + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets>$sheetsXML
    </sheets>
</workbook>"""
}

fun XLSXWorkbook.Companion.workbookRelsXML(sheetCount: Int): String {
    var rels = ""
    for (i in 1..maxOf(sheetCount, 1)) {
        rels += "\n    <Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>"
    }
    val stylesRelId = "rId${maxOf(sheetCount, 1) + 1}"
    return XML_HEADER + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels
    <Relationship Id="$stylesRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
}

// MARK: - xl/styles.xml

/**
 * Reserved slots required by the OOXML spec occupy index 0 (fonts,
 * borders) or 0-1 (fills, since index 1 must be the built-in `gray125`
 * pattern) — every custom style's font/fill/border/xf all share the same
 * offset `i+1`/`i+2`/`i+1`/`i+1` from its position `i` in `styles`, per
 * this file's own header-comment explanation of why sub-component dedup
 * isn't needed at this document's scale. This offset math is
 * load-bearing, not decorative — get it wrong and every cell's style
 * points at the wrong font/fill/border.
 */
fun XLSXWorkbook.Companion.stylesXML(styles: List<XLSXStyle>): String {
    var fonts = "<font><sz val=\"10\"/><name val=\"Arial\"/></font>"
    var fills = "<fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
    var borders = "<border><left/><right/><top/><bottom/><diagonal/></border>"
    var xfs = "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>"

    for (style in styles) {
        fonts += "<font>${if (style.font.bold) "<b/>" else ""}${if (style.font.italic) "<i/>" else ""}" +
            "<sz val=\"${fmtStylesNum(style.font.size)}\"/><color rgb=\"${style.font.argb}\"/><name val=\"${XLSXWorkbook.escapeXML(style.font.name)}\"/></font>"
        val fillArgb = style.fillArgb
        fills += if (fillArgb != null) {
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$fillArgb\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        } else {
            "<fill><patternFill patternType=\"none\"/></fill>"
        }
        borders += "<border>${borderSideXML(style.border.left, "left")}${borderSideXML(style.border.right, "right")}" +
            "${borderSideXML(style.border.top, "top")}${borderSideXML(style.border.bottom, "bottom")}<diagonal/></border>"

        var alignmentAttrs = "horizontal=\"${style.horizontalAlignment.rawValue}\" vertical=\"${style.verticalAlignment.rawValue}\""
        if (style.wrapText) alignmentAttrs += " wrapText=\"1\""
        if (style.indent > 0) alignmentAttrs += " indent=\"${style.indent}\""
        xfs += "<xf numFmtId=\"0\" fontId=\"${xlsxFontIndex(style, styles)}\" fillId=\"${xlsxFillIndex(style, styles)}\" " +
            "borderId=\"${xlsxBorderIndex(style, styles)}\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
            "<alignment $alignmentAttrs/></xf>"
    }

    return XML_HEADER + """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="${styles.size + 1}">$fonts</fonts>
    <fills count="${styles.size + 2}">$fills</fills>
    <borders count="${styles.size + 1}">$borders</borders>
    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
    <cellXfs count="${styles.size + 1}">$xfs</cellXfs>
    <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

/** Index of `style` within the caller's own `styles` list, offset by the reserved default slot(s) — see [stylesXML]'s own doc comment. */
private fun xlsxFontIndex(style: XLSXStyle, styles: List<XLSXStyle>): Int = xlsxIndexOrZero(style, styles) + 1
private fun xlsxFillIndex(style: XLSXStyle, styles: List<XLSXStyle>): Int = xlsxIndexOrZero(style, styles) + 2
private fun xlsxBorderIndex(style: XLSXStyle, styles: List<XLSXStyle>): Int = xlsxIndexOrZero(style, styles) + 1
private fun xlsxIndexOrZero(style: XLSXStyle, styles: List<XLSXStyle>): Int = styles.indexOf(style).let { if (it < 0) 0 else it }

private fun borderSideXML(side: XLSXBorderSide?, tag: String): String {
    if (side == null) return "<$tag/>"
    return "<$tag style=\"${side.style.rawValue}\"><color rgb=\"${side.argb}\"/></$tag>"
}

private fun fmtStylesNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

// MARK: - xl/worksheets/sheetN.xml

fun XLSXWorkbook.Companion.sheetXML(ws: XLSXWorksheet, styleIndex: Map<XLSXStyle, Int>): String {
    var cols = ""
    for ((col, width) in ws.columnWidths.toSortedMap()) {
        cols += "<col min=\"$col\" max=\"$col\" width=\"${fmtStylesNum(width)}\" customWidth=\"1\"/>"
    }

    var sheetData = ""
    for (row in ws.cellsByRow.keys.sorted()) {
        val cells = ws.cellsByRow.getValue(row).sortedBy { it.col }
        var rowAttrs = "r=\"$row\""
        ws.rowHeights[row]?.let { height -> rowAttrs += " ht=\"${fmtStylesNum(height)}\" customHeight=\"1\"" }
        var rowCells = ""
        for (cell in cells) {
            val s = (styleIndex[cell.style] ?: -1) + 1
            rowCells += "<c r=\"${XLSXWorkbook.cellRef(row, cell.col)}\" s=\"$s\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${XLSXWorkbook.escapeXML(cell.text)}</t></is></c>"
        }
        sheetData += "<row $rowAttrs>$rowCells</row>"
    }

    var mergeCellsXML = ""
    if (ws.merges.isNotEmpty()) {
        val refs = ws.merges.joinToString("") { m ->
            "<mergeCell ref=\"${XLSXWorkbook.cellRef(m.startRow, m.startCol)}:${XLSXWorkbook.cellRef(m.endRow, m.endCol)}\"/>"
        }
        mergeCellsXML = "<mergeCells count=\"${ws.merges.size}\">$refs</mergeCells>"
    }

    return XML_HEADER + """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <dimension ref="${ws.dimensionRef}"/>
    <sheetViews><sheetView workbookViewId="0"/></sheetViews>
    <sheetFormatPr defaultRowHeight="15"/>
    <cols>$cols</cols>
    <sheetData>$sheetData</sheetData>
    $mergeCellsXML
</worksheet>"""
}
