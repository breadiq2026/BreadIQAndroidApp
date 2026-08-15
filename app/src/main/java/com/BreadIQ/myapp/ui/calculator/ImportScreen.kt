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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.ImportAnalysisResult
import com.BreadIQ.myapp.core.ImportFermentationType
import com.BreadIQ.myapp.core.ImportModalFormatting
import com.BreadIQ.myapp.core.ImportPreFerment
import com.BreadIQ.myapp.core.RecipeScanSource
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.RecipeScannerCameraOverlay
import com.BreadIQ.myapp.ui.components.rememberRecipeScanner
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.ImportRow
import com.BreadIQ.myapp.viewmodel.ImportUiState
import com.BreadIQ.myapp.viewmodel.ImportViewModel
import com.BreadIQ.myapp.viewmodel.ImportViewModelFactory
import com.BreadIQ.myapp.viewmodel.canContinueFromStep1

private val ImportAccent = Color(0xFF1B3A8C)

/**
 * Ported from the iOS app's `Screens/ImportModal.swift` +
 * `Core/ImportModalFormatting.swift` — the 3-step ingredients →
 * environment → results wizard, presented from the Calculator tab's
 * "Import" button (now real — see `CalculatorScreen.kt`'s own doc
 * comment, updated alongside this).
 *
 * **Genuinely self-contained, confirmed directly against the source**:
 * [onClose] is the only callback this screen takes. There is no
 * "Apply to Calculator" action anywhere — Step 3 displays the computed
 * [ImportAnalysisResult] entirely within the screen itself, matching
 * `ImportModal.swift`'s own `ImportModal(onClose: { ... })` call site
 * exactly (no result callback at all). `CalculatorImportMapping.kt`
 * (ported in the previous session) is NOT used here — it belongs to the
 * separate Safari/Chrome-extension staged-import deep-link flow, which
 * has no Android infrastructure yet (see `PORTING_PLAN.md`).
 *
 * Presented as a real Compose Navigation route
 * ([com.BreadIQ.myapp.navigation.BreadIQRoutes.IMPORT]), matching this
 * codebase's established convention for every other iOS `.sheet`
 * (`NutritionAnalysisScreen`/`AutolyseGuidanceScreen`/`ScheduleScreen`),
 * not a `Dialog`/`ModalBottomSheet`.
 *
 * **The camera/library scan trigger lives here, in the Composable
 * layer** ([rememberRecipeScanner], [RecipeScannerCameraOverlay]) —
 * [ImportViewModel] only consumes an already-resolved
 * `RecipeScanOutcome` via `handleScanOutcome`, matching the
 * Context/Activity boundary every other camera/picker-needing feature
 * in this app already established (calendar/notification permissions,
 * `RecipeScanCapture.kt` itself).
 */
@Composable
fun ImportScreen(
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModelFactory(LocalContext.current)),
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    var showScanSourceDialog by remember { mutableStateOf(false) }

    val scanner = rememberRecipeScanner(onResult = { outcome -> viewModel.handleScanOutcome(outcome) })

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            ImportHeader(step = state.step, onClose = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (state.step) {
                    1 -> Step1Ingredients(state, viewModel, onScanClick = { showScanSourceDialog = true })
                    2 -> Step2Environment(state, viewModel)
                    3 -> state.result?.let { Step3Formula(it, state.env.recipeName, onBack = { viewModel.goToStep(2) }) }
                }
            }
        }
        RecipeScannerCameraOverlay(scanner, modifier = Modifier.fillMaxSize())
    }

    if (showScanSourceDialog) {
        ScanSourceDialog(
            onDismiss = { showScanSourceDialog = false },
            onTakePhoto = {
                showScanSourceDialog = false
                viewModel.beginScan()
                scanner.launch(RecipeScanSource.CAMERA)
            },
            onChooseLibrary = {
                showScanSourceDialog = false
                viewModel.beginScan()
                scanner.launch(RecipeScanSource.LIBRARY)
            },
        )
    }
}

// MARK: - Header

