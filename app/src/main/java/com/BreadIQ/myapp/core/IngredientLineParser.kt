package com.BreadIQ.myapp.core

import kotlin.math.abs

data class CleanedIngredientName(val name: String, val flag: String? = null)

data class ParsedIngredientLine(val name: String, val quantityStr: String, val unit: String)

/**
 * Port of `ImportModal.tsx`'s regex-heavy OCR/URL-text → structured-row
 * pipeline (`breadiq-mobile/components/ImportModal.tsx`, lines 215-306
 * for `parseFraction`/`filterIngredientLines`/`cleanIngredientName`/
 * `parseIngredientLine`, plus lines 396-454 for the OCR-only
 * `extractIngredientLines`). Flagged as the single highest-value logic
 * to get exactly right — this is real OCR output and pasted recipe
 * text, not clean structured data.
 *
 * **Mobile/web drift check**: unlike [IngredientClassifier] and
 * [IngredientDensityConverter], this one found NO drift. There's a
 * second real copy — `bread-lab/src/pages/import.tsx` (the web app, not
 * the `lib/ingredient-densities` shared module checked for the other two
 * files) — and `parseFraction`/`filterIngredientLines`/
 * `cleanIngredientName`/`parseIngredientLine` are byte-for-byte identical
 * between the two files, diffed line by line. `extractIngredientLines`
 * has no web counterpart at all — the web app has no camera/OCR import
 * path, only URL import, so there was nothing to diff it against.
 *
 * **Kotlin's [Regex] replaces the source's `NSRegularExpression`
 * wrapper helpers directly** — no behavior change, just no separate
 * `regexMatches`/`regexReplace`/`firstMatch` utility functions needed,
 * since [Regex.containsMatchIn]/[Regex.replace]/[Regex.find] already
 * provide the same ergonomics natively.
 */
object IngredientLineParser {

    // MARK: - parseFraction() — lines 215-218

    /**
     * Splits on `/` and divides the first two parts. Matches JS's exact
     * (slightly surprising) semantics rather than a "sensible" fraction
     * parser: a string with no `/` at all returns `0`, not the parsed
     * number (`b` is `undefined` → falsy in the source's `b ? a/b : 0`);
     * a valid non-zero denominator with an unparseable numerator returns
     * `NaN` (JS's `Number("abc")` is `NaN`, and only `b`'s truthiness
     * gates the ternary, not `a`'s); a zero or unparseable denominator
     * returns `0`. In practice [parseIngredientLine] only ever calls this
     * with regex-guaranteed `\d+/\d+` captures, so these edge cases never
     * actually surface through the normal pipeline — but this function is
     * tested standalone, so they're preserved rather than "cleaned up."
     */
    fun parseFraction(s: String): Double {
        val parts = s.split("/").map { it.toDoubleOrNull() }
        if (parts.size < 2) return 0.0
        val b = parts[1] ?: return 0.0
        if (b == 0.0) return 0.0
        val a = parts[0] ?: return Double.NaN
        return a / b
    }

    // MARK: - Shared line-filter primitives

    private val ocrBreadKeywords = listOf(
        "flour", "water", "yeast", "salt", "butter", "oil", "sugar", "honey", "milk",
        "cream", "egg", "eggs", "malt", "rye", "wheat", "semolina", "spelt",
        "shortening", "lard", "molasses", "oats", "bran", "gluten",
    )

    /**
     * `OCR_INGREDIENT_START` / the leading `[\d½¼¾⅓⅔⅛⅜⅝⅞]` check reused
     * by [filterIngredientLines]. `\d` in the source regex matches ASCII
     * digits only, so this checks the char is `'0'..'9'` explicitly
     * rather than Kotlin's broader `Char.isDigit()` (which also accepts
     * non-ASCII numerals JS's `\d` would not).
     */
    private fun startsWithQuantity(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        if (first in '0'..'9') return true
        return "½¼¾⅓⅔⅛⅜⅝⅞".contains(first)
    }

    private val tempOrTimeDegreePattern = Regex("°[CF]")
    private val tempOrTimeUnitPattern = Regex("\\b(?:minutes?|hours?|mins?|hrs?)\\b", RegexOption.IGNORE_CASE)

    private fun hasTempOrTime(line: String): Boolean {
        if (tempOrTimeDegreePattern.containsMatchIn(line)) return true
        return tempOrTimeUnitPattern.containsMatchIn(line)
    }

    private val endsWithColonPattern = Regex(":\\s*$")
    private fun endsWithColon(line: String): Boolean = endsWithColonPattern.containsMatchIn(line)

    private val numberedStepPattern = Regex("^\\d+[.)]\\s")
    private fun isNumberedStep(line: String): Boolean = numberedStepPattern.containsMatchIn(line)

