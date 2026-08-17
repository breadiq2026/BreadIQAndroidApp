package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.CalculatorImportMapping
import com.BreadIQ.myapp.core.ImportModalFormatting
import com.BreadIQ.myapp.core.ImportReviewFormatting
import com.BreadIQ.myapp.core.IngredientCategory
import com.BreadIQ.myapp.core.StagedImportIngredient
import com.BreadIQ.myapp.core.StagedImportPayload
import com.BreadIQ.myapp.model.BreadStyleCatalog
import com.BreadIQ.myapp.model.BreadStyleDef
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.LoafShapeCatalog
import com.BreadIQ.myapp.model.calculatorFlourTypes
import com.BreadIQ.myapp.model.calculatorYeastTypes
import com.BreadIQ.myapp.model.flourBlendTemplates
import com.BreadIQ.myapp.model.prefermentTypes
import com.BreadIQ.myapp.ui.components.Badge
import com.BreadIQ.myapp.ui.components.BadgeVariant
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `Screens/ImportReviewScreen.swift`'s
 * `ImportReviewOutcome` struct.
 *
 * Everything this screen hands back to [CalculatorViewModel.applyImportReviewOutcome]
 * when the user taps Continue — the fields Cards 0-2 of the normal
 * wizard would otherwise have populated (`IMPORT_REVIEW_SPEC.md` §4's
 * own framing: "this screen is populating the same state Cards 0-2
 * populate today, just through one screen instead of three").
 *
 * [hydration]/[fat]/[salt]/[yeast]/[sweetenerType]/[sweetenerPct]/
 * [milkPercent] are NOT editable anywhere on this screen (per spec §4's
 * own layout — no slider/field for any of them appears there) — they
 * pass straight through from [CalculatorImportMapping]'s already-correct
 * ingredient math. Everything else here reflects a real control on this
 * screen the user can see and change.
 */
data class ImportReviewOutcome(
    val styleValue: String,
    val shapeValue: String,
    val formatNote: String,
    val numLoaves: Double,
    val hydration: Double,
    val fat: Double,
    val salt: Double,
    val yeast: Double,
    val yeastType: String,
    val sweetenerType: String?,
    val sweetenerPct: Double,
    /** See [CalculatorImportMapping.milkPercent]'s own doc comment — `null` means this import had no dairy ingredient at all. */
    val milkPercent: Double?,
    /** See [CalculatorImportMapping.dairyDisplayName]'s own doc comment. */
    val dairyDisplayName: String?,
    val flourBlend: List<FlourBlendEntry>,
    val isHumidityMode: Boolean,
    val relativeHumidity: Double,
    val usePrefermant: Boolean,
    val prefermentType: String,
    val useColdRetard: Boolean,
    val coldRetardHours: Double,
)

/**
 * The dedicated Import Review screen — `IMPORT_REVIEW_SPEC.md`'s
 * centerpiece. Shown in place of Cards 0-2 of the normal calculator
 * wizard whenever a browser-extension import lands (see
 * `CalculatorViewModel.fetchStagedImport`/`applyImportReviewOutcome`),
 * never as a modification of those cards' own copy or layout.
 *
 * **No silent style-forcing** (spec §2's core decision):
 * `mapping.suggestedStyleValue` is only ever a PRE-SELECTED suggestion in
 * the style picker below — the user confirms or overrides it before
 * anything reaches the calculator. Nothing here is written into
 * [CalculatorViewModel]'s real state until [onContinue] fires; every
 * field below is this screen's own **local** state, seeded once from
 * [payload]/[mapping], specifically so an abandoned/discarded review
 * (see [onDiscard]) can't leave partial edits leaked into the
 * calculator's real state.
 *
 * Reuses this app's own shared calculator row controls
 * ([CalcSectionLabel]/[CalcToggleRow]/[CalcStepperRow]/[CalcChipRow]/
 * [CalcSelectMenu]/[CalcInfoBox]) rather than duplicating ~200 lines of
 * working, already-styled control code — the direct analog of the
 * source widening its own private versions of these to internal for
 * exactly this reuse.
 */
@Composable
fun ImportReviewScreen(
    payload: StagedImportPayload,
    mapping: CalculatorImportMapping,
    onContinue: (ImportReviewOutcome) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBreadIQColors.current
    val initialStyle = remember(mapping) { ImportReviewFormatting.initialStyle(mapping) }

    var selectedStyleValue by remember { mutableStateOf(initialStyle.value) }
    var selectedShapeValue by remember { mutableStateOf(initialStyle.defaultShape) }
    var formatNote by remember { mutableStateOf("") }
    var numLoaves by remember { mutableStateOf(1.0) }

    var isHumidityMode by remember { mutableStateOf(false) }
    var relativeHumidity by remember { mutableStateOf(50.0) }
    var usePrefermant by remember { mutableStateOf(false) }
    var prefermentType by remember { mutableStateOf("poolish") }
    var useColdRetard by remember { mutableStateOf(false) }
    var coldRetardHours by remember { mutableStateOf(12.0) }

    var showAdvanced by remember { mutableStateOf(false) }
    var flourBlend by remember { mutableStateOf(initialStyle.defaultFlourBlend ?: listOf(FlourBlendEntry(type = "bread", percent = 100.0))) }
    var yeastType by remember { mutableStateOf("instant") }
    var editableIngredients by remember { mutableStateOf(payload.ingredients) }

    val availableShapes = LoafShapeCatalog.all.filter { it.styles.contains(selectedStyleValue) }
    val selectedShape = availableShapes.firstOrNull { it.value == selectedShapeValue } ?: availableShapes.firstOrNull()
    val prefInfo = prefermentTypes[prefermentType]
    val sourceDomain = ImportReviewFormatting.sourceDomain(payload.sourceURL)
    val confidenceBadge = when (payload.confidence.lowercase()) {
        "high" -> "High Confidence" to BadgeVariant.SUCCESS
        "low" -> "Low Confidence" to BadgeVariant.DESTRUCTIVE
        else -> "Moderate Confidence" to BadgeVariant.ORANGE
    }

    fun selectStyle(style: BreadStyleDef) {
        selectedStyleValue = style.value
        val newShapes = LoafShapeCatalog.all.filter { it.styles.contains(style.value) }
        val defaultShape = newShapes.firstOrNull { it.value == style.defaultShape } ?: newShapes.firstOrNull()
        if (defaultShape != null) selectedShapeValue = defaultShape.value
        flourBlend = style.defaultFlourBlend ?: listOf(FlourBlendEntry(type = "bread", percent = 100.0))
    }

    fun updateFlourType(idx: Int, newType: String) {
        if (idx !in flourBlend.indices) return
        flourBlend = flourBlend.mapIndexed { i, e -> if (i == idx) e.copy(type = newType) else e }
    }

    fun addFlour() {
        if (flourBlend.size >= 5) return
        val used = flourBlend.map { it.type }.toSet()
        val nextType = calculatorFlourTypes.firstOrNull { it.value !in used }?.value ?: "whole_wheat"
        val defaultPct = if (flourBlend.size == 1) 20.0 else 10.0
        val secSum = flourBlend.drop(1).sumOf { it.percent } + defaultPct
        val primary = maxOf(1.0, 100 - secSum)
        val updated = flourBlend.toMutableList()
        updated[0] = updated[0].copy(percent = primary)
        updated.add(FlourBlendEntry(type = nextType, percent = defaultPct))
        flourBlend = updated
    }

    fun removeFlour(idx: Int) {
        if (idx == 0 || flourBlend.size <= 1 || idx !in flourBlend.indices) return
        val updated = flourBlend.toMutableList()
        updated.removeAt(idx)
        val secSum = updated.drop(1).sumOf { it.percent }
        updated[0] = updated[0].copy(percent = maxOf(1.0, 100 - secSum))
        flourBlend = updated
    }

    fun handleContinue() {
        onContinue(
            ImportReviewOutcome(
                styleValue = selectedStyleValue,
                shapeValue = selectedShapeValue,
                formatNote = formatNote.trim(),
                numLoaves = numLoaves,
                hydration = mapping.hydration,
                fat = mapping.fat,
                salt = mapping.salt,
                yeast = mapping.yeast,
                yeastType = yeastType,
                sweetenerType = mapping.sweetenerType,
                sweetenerPct = mapping.sweetenerPct ?: 3.0,
                milkPercent = mapping.milkPercent,
                dairyDisplayName = mapping.dairyDisplayName,
                flourBlend = flourBlend,
                isHumidityMode = isHumidityMode,
                relativeHumidity = relativeHumidity,
                usePrefermant = usePrefermant,
                prefermentType = prefermentType,
                useColdRetard = useColdRetard,
                coldRetardHours = coldRetardHours,
            ),
        )
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ImportReviewHeader(onDiscard = onDiscard)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(payload = payload, sourceDomain = sourceDomain, confidenceLabel = confidenceBadge.first, confidenceVariant = confidenceBadge.second)
            if (payload.flags.isNotEmpty()) FlagsCard(payload.flags)

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcSectionLabel("Dough Style")
                    CalcChipRow(
                        options = BreadStyleCatalog.all.map { CalcChipOption(value = it.value, label = it.label) },
                        selected = selectedStyleValue,
                        onSelect = { v -> BreadStyleCatalog.all.firstOrNull { it.value == v }?.let(::selectStyle) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Choose Your Output", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("Same formula, made your way — pick the shape and quantity you actually want.", fontSize = 12.sp, color = colors.mutedForeground)
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcSectionLabel("Shape / Format")
                    CalcSelectMenu(
                        selectedLabel = selectedShape?.label ?: "Select…",
                        options = availableShapes.map { CalcSelectOption(value = it.value, label = it.label) },
                        onSelect = { v -> selectedShapeValue = v },
                    )
                }
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcSectionLabel("Format Note (optional)")
                    Text(
                        "What the original recipe said — e.g. \"9-inch round cake pan.\" For your own reference only; doesn't affect the formula.",
                        fontSize = 12.sp, color = colors.mutedForeground,
                    )
                    BasicTextField(
                        value = formatNote,
                        onValueChange = { formatNote = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.foreground),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.muted).border(1.dp, colors.border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (formatNote.isEmpty()) Text("e.g. 9-inch round cake pan", fontSize = 14.sp, color = colors.mutedForeground)
                            inner()
                        },
                    )
                }
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcSectionLabel("Batch Size")
                    CalcStepperRow(
                        label = "${batchSizeLabel(selectedStyleValue)} — ${numLoaves.toInt()}",
                        value = numLoaves, onValueChange = { numLoaves = it }, minValue = 1.0, maxValue = 20.0, step = 1.0,
                    )
                }
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcSectionLabel("Humidity Adjustment")
                    CalcToggleRow(
                        label = "💧 Humidity Adjustment", description = "Water weight and proof times adjust automatically to your environment.",
                        value = isHumidityMode, onChange = { isHumidityMode = it },
                    )
                    if (isHumidityMode) {
                        CalcStepperRow(label = "Relative Humidity — ${relativeHumidity.toInt()}%", value = relativeHumidity, onValueChange = { relativeHumidity = it }, minValue = 5.0, maxValue = 100.0, step = 5.0)
                    }
                }
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcSectionLabel("Pre-ferment")
                    CalcToggleRow(label = "Use Pre-ferment", description = "Poolish or biga — adds complexity and extensibility.", value = usePrefermant, onChange = { usePrefermant = it })
                    if (usePrefermant) {
                        CalcSelectMenu(
                            label = "Type", selectedLabel = prefInfo?.label ?: "Poolish",
                            options = listOfNotNull(
                                prefermentTypes["poolish"]?.let { CalcSelectOption("poolish", "Poolish", it.hydrationLabel) },
                                prefermentTypes["biga"]?.let { CalcSelectOption("biga", "Biga", it.hydrationLabel) },
                            ),
                            onSelect = { v -> prefermentType = v },
                        )
                        prefInfo?.let { CalcInfoBox(it.description, variant = CalcInfoVariant.NEUTRAL) }
                    }
                }
            }

            Card(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcSectionLabel("Cold Ferment")
                    CalcToggleRow(label = "❄ Cold Ferment", description = "Ferment shaped dough in the refrigerator for 8–24 hours for deeper flavor.", value = useColdRetard, onChange = { useColdRetard = it })
                    if (useColdRetard) {
                        CalcStepperRow(
                            label = "Cold Ferment Duration — ${coldRetardHours.toInt()}h", value = coldRetardHours, onValueChange = { coldRetardHours = it },
                            minValue = 1.0, maxValue = 24.0, step = 1.0, note = "12–16h is the sweet spot for most bakers.",
                        )
                    }
                }
            }

            // Advanced Options — collapsed by default, spec §2's progressive disclosure.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 4.dp),
                ) {
                    Text("Advanced Options", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, modifier = Modifier.weight(1f))
                    Icon(
                        if (showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp),
                    )
                }
                if (showAdvanced) {
                    Card(padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CalcSectionLabel("Flour Blend")
                            CalcChipRow(
                                options = flourBlendTemplates.map { CalcChipOption(value = it.label, label = it.label) },
                                selected = flourBlendTemplates.firstOrNull { blendMatches(flourBlend, it.blend) }?.label ?: "",
                                onSelect = { label -> flourBlendTemplates.firstOrNull { it.label == label }?.let { flourBlend = it.blend } },
                            )
                            flourBlend.forEachIndexed { idx, entry ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CalcSelectMenu(
                                        selectedLabel = calculatorFlourTypes.firstOrNull { it.value == entry.type }?.label ?: entry.type,
                                        options = calculatorFlourTypes.map { CalcSelectOption(it.value, it.label) },
                                        onSelect = { v -> updateFlourType(idx, v) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${entry.percent.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground, modifier = Modifier.width(44.dp))
                                    if (idx != 0) {
                                        Icon(
                                            Icons.Filled.Cancel, contentDescription = "Remove", tint = colors.mutedForeground,
                                            modifier = Modifier.size(20.dp).clickable { removeFlour(idx) },
                                        )
                                    }
                                }
                            }
                            if (flourBlend.size < 5) {
                                Text("+ Add Flour", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.clickable(onClick = ::addFlour))
                            }
                        }
                    }
                    Card(padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CalcSectionLabel("Yeast Type")
                            CalcSelectMenu(
                                selectedLabel = calculatorYeastTypes.firstOrNull { it.value == yeastType }?.label ?: "Instant",
                                options = calculatorYeastTypes.map { CalcSelectOption(it.value, it.label) },
                                onSelect = { v -> yeastType = v },
                            )
                        }
                    }
                    // Deliberately does NOT recompute CalculatorImportMapping's
                    // output (hydration/fat/salt/yeast/style) when a row is
                    // edited here — a real scope boundary, not an oversight,
                    // matching the source exactly: these overrides are for
                    // the user's own review/correction, same as this
                    // screen's other Advanced Options.
                    Card(padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CalcSectionLabel("Ingredients Detected")
                            editableIngredients.forEachIndexed { idx, ingredient ->
                                IngredientOverrideRow(
                                    ingredient = ingredient,
                                    onNameChange = { newName -> editableIngredients = editableIngredients.mapIndexed { i, ing -> if (i == idx) ing.copy(name = newName) else ing } },
                                    onCategoryChange = { newCategory -> editableIngredients = editableIngredients.mapIndexed { i, ing -> if (i == idx) ing.copy(category = newCategory) else ing } },
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            BreadIQButton(label = "Continue", variant = BreadIQButtonVariant.PRIMARY, fullWidth = true, onClick = ::handleContinue)
        }
    }
}

