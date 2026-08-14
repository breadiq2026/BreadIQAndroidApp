package com.BreadIQ.myapp.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.Recipe
import com.BreadIQ.myapp.ui.components.Badge
import com.BreadIQ.myapp.ui.components.BadgeVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.RecipesViewModel
import com.BreadIQ.myapp.viewmodel.RecipesViewModelFactory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Port of `RecipesScreen.swift`'s `RecipeFormatting` enum — pure string-formatting helpers. */
object RecipeFormatting {
    /** `s.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase())`. */
    fun formatStyle(s: String): String =
        s.replace('_', ' ').split(" ").joinToString(" ") { word -> if (word.isEmpty()) "" else word[0].uppercaseChar() + word.substring(1) }

    private val flourLabels = mapOf(
        "bread" to "Bread Flour", "all_purpose" to "All-Purpose", "whole_wheat" to "Whole Wheat",
        "dark_rye" to "Dark Rye", "spelt" to "Spelt", "einkorn" to "Einkorn", "semolina" to "Semolina", "tipo_00" to "Tipo 00",
    )

    /** `map[t] ?? t.replace(/_/g, " ")` — the fallback does NOT title-case, unlike [formatStyle]'s fallback-free transform above. */
    fun formatFlourType(t: String): String = flourLabels[t] ?: t.replace('_', ' ')

    /**
     * Takes [direction] as a non-optional `String`, not the source's
     * nullable one — the source interpolates `recipe.humidityDirection`
     * raw with no null guard (an accidental JS artifact, not a
     * deliberate behavior); the caller passes `recipe.humidityDirection ?: ""`.
     */
    fun humidityBannerText(rh: Int, direction: String, waterWeightUnadjusted: Double?): String {
        var text = "Humidity adjusted formula — built at $rh% RH ($direction humidity)."
        if (waterWeightUnadjusted != null) {
            text += " Standard water weight would be ${formatGrams(waterWeightUnadjusted)}."
        }
        return text
    }

    /** `${value}` — kept separate from [formatGrams]'s callers for clarity at the call site (no "g" suffix here). */
    fun formatNumberForDisplay(n: Double): String = formatNumber(n)

    /** Trims a trailing `.0`/`.00…`, matching JS's default `Number` → `String` coercion. Duplicated from `FormulaResultView`'s own private formatting rather than exposing it — same "each screen keeps its own local copy" convention already established (`CalculatorFormatting`/`NutritionFormatting`). */
    fun formatNumber(n: Double): String {
        if (n == n.swiftRounded()) return n.toLong().toString()
        var s = String.format("%.4f", n)
        while (s.endsWith("0")) s = s.dropLast(1)
        if (s.endsWith(".")) s = s.dropLast(1)
        return s
    }

    fun formatGrams(n: Double): String {
        val rounded = (n * 10).swiftRounded() / 10
        return "${formatNumber(rounded)}g"
    }
}

private val cardDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val detailDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private fun formatCardDate(instant: Instant): String = cardDateFormatter.format(instant.atZone(ZoneId.systemDefault()))
private fun formatDetailDate(instant: Instant): String = detailDateFormatter.format(instant.atZone(ZoneId.systemDefault()))