    // MARK: - filterIngredientLines() — lines 220-237

    /**
     * Client-side safety filter for URL-imported lines. The server
     * handles the same rules for its text-scraping path, but schema.org
     * lines bypass server filtering, so the same rules apply client-side
     * too (source's own comment, preserved).
     */
    fun filterIngredientLines(lines: List<String>): List<String> = lines.filter { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@filter false
        if (endsWithColon(line)) return@filter false
        if (isNumberedStep(line)) return@filter false
        val startsNum = startsWithQuantity(line)
        if (hasTempOrTime(line) && !startsNum) return@filter false
        true
    }

    // MARK: - extractIngredientLines() — lines 396-454 (OCR-only)

    fun extractIngredientLines(ocrText: String): List<String> {
        // Pass 1: filter candidate lines.
        val candidates = mutableListOf<String>()
        outer@ for (raw in ocrText.split("\n")) {
            val line = raw.trim()
            if (line.length < 3 || line.length > 200) continue
            if (endsWithColon(line)) continue
            if (isNumberedStep(line)) continue

            val lower = line.lowercase()
            val startsNum = startsWithQuantity(line)
            val hasKw = ocrBreadKeywords.any { lower.contains(it) }
            val hasNum = line.any { it in '0'..'9' }

            if (hasTempOrTime(line) && !startsNum) continue

            if (startsNum || (hasKw && hasNum)) {
                candidates.add(line)
            }
            if (candidates.size >= 40) break@outer
        }

        // Pass 2: merge OCR continuation lines. The source's own comment
        // example ("Line 1: '2 cups'... Line 2: 'bread flour'") is
        // misleading, confirmed by running the actual JS directly: a
        // continuation line with NO digit at all (like plain "bread
        // flour") never survives Pass 1's candidate filter in the first
        // place — `hasKw && hasNum` requires a digit somewhere in the
        // line for a non-quantity-leading line to become a candidate at
        // all, so there's nothing in `candidates` for this loop to merge
        // onto. The merge only actually fires when the continuation line
        // has some incidental digit too (e.g. "bread flour 12oz" — a
        // price, package size, or OCR-noise digit). Ported faithfully
        // either way; not "fixed," since this is exactly what the
        // shipped app does.
        val merged = mutableListOf<String>()
        var i = 0
        while (i < candidates.size) {
            val line = candidates[i]
            val lower = line.lowercase()
            val startsNum = startsWithQuantity(line)
            val hasKw = ocrBreadKeywords.any { lower.contains(it) }
            val next = if (i + 1 < candidates.size) candidates[i + 1] else null

            if (startsNum && !hasKw && next != null) {
                val nextLower = next.lowercase()
                val nextStartsNum = startsWithQuantity(next)
                val nextHasKw = ocrBreadKeywords.any { nextLower.contains(it) }
                if (nextHasKw && !nextStartsNum) {
                    merged.add("$line $next")
                    i += 2
                    continue
                }
            }
            merged.add(line)
            i += 1
        }

        return merged.take(30)
    }

    // MARK: - cleanIngredientName() — lines 239-256

    private const val STRIP_EDGES_CHAR_CLASS = "[\\s*•·\\-–—,]"
    private val doubleParenPattern = Regex("\\(\\([^)]*\\)\\)")
    private val stripEdgesPattern = Regex("^$STRIP_EDGES_CHAR_CLASS+|$STRIP_EDGES_CHAR_CLASS+$")
    private val parentheticalPattern = Regex("\\s*\\([^)]*\\)")
    private val trailingCommaPattern = Regex(",.*$")
    private val orAlternativePattern = Regex(
        "^([a-zA-Z][a-zA-Z\\s-]*?)\\s+or\\s+([a-zA-Z][a-zA-Z\\s-]*?)(?:\\s*[,(]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun cleanIngredientName(raw: String): CleanedIngredientName {
        var s = raw.trim()
        s = doubleParenPattern.replace(s, "").trim()
        s = stripEdgesPattern.replace(s, "").trim()
        s = parentheticalPattern.replace(s, "").trim()
        s = stripEdgesPattern.replace(s, "").trim()
        // Fix 3: strip trailing modifiers after the first comma —
        // "bread flour, sifted" → "bread flour".
        s = trailingCommaPattern.replace(s, "").trim()

        val m = orAlternativePattern.find(s)
        if (m != null) {
            val first = m.groupValues[1].trim()
            val second = m.groupValues[2].trim()
            if (first.length > 1 && second.length > 1) {
                return CleanedIngredientName(
                    name = first,
                    flag = "We chose \"$first\" — tap to change if your recipe uses \"$second\"",
                )
            }
        }

        return CleanedIngredientName(name = if (s.isEmpty()) raw.trim() else s)
    }