private fun batchSizeLabel(styleValue: String): String = when {
    styleValue.startsWith("pizza_") -> "Pizzas"
    styleValue == "focaccia" -> "Pans"
    else -> "Loaves / Units"
}

private fun blendMatches(blend: List<FlourBlendEntry>, template: List<FlourBlendEntry>): Boolean =
    blend.size == template.size && blend.zip(template).all { (a, b) -> a.type == b.type && a.percent == b.percent }

@Composable
private fun ImportReviewHeader(onDiscard: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row {
                    Text("Bread", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text("IQ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Start from Scratch", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground,
                    modifier = Modifier.clickable(onClick = onDiscard),
                )
            }
            Text("Review Your Import", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun SummaryCard(payload: StagedImportPayload, sourceDomain: String?, confidenceLabel: String, confidenceVariant: BadgeVariant) {
    val colors = LocalBreadIQColors.current
    Card(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                payload.recipeName?.takeIf { it.isNotEmpty() } ?: "Imported Recipe",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.foreground,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (sourceDomain != null) {
                    Text(sourceDomain, fontSize = 12.sp, color = colors.mutedForeground, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Badge(label = confidenceLabel, variant = confidenceVariant)
            }
        }
    }
}

@Composable
private fun FlagsCard(flags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        flags.forEach { flag ->
            CalcInfoBox("⚠ $flag", variant = CalcInfoVariant.WARNING)
        }
    }
}

@Composable
private fun IngredientOverrideRow(
    ingredient: StagedImportIngredient,
    onNameChange: (String) -> Unit,
    onCategoryChange: (IngredientCategory) -> Unit,
) {
    val colors = LocalBreadIQColors.current
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = ingredient.name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = colors.foreground),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(colors.muted).border(1.dp, colors.border, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Box {
            Text(
                ImportModalFormatting.displayLabel(ingredient.category, ingredient.name),
                fontSize = 12.sp, color = colors.foreground,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .clickable { categoryMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                IngredientCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(ImportModalFormatting.displayLabel(category, ingredient.name)) },
                        onClick = { onCategoryChange(category); categoryMenuExpanded = false },
                    )
                }
            }
        }
        Text(
            "${ingredient.grams.toInt()}g", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = colorFromHex(ImportModalFormatting.categoryColorHex(ingredient.category)),
            modifier = Modifier.width(50.dp),
        )
    }
}

/** Same shape as `ImportScreen.kt`'s own private `colorFromHex` — file-private in Kotlin, so re-declared here rather than exported across files for one call site. */
private fun colorFromHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