@Composable
private fun ImportHeader(step: Int, onClose: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val stepLabels = listOf("Ingredients", "Environment", "Formula")
    Column(modifier = Modifier.fillMaxWidth().background(colors.card)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = ImportAccent, modifier = Modifier.size(16.dp))
                    Text("Import Recipe", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                }
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.mutedForeground, modifier = Modifier.size(18.dp).clickable(onClick = onClose))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stepLabels.forEachIndexed { i, label ->
                    val n = i + 1
                    Text(
                        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (n >= step) Color.White else Color(0xFFD1D5DB),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (n == step) ImportAccent else if (n < step) Color(0xFF6B7280) else colors.muted)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

// MARK: - Step 1: Ingredients

@Composable
private fun Step1Ingredients(state: ImportUiState, viewModel: ImportViewModel, onScanClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val hasAnyName = state.rows.any { it.name.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enter your ingredients", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text(
            "Type each ingredient name — BreadIQ classifies it automatically. Enter quantity as written in your recipe.",
            fontSize = 13.sp, color = colors.mutedForeground,
        )

        if (state.ocrMode && hasAnyName) {
            InfoBanner(
                "📷 Scan results — review before continuing",
                "OCR isn't perfect, especially with handwritten recipes or low-contrast images. Check each ingredient name, quantity, and unit — then tap \"Review & Environment\" when you're ready.",
                background = Color(0xFFEFF6FF), border = Color(0xFFBFDBFE), text = Color(0xFF1E40AF),
            )
        }

        state.rows.forEach { row ->
            IngredientRowCard(
                row = row, canRemove = state.rows.size > 1,
                onNameChange = { viewModel.updateRowName(row.id, it) },
                onNameBlur = { viewModel.handleNameBlur(row.id) },
                onQtyChange = { viewModel.updateRowQuantity(row.id, it) },
                onUnitChange = { viewModel.updateRowUnit(row.id, it) },
                onRemove = { viewModel.removeRow(row.id) },
            )
        }

        DashedAddRow(onClick = viewModel::addRow)

        DividerLabel("OR IMPORT FROM URL")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SimpleTextField(
                value = state.urlStr, onValueChange = viewModel::updateUrlStr, placeholder = "https://www.kingarthurbaking.com/recipes/…",
                enabled = !state.urlLoading, keyboardType = KeyboardType.Uri, modifier = Modifier.weight(1f),
            )
            val urlEmpty = state.urlStr.trim().isEmpty()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .widthIn(min = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.urlLoading && !urlEmpty, onClick = viewModel::fetchURL)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (state.urlLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ImportAccent, strokeWidth = 2.dp)
                } else {
                    Text("Fetch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (urlEmpty) colors.mutedForeground else ImportAccent)
                }
            }
        }

        if (state.urlError.isNotEmpty()) WarningBox(state.urlError)
        if (state.urlConfidence == "low" && state.urlError.isEmpty() && hasAnyName) {
            WarningBox("Page didn't use standard recipe markup — ingredients parsed from page text. Review each one carefully.")
        }
        if (state.urlConfidence == "high" && state.urlError.isEmpty() && hasAnyName) {
            SuccessBox("Recipe imported — review the list above, then continue.")
        }

        DividerLabel("OR SCAN FROM CAMERA")

        Row(
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ImportAccent, RoundedCornerShape(8.dp))
                .clickable(enabled = !state.ocrLoading, onClick = onScanClick)
                .padding(vertical = 13.dp),
        ) {
            if (state.ocrLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ImportAccent, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = ImportAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan Recipe", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ImportAccent)
            }
        }

        Text(
            "Photograph a cookbook page, printed recipe, or handwritten card", fontSize = 11.sp, color = colors.mutedForeground,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        if (state.ocrError.isNotEmpty()) WarningBox(state.ocrError)

        if (!state.canContinueFromStep1 && hasAnyName) WarningBox("Add at least one flour to continue.")

        BreadIQButton(
            label = "Review & Environment →", onClick = { viewModel.goToStep(2) },
            variant = BreadIQButtonVariant.PRIMARY, disabled = !state.canContinueFromStep1, fullWidth = true,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun IngredientRowCard(
    row: ImportRow,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onNameBlur: () -> Unit,
    onQtyChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimpleTextField(value = row.name, onValueChange = onNameChange, placeholder = "Ingredient", onBlur = onNameBlur, modifier = Modifier.weight(1f))
            if (canRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = colors.mutedForeground, modifier = Modifier.size(14.dp).clickable(onClick = onRemove))
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimpleTextField(value = row.quantityStr, onValueChange = onQtyChange, placeholder = "Qty", keyboardType = KeyboardType.Decimal, modifier = Modifier.width(80.dp))
            SimpleTextField(value = row.unit, onValueChange = onUnitChange, placeholder = "unit", modifier = Modifier.width(80.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                if (row.name.isNotEmpty() && row.grams > 0) {
                    val gramsText = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.mutedForeground)) {
                            append("→ ${ImportModalFormatting.formatted(ImportModalFormatting.round1(row.grams))}g ")
                        }
                        withStyle(SpanStyle(color = colorFromHex(ImportModalFormatting.categoryColorHex(row.category)))) {
                            append(ImportModalFormatting.displayLabel(row.category, row.name))
                        }
                    }
                    Text(gramsText, fontSize = 12.sp, textAlign = TextAlign.End)
                }
                row.flagged?.let { flagged ->
                    Text("⚠ $flagged", fontSize = 11.sp, color = Color(0xFFD97706), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun DashedAddRow(onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(colors.border, cornerRadius = 8.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add ingredient", fontSize = 13.sp, color = colors.mutedForeground)
    }
}

// MARK: - Step 2: Environment

@Composable
private fun Step2Environment(state: ImportUiState, viewModel: ImportViewModel) {
    val colors = LocalBreadIQColors.current
    val unitSymbol = state.temperatureUnit.symbol

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Review & Environment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)

        SectionLabel("Ingredients")
        state.rows.filter { it.name.isNotEmpty() && it.grams > 0 }.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(row.name, fontSize = 13.sp, color = colors.foreground, modifier = Modifier.weight(1f))
                Text("${ImportModalFormatting.formatted(ImportModalFormatting.round1(row.grams))}g", fontSize = 13.sp, color = colors.mutedForeground)
            }
        }

        SectionLabel("Recipe Name (optional)")
        SimpleTextField(value = state.env.recipeName, onValueChange = viewModel::updateRecipeName, placeholder = "e.g. Grandma's White Bread", modifier = Modifier.fillMaxWidth())

        SectionLabel("Fermentation Environment")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField("Ambient temp ($unitSymbol)", state.env.ambientTemp, viewModel::updateAmbientTemp, modifier = Modifier.weight(1f))
            LabeledField("Water temp ($unitSymbol)", state.env.waterTemp, viewModel::updateWaterTemp, modifier = Modifier.weight(1f))
        }

        Text("Fermentation type", fontSize = 12.sp, color = colors.mutedForeground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleButton("Same-day", state.env.fermentationType == ImportFermentationType.STRAIGHT, Modifier.weight(1f)) {
                viewModel.updateFermentationType(ImportFermentationType.STRAIGHT)
            }
            ToggleButton("Cold retard", state.env.fermentationType == ImportFermentationType.COLD, Modifier.weight(1f)) {
                viewModel.updateFermentationType(ImportFermentationType.COLD)
            }
        }

        if (state.env.fermentationType == ImportFermentationType.COLD) {
            LabeledField("Cold retard duration (hours)", state.env.coldRetardHours, viewModel::updateColdRetardHours, widthDp = 100.dp)
        }

        Text("Pre-ferment", fontSize = 12.sp, color = colors.mutedForeground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleButton("None", state.env.preFerment == ImportPreFerment.NONE, Modifier.weight(1f)) { viewModel.updatePreFerment(ImportPreFerment.NONE) }
            ToggleButton("Biga", state.env.preFerment == ImportPreFerment.BIGA, Modifier.weight(1f)) { viewModel.updatePreFerment(ImportPreFerment.BIGA) }
            ToggleButton("Poolish", state.env.preFerment == ImportPreFerment.POOLISH, Modifier.weight(1f)) { viewModel.updatePreFerment(ImportPreFerment.POOLISH) }
        }

        state.analyzeError?.let { WarningBox(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .clickable { viewModel.goToStep(1) }
                    .padding(vertical = 12.dp),
            ) {
                Text("← Back", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
            }
            BreadIQButton(
                label = if (state.loading) "Calculating…" else "Calculate Formula →",
                onClick = { viewModel.analyze() }, variant = BreadIQButtonVariant.PRIMARY, loading = state.loading,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, widthDp: Dp? = null) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (widthDp != null) Modifier.width(widthDp) else modifier,
    ) {
        Text(label, fontSize = 12.sp, color = colors.mutedForeground)
        SimpleTextField(value = value, onValueChange = onValueChange, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToggleButton(label: String, isOn: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isOn) Color(0xFFEEF2FF) else colors.background)
            .border(1.dp, if (isOn) ImportAccent else colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isOn) ImportAccent else colors.foreground)
    }
}