    // MARK: - parseIngredientLine() — lines 258-305

    /**
     * Extended beyond the source's own `UNITS` list, per direct
     * instruction — no source precedent for any of the additions below
     * (confirmed identical to this port's original list, byte for byte,
     * before this extension).
     *
     * **Deliberately excludes bare `"t"`/`"T"`** (teaspoon/tablespoon) —
     * matching only ever happens after lowercasing ([normUnit] below, and
     * [IngredientDensityConverter.normalizeUnit], both mirroring the
     * source's own `u.toLowerCase()`), so `"T"` and `"t"` are literally
     * the same string by the time any lookup happens. There's no way to
     * keep them as distinct abbreviations without introducing
     * case-sensitive matching just for these two — and doing that would
     * be actively worse than not recognizing them at all: a scanned
     * recipe with OCR-confused case would silently produce a wrong 3x
     * measurement (teaspoon read as tablespoon or vice versa) instead of
     * falling through to the existing "no unit matched, ask the user"
     * path. `"tsp"` and the multi-letter tablespoon abbreviations below
     * are unambiguous and safe; bare single-letter `t`/`T` are not.
     *
     * `"c"` was already expected by
     * [IngredientDensityConverter.normalizeUnit]'s own cup-synonym list
     * but missing here — a real, pre-existing gap (not something this
     * extension introduced): `"1 c flour"` silently failed to parse its
     * unit at all before this fix, falling back to the category default
     * instead. Fixed alongside this work since it's the same class of
     * gap.
     */
    private val recognizedUnits: Set<String> = setOf(
        "cups", "cup", "c", "tablespoons", "tablespoon", "tbsp", "tbl", "tbls", "tblsp",
        "teaspoons", "teaspoon", "tsp",
        "pounds", "pound", "lbs", "lb", "#", "ounces", "ounce", "oz", "grams", "gram", "gr", "g",
        "kilograms", "kilogram", "kilo", "kg",
        "ml", "milliliter", "milliliters", "stick", "sticks", "package", "packages", "pkg",
        "envelope", "envelopes", "each", "ea",
    )

    private fun normUnit(u: String): String {
        var lower = u.lowercase()
        if (lower.endsWith(".")) lower = lower.dropLast(1)
        return lower
    }

    /**
     * Matches JS's `String(Math.round(n * 1000) / 1000)` — round to 3
     * decimal places, then stringify without a trailing `.0` for whole
     * values (same underlying JS-number-to-string quirk
     * `ProofStageNarrator.jsNumber` handles at 1-decimal precision for
     * `coldHours`; reimplemented locally at 3-decimal precision rather
     * than reaching into an unrelated file for a one-line utility). The
     * `abs(rounded) < 1e15` guard is a native-only crash fix, not present
     * in the source — JS's number-to-string conversion never traps
     * regardless of magnitude, but Kotlin's `Long` conversion could for
     * an extreme value. Falls back to `rounded.toString()`
     * (never-crashing) for magnitudes no real parsed ingredient quantity
     * would ever reach.
     */
    private fun round3AndFormat(n: Double): String {
        val rounded = (n * 1000).swiftRounded() / 1000
        return if (rounded % 1.0 == 0.0 && abs(rounded) < 1e15) rounded.toLong().toString() else rounded.toString()
    }

    private val fractionSlashVariantsPattern = Regex("[⁄∕／]")
    private val digitFractionAdjacentPattern = Regex("(\\d)([½¼¾⅓⅔⅛⅜⅝⅞])")
    private val spacedSlashPattern = Regex("(\\d+)\\s*/\\s*(\\d+)")
    private val hyphenMixedNumberPattern = Regex("(\\d)\\s*-\\s*(\\d+/\\d+)")
    private val fractionUnitNoSpacePattern = Regex("(\\d+/\\d+)([a-zA-Z])")
    private val parentheticalWithDigitPattern = Regex("\\s*\\([^)]*\\d[^)]*\\)")