/**
 * Ported from the iOS app's `Screens/RecipesScreen.swift`.
 *
 * **"Load into Calculator" — a real, non-obvious gap found while
 * porting, not just a stub.** The source's `RecipesScreen` sets
 * `appRouter.pendingRecipe` and switches tabs, but `CalculatorScreen`
 * never actually reads `pendingRecipe` anywhere — grepped the whole
 * file, confirmed absent. The iOS app itself never finished wiring this
 * consumption. [onLoadIntoCalculator] here is the real handoff: it
 * calls into `CalculatorViewModel.loadFromRecipe(recipeId)` (this
 * session's own addition, approved directly for its auto-calculate +
 * land-on-Card-4 behavior, since the source has zero precedent to
 * follow) via whatever pending-recipe mechanism `MainActivity`'s
 * `NavHost` wires up — see that wiring's own doc comment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = viewModel(factory = RecipesViewModelFactory(LocalContext.current)),
    onLoadIntoCalculator: (recipeId: Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        RecipesHeader(onRefreshClick = {
            coroutineScope.launch {
                isRefreshing = true
                viewModel.refresh()
                isRefreshing = false
            }
        })

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.recipes.isEmpty()) {
                RecipesEmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onTap = { viewModel.selectRecipe(recipe.id) })
                    }
                    item(key = "bottom-padding") { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    val selectedRecipe = state.recipes.firstOrNull { it.id == state.selectedRecipeId }
    if (selectedRecipe != null) {
        val sheetState = rememberModalBottomSheetState()
        RecipeDetailSheet(
            recipe = selectedRecipe,
            sheetState = sheetState,
            onDismiss = { viewModel.selectRecipe(null) },
            onLoad = {
                onLoadIntoCalculator(selectedRecipe.id)
                viewModel.selectRecipe(null)
            },
            onDelete = {
                viewModel.delete(selectedRecipe)
                Haptics.impact(context, HapticImpactStyle.MEDIUM)
            },
        )
    }
}

@Composable
private fun RecipesHeader(onRefreshClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text("Recipes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.foreground, modifier = Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.muted)
                .clickable(onClick = onRefreshClick),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RecipesEmptyState() {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp).padding(horizontal = 32.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.border, modifier = Modifier.size(44.dp))
        Text("No recipes yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        Text(
            "Save a recipe from the Calculator to see it here.", fontSize = 14.sp, color = colors.mutedForeground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onTap: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(recipe.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, maxLines = 1)
                    Text(formatCardDate(recipe.createdAt), fontSize = 12.sp, color = colors.mutedForeground)
                }
                Badge(label = recipe.loafStyle.replace('_', ' '), variant = BadgeVariant.PRIMARY)
            }
            StatPillRow(recipe)
        }
    }
}

@Composable
private fun StatPillRow(recipe: Recipe) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(Icons.Filled.WaterDrop, "${RecipeFormatting.formatNumberForDisplay(recipe.hydrationPercent)}%", colors.primary)
        StatPill(Icons.Filled.Inventory2, RecipeFormatting.formatGrams(recipe.flourWeight.swiftRounded()), colors.mutedForeground)
        StatPill(Icons.Filled.Layers, "×${recipe.numLoaves}", colors.mutedForeground)
        if (recipe.fatPercent > 0) {
            StatPill(Icons.Filled.Circle, "Fat ${RecipeFormatting.formatNumberForDisplay(recipe.fatPercent)}%", colors.mutedForeground)
        }
        if (recipe.humidityAdjusted && recipe.humidityRh != null) {
            StatPill(Icons.Filled.Cloud, "${recipe.humidityRh}% RH adjusted", Color(0xFFC2410C))
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 12.sp, color = color)
    }
}

// MARK: - Detail sheet

private data class WeightRow(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailSheet(
    recipe: Recipe,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val hasBlend = (recipe.flourBlend?.size ?: 0) > 1

    val weightRows = remember(recipe) {
        buildList {
            add(WeightRow("Flour", RecipeFormatting.formatGrams(recipe.flourWeight)))
            add(WeightRow("Water", RecipeFormatting.formatGrams(recipe.waterWeight)))
            add(WeightRow("Salt", RecipeFormatting.formatGrams(recipe.saltWeight)))
            add(WeightRow("Yeast", RecipeFormatting.formatGrams(recipe.yeastWeight)))
            if (recipe.fatWeight > 0) add(WeightRow("Fat / Oil", RecipeFormatting.formatGrams(recipe.fatWeight)))
            val sweetenerType = recipe.sweetenerType
            val sweetenerWeight = recipe.sweetenerWeight
            if (sweetenerType != null && sweetenerWeight != null && sweetenerWeight > 0) {
                add(WeightRow(RecipeFormatting.formatStyle(sweetenerType), RecipeFormatting.formatGrams(sweetenerWeight)))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DetailSheetHeader(recipe, onDismiss)

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                BadgeRow(recipe)
                if (recipe.importSourceName != null && recipe.importSourceURL != null) {
                    ImportSourceLine(recipe.importSourceName)
                }
                if (recipe.humidityAdjusted && recipe.humidityRh != null) {
                    HumidityBanner(recipe.humidityRh, recipe.humidityDirection ?: "", recipe.waterWeightUnadjusted)
                }
                FormulaSection(recipe)
                SectionBox("WEIGHTS") {
                    Column { weightRows.forEach { WeightRowView(it.label, it.value) } }
                }
                if (hasBlend) {
                    SectionBox("FLOUR BLEND") {
                        Column {
                            recipe.flourBlend?.forEach { entry ->
                                WeightRowView(RecipeFormatting.formatFlourType(entry.type), "${RecipeFormatting.formatNumberForDisplay(entry.percent)}%", colors.mutedForeground)
                            }
                        }
                    }
                }
                val preFermentType = recipe.preFermentType
                if (preFermentType != null && recipe.preFermentFlourWeight != null) {
                    SectionBox(preFermentType.uppercase()) {
                        Column {
                            WeightRowView("Pre-ferment Flour", RecipeFormatting.formatGrams(recipe.preFermentFlourWeight))
                            recipe.preFermentWaterWeight?.let { WeightRowView("Pre-ferment Water", RecipeFormatting.formatGrams(it)) }
                        }
                    }
                }
                SectionBox("FERMENTATION") {
                    WeightRowView("Method", RecipeFormatting.formatStyle(recipe.fermentationType))
                }
                val formatNote = recipe.formatNote
                if (!formatNote.isNullOrEmpty()) {
                    SectionBox("FORMAT NOTE") {
                        Text(formatNote, fontSize = 13.sp, color = colors.foreground, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
                val notes = recipe.notes
                if (!notes.isNullOrEmpty()) {
                    SectionBox("NOTES") {
                        Text(notes, fontSize = 13.sp, color = colors.foreground, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
            }

            ActionRow(onDeleteClick = { showDeleteConfirmation = true }, onLoad = onLoad)
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete \"${recipe.name}\"?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Delete", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailSheetHeader(recipe: Recipe, onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(recipe.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.foreground, maxLines = 1)
                Text(formatDetailDate(recipe.createdAt), fontSize = 12.sp, color = colors.mutedForeground)
            }
            Icon(
                Icons.Filled.Close, contentDescription = "Close", tint = colors.mutedForeground,
                modifier = Modifier.size(18.dp).clickable(onClick = onDismiss),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun BadgeRow(recipe: Recipe) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge(label = RecipeFormatting.formatStyle(recipe.loafStyle), variant = BadgeVariant.PRIMARY)
        Badge(label = "×${recipe.numLoaves} ${if (recipe.numLoaves == 1) "loaf" else "loaves"}", variant = BadgeVariant.MUTED)
        if (recipe.humidityAdjusted && recipe.humidityRh != null) {
            Badge(label = "${recipe.humidityRh}% RH", variant = BadgeVariant.MUTED)
        }
    }
}

@Composable
private fun ImportSourceLine(sourceName: String) {
    val colors = LocalBreadIQColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(10.dp))
        Text("Imported from $sourceName", fontSize = 11.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun HumidityBanner(rh: Int, direction: String, waterWeightUnadjusted: Double?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Icon(Icons.Filled.Cloud, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(13.dp))
        Text(RecipeFormatting.humidityBannerText(rh, direction, waterWeightUnadjusted), fontSize = 12.sp, color = Color(0xFFC2410C))
    }
}

@Composable
private fun FormulaSection(recipe: Recipe) {
    SectionBox("FORMULA") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            StatBox("Hydration", "${RecipeFormatting.formatNumberForDisplay(recipe.hydrationPercent)}%", accent = true)
            StatBox("Fat", "${RecipeFormatting.formatNumberForDisplay(recipe.fatPercent)}%")
            StatBox("Yeast", "${RecipeFormatting.formatNumberForDisplay(recipe.yeastPercent)}%")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatBox(label: String, value: String, accent: Boolean = false) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.muted)
            .padding(vertical = 10.dp),
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (accent) colors.primary else colors.foreground)
        Text(label, fontSize = 11.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun SectionBox(title: String, content: @Composable () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
    ) {
        Text(
            title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = colors.mutedForeground,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).padding(bottom = 0.dp),
        )
        content()
    }
}

@Composable
private fun WeightRowView(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(label, fontSize = 14.sp, color = colors.foreground, modifier = Modifier.weight(1f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor ?: colors.foreground)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun ActionRow(onDeleteClick: () -> Unit, onLoad: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.destructive, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDeleteClick)
                    .padding(vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(16.dp))
                Text("Delete", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.destructive)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary)
                    .clickable(onClick = onLoad)
                    .padding(vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Load into Calculator", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