// MARK: - Step 3: Results

@Composable
private fun Step3Formula(result: ImportAnalysisResult, recipeName: String, onBack: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val bp = result.bakerPercentages
    val wt = result.ingredientWeights

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(recipeName.ifEmpty { "Your Imported Formula" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text(
            "Inferred as ${ImportModalFormatting.styleLabel(result.inferredStyle)} · ${ImportModalFormatting.formatted(ImportModalFormatting.round1(result.totalFlourGrams))}g flour",
            fontSize = 13.sp, color = colors.mutedForeground,
        )

        if (result.flags.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFFBEB))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                result.flags.forEach { flag -> Text("⚠ $flag", fontSize = 12.sp, color = Color(0xFF92400E)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Hydration", "${result.effectiveHydrationPercent.toInt()}%", result.hydrationTier.tier.replace("_", " "), Modifier.weight(1f))
            StatCard("Fat", "${result.totalFatPercent.toInt()}%", result.fatTier.tier.replace("_", " "), Modifier.weight(1f))
            StatCard("Sugar", "${result.sugarPercent.toInt()}%", result.sugarTier.tier.replace("_", " "), Modifier.weight(1f))
        }

        SectionLabel("Baker's Percentages")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            InfoRow("Flour", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(wt.flour))}g · 100%")
            InfoRow("Water", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(wt.water))}g · ${ImportModalFormatting.formatted(bp.water)}%")
            InfoRow("Salt", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(wt.salt))}g · ${ImportModalFormatting.formatted(bp.salt)}%")
            InfoRow("Fat", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(wt.fat))}g · ${ImportModalFormatting.formatted(bp.fat)}%")
            InfoRow("Yeast", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(wt.yeast))}g · ${ImportModalFormatting.formatted(bp.yeast)}%")
            val sugar = wt.sugar
            val sugarBp = bp.sugar
            if (sugar != null && sugarBp != null) InfoRow("Sugar", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(sugar))}g · ${ImportModalFormatting.formatted(sugarBp)}%")
            val eggs = wt.eggs
            val eggsBp = bp.eggs
            if (eggs != null && eggsBp != null) InfoRow("Eggs", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(eggs))}g · ${ImportModalFormatting.formatted(eggsBp)}%")
            val dairy = wt.dairy
            val dairyBp = bp.dairy
            if (dairy != null && dairyBp != null) InfoRow("Dairy", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(dairy))}g · ${ImportModalFormatting.formatted(dairyBp)}%")
            InfoRow("Total dough", "${ImportModalFormatting.formatted(ImportModalFormatting.round1(result.totalDoughWeight))}g", showDivider = false)
        }

        SectionLabel("Proof Timeline · ${ImportModalFormatting.fmtMins(result.totalMinutes)} total")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            result.stages.forEachIndexed { i, stage ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stage.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ImportAccent, modifier = Modifier.weight(1f))
                        Text(ImportModalFormatting.fmtMins(stage.durationMinutes), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                    }
                    Text(stage.description, fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                if (i < result.stages.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE5E7EB)))
            }
        }

        if (result.advisories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.advisories.forEach { a -> Text("ℹ $a", fontSize = 12.sp, color = Color(0xFF2563EB)) }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(vertical = 12.dp),
        ) {
            Text("← Edit Ingredients", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ImportAccent)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label.uppercase(), fontSize = 10.sp, color = colors.mutedForeground)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = ImportAccent)
        Text(sub, fontSize = 10.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun InfoRow(label: String, value: String, showDivider: Boolean = true) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(label, fontSize = 13.sp, color = colors.mutedForeground, modifier = Modifier.weight(1f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

// MARK: - Shared small pieces

@Composable
private fun SectionLabel(title: String) {
    val colors = LocalBreadIQColors.current
    Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.mutedForeground, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DividerLabel(text: String) {
    val colors = LocalBreadIQColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        Text(text, fontSize = 11.sp, color = colors.mutedForeground)
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
    }
}

@Composable
private fun WarningBox(text: String) {
    Text(
        "⚠ $text", fontSize = 12.sp, color = Color(0xFF92400E),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

@Composable
private fun SuccessBox(text: String) {
    Text(
        "✓ $text", fontSize = 12.sp, color = Color(0xFF166534),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0FDF4))
            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

@Composable
private fun InfoBanner(title: String, body: String, background: Color, border: Color, text: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = text)
        Text(body, fontSize = 12.sp, color = text)
    }
}

@Composable
private fun ScanSourceDialog(onDismiss: () -> Unit, onTakePhoto: () -> Unit, onChooseLibrary: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Recipe") },
        text = {
            Column {
                TextButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) { Text("Take Photo") }
                TextButton(onClick = onChooseLibrary, modifier = Modifier.fillMaxWidth()) { Text("Choose from Library") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shared `BasicTextField` wrapper matching this codebase's established
 * box+border decoration (`CalculatorResultsCard.kt`'s recipe-name field,
 * `CalculatorAtoms.kt`'s numeric steppers). [onBlur] fires once on the
 * focused→unfocused transition — the Compose counterpart of the
 * source's `.onSubmit`/blur-triggered `handleNameBlur`.
 */
@Composable
private fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onBlur: (() -> Unit)? = null,
) {
    val colors = LocalBreadIQColors.current
    var wasFocused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = colors.foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().let { base ->
                if (onBlur == null) {
                    base
                } else {
                    base.onFocusChanged { focusState ->
                        if (wasFocused && !focusState.isFocused) onBlur()
                        wasFocused = focusState.isFocused
                    }
                }
            },
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = colors.mutedForeground)
                }
                innerTextField()
            },
        )
    }
}

private fun colorFromHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)),
    )
}