    private val patternMixedNumberUnit = Regex("^(\\d+(?:\\.\\d+)?)\\s+(\\d+/\\d+)\\s+([a-zA-Z.#]+)\\s+(.+)$")
    private val patternFractionUnit = Regex("^(\\d+/\\d+)\\s+([a-zA-Z.#]+)\\s+(.+)$")
    private val patternWholeNumberUnit = Regex("^(\\d+(?:\\.\\d+)?)\\s+([a-zA-Z.#]+)\\s+(.+)$")
    private val patternCompactUnit = Regex("^(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z.#]+)\\s+(.+)$")
    private val patternNumberOnly = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)$")

    fun parseIngredientLine(raw: String): ParsedIngredientLine {
        var s = raw.trim()
        // Normalize unicode fraction-slash variants to standard solidus.
        s = fractionSlashVariantsPattern.replace(s, "/")
        // Insert a space between an adjacent digit and a unicode vulgar fraction.
        s = digitFractionAdjacentPattern.replace(s, "$1 $2")
        s = s.replace("½", "1/2").replace("¼", "1/4").replace("¾", "3/4")
            .replace("⅓", "1/3").replace("⅔", "2/3")
            .replace("⅛", "1/8").replace("⅜", "3/8").replace("⅝", "5/8").replace("⅞", "7/8")
        // Fix 1: normalize spaces around a slash — OCR sometimes produces "1 / 2".
        s = spacedSlashPattern.replace(s, "$1/$2")
        // "5-1/2" → "5 1/2" (hyphen-joined mixed number).
        s = hyphenMixedNumberPattern.replace(s, "$1 $2")
        // "1/2cups" → "1/2 cups" (missing space between fraction and unit).
        s = fractionUnitNoSpacePattern.replace(s, "$1 $2")
        // Fix 2: strip parenthetical alt-measurements containing digits
        // before unit matching — "1 cup (240ml) flour" → "1 cup flour".
        s = parentheticalWithDigitPattern.replace(s, "")
        s = s.trim()

        // The unit-token character classes below (`[a-zA-Z.#]+`) include
        // `#` (the pound symbol) — added per direct instruction alongside
        // [recognizedUnits]. `#` isn't a letter, so it was never captured
        // as part of a unit at all before this change, regardless of what
        // [recognizedUnits] contained; the character classes themselves
        // needed widening, not just the recognized-unit set.
        // "2 1/2 cups flour"
        patternMixedNumberUnit.find(s)?.let { m ->
            val (g1, g2, g3, g4) = m.destructured
            if (recognizedUnits.contains(normUnit(g3))) {
                val qty = (g1.toDoubleOrNull() ?: 0.0) + parseFraction(g2)
                return ParsedIngredientLine(name = g4.trim(), quantityStr = round3AndFormat(qty), unit = normUnit(g3))
            }
        }
        // "1/2 cup flour"
        patternFractionUnit.find(s)?.let { m ->
            val (g1, g2, g3) = m.destructured
            if (recognizedUnits.contains(normUnit(g2))) {
                return ParsedIngredientLine(name = g3.trim(), quantityStr = round3AndFormat(parseFraction(g1)), unit = normUnit(g2))
            }
        }
        // "2 cups flour" or "400 g flour"
        patternWholeNumberUnit.find(s)?.let { m ->
            val (g1, g2, g3) = m.destructured
            if (recognizedUnits.contains(normUnit(g2))) {
                return ParsedIngredientLine(name = g3.trim(), quantityStr = g1, unit = normUnit(g2))
            }
        }
        // "400g flour" (compact — no required space) / "5# flour" (compact
        // pound-symbol form) / "1tbsp. butter" (compact form with a
        // trailing abbreviation dot).
        //
        // **Fixes a real gap, not just ported one**: this pattern's unit
        // charset used to exclude "." entirely, so a compact/no-space
        // dotted abbreviation like "1tbsp. butter" matched NONE of the 5
        // patterns at all — pattern 3 (which does allow dots) requires
        // real whitespace between quantity and unit, and pattern 5 (bare
        // number, any unit) also requires whitespace right after the
        // digit. The whole line fell through to the final fallback with
        // no quantity or unit extracted. Confirmed this is a genuine bug
        // in the real source too (traced by hand, not a porting
        // mistake) — previously ported faithfully and pinned with a
        // regression test; now fixed here per direct instruction rather
        // than left as a parity bug, by widening the charset to "."
        // (matching pattern 3) and switching to [normUnit] (which strips
        // the trailing dot) for both the recognized-unit check and the
        // returned unit, same as patterns 1-3 already do.
        patternCompactUnit.find(s)?.let { m ->
            val (g1, g2, g3) = m.destructured
            if (recognizedUnits.contains(normUnit(g2))) {
                return ParsedIngredientLine(name = g3.trim(), quantityStr = g1, unit = normUnit(g2))
            }
        }
        // Number + ingredient, no matched unit — empty unit so
        // normalizeUnit() falls to .count and the category default (e.g.
        // "count" for eggs = 50g each) applies downstream.
        patternNumberOnly.find(s)?.let { m ->
            val (g1, g2) = m.destructured
            return ParsedIngredientLine(name = g2.trim(), quantityStr = g1, unit = "")
        }
        return ParsedIngredientLine(name = s, quantityStr = "", unit = "g")
    }
}
