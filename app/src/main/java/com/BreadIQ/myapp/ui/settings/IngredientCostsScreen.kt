package com.BreadIQ.myapp.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.IngredientCostFormatting
import com.BreadIQ.myapp.model.IngredientReferencePrice
import com.BreadIQ.myapp.model.IngredientReferencePriceCatalog
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.IngredientCostsUiState
import com.BreadIQ.myapp.viewmodel.IngredientCostsViewModel
import com.BreadIQ.myapp.viewmodel.IngredientCostsViewModelFactory
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModel
import com.BreadIQ.myapp.viewmodel.isPremium
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ported from the iOS app's `Screens/IngredientCostsScreen.swift` (minus
 * `IngredientCostFormatting`, split out into its own file).
 *
 * **`GET /api/reference-prices`, `GET/PUT/DELETE /api/ingredient-costs`
 * are real, live endpoints today** — no STUB-BACKEND boundary this
 * screen, unlike `ConnectBrowserScreen`.
 *
 * **One deliberate, explicitly-approved behavior change from the
 * source**: the source's own "Upgrade to Premium" button on the premium
 * gate has NO `onPress` at all — a genuine dead button, confirmed by
 * reading the JSX. Wired for real here to [onOpenSubscription], the same
 * paywall entry point `SettingsScreen`'s own tier/Upgrade Plan rows
 * already use. Everything else in this screen is a faithful, preserve-
 * don't-fix port.
 *
 * Reads [SubscriptionViewModel.uiState] directly for the premium gate
 * (not `CalculatorUiState` — this screen isn't part of the Calculator's
 * own state tree) — [subscriptionViewModel] is the app's shared,
 * Activity-scoped instance, threaded in from `MainActivity.kt` the same
 * way `SettingsScreen` already reads it, not a fresh one constructed
 * here.
 */
@Composable
fun IngredientCostsScreen(
    modifier: Modifier = Modifier,
    subscriptionViewModel: SubscriptionViewModel,
    viewModel: IngredientCostsViewModel = viewModel(factory = IngredientCostsViewModelFactory(LocalContext.current)),
    onOpenSubscription: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = LocalBreadIQColors.current
    val state by viewModel.uiState.collectAsState()
    val subState by subscriptionViewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        IngredientCostsHeader(onClose = onClose)
        if (!subState.isPremium) {
            PremiumGate(onOpenSubscription = onOpenSubscription)
        } else {
            Content(state = state, viewModel = viewModel)
        }
    }

    if (state.showInvalidPriceAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInvalidPriceAlert() },
            title = { Text("Invalid Price") },
            text = { Text("Enter a positive dollar amount per pound.") },
            confirmButton = { TextButton(onClick = { viewModel.dismissInvalidPriceAlert() }) { Text("OK") } },
        )
    }
}

/**
 * `"Dairy & Eggs"` added alongside the reference-catalog consolidation
 * ([IngredientReferencePriceCatalog]'s own doc comment) — eggs/milk/
 * butter always affected a real recipe's total cost via `CostEstimator`'s
 * own separate table, but had no category here to render in at all.
 * Placed last, after the pre-existing categories, so it reads as an
 * addition rather than a reordering.
 */
private val categories = listOf("Flours", "Fats & Oils", "Salt", "Yeast", "Sweeteners", "Malt", "Dairy & Eggs")

@Composable
private fun IngredientCostsHeader(onClose: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 14.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.foreground, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text("Ingredient Library", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("Custom pricing for cost analysis", fontSize = 11.sp, color = colors.mutedForeground)
            }
            Text(
                "⭐ Premium", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFEF3C7)).border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun PremiumGate(onOpenSubscription: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text("🔒", fontSize = 40.sp)
        Text("Premium Feature", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text(
            "Upgrade to Premium to set your actual ingredient prices and get precise formula cost analysis — synced between web and Android.",
            fontSize = 14.sp, color = colors.mutedForeground,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.orange)
                .clickable(onClick = onOpenSubscription)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Upgrade to Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Content

@Composable
private fun Content(state: IngredientCostsUiState, viewModel: IngredientCostsViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        InfoBanner(refUpdatedAt = state.refUpdatedAt)
        if (IngredientCostFormatting.isStale(state.refUpdatedAt)) {
            StaleBanner()
        }
        categories.forEach { category ->
            CategorySection(category = category, state = state, viewModel = viewModel)
        }
    }
}

private val lastUpdatedFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

@Composable
private fun InfoBanner(refUpdatedAt: Instant?) {
    val colors = LocalBreadIQColors.current
    val lastUpdatedClause = refUpdatedAt?.let { " Last updated: ${lastUpdatedFormatter.format(it)}." } ?: ""
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.muted.copy(alpha = 0.25f)).border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(13.dp))
        Text(
            "Reference prices updated monthly (King Arthur, Caputo, Bob's Red Mill, SAF). Leave blank to use reference. " +
                "Enter \$/lb — stored per gram and synced across web and Android.$lastUpdatedClause",
            fontSize = 12.sp, color = colors.mutedForeground,
        )
    }
}

@Composable
private fun StaleBanner() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF7ED)).border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(13.dp))
        Text(
            "Reference prices are over 30 days old and may not reflect current market pricing.",
            fontSize = 12.sp, color = Color(0xFF9A3412),
        )
    }
}

@Composable
private fun CategorySection(category: String, state: IngredientCostsUiState, viewModel: IngredientCostsViewModel) {
    val colors = LocalBreadIQColors.current
    val items = IngredientReferencePriceCatalog.all.filter { it.category == category }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = colors.mutedForeground)
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(12.dp)),
        ) {
            items.forEachIndexed { index, ingredient ->
                IngredientRow(ingredient = ingredient, isFirst = index == 0, state = state, viewModel = viewModel)
            }
        }
    }
}

// MARK: - Row

@Composable
private fun IngredientRow(
    ingredient: IngredientReferencePrice, isFirst: Boolean,
    state: IngredientCostsUiState, viewModel: IngredientCostsViewModel,
) {
    val colors = LocalBreadIQColors.current
    val customPpg = state.customPriceByKey[ingredient.key]
    val hasCustom = customPpg != null
    // Live server price when available, else the bundled ship-time
    // fallback — custom > live server > bundled, same precedence
    // CostEstimator.price(for:) already implements.
    val effectiveRefPpg = state.serverReferencePrices[ingredient.key] ?: ingredient.pricePerGram
    val displayPpg = customPpg ?: effectiveRefPpg
    val editVal = state.editValues[ingredient.key] ?: ""
    val isDirty = editVal.isNotEmpty()
    val isSaving = ingredient.key in state.savingKeys
    val isDeleting = ingredient.key in state.deletingKeys

    Column {
        if (!isFirst) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(ingredient.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                Text(ingredient.sourceCitation, fontSize = 10.sp, color = colors.mutedForeground)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        IngredientCostFormatting.formatPerLb(displayPpg), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (hasCustom) Color(0xFF1B3A8C) else colors.mutedForeground,
                    )
                    if (hasCustom) {
                        Text(
                            "custom", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B3A8C),
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEF2FF)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background)
                        .border(1.5.dp, if (isDirty) colors.primary else colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text("$", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                    BasicTextField(
                        value = editVal,
                        onValueChange = { viewModel.setEditValue(ingredient.key, it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(colors.primary),
                        decorationBox = { inner ->
                            if (editVal.isEmpty()) {
                                Text(IngredientCostFormatting.placeholderPerLb(effectiveRefPpg), fontSize = 13.sp, color = colors.mutedForeground.copy(alpha = 0.5f))
                            }
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 40.dp)
                            // Auto-save on blur — a real behavior to
                            // preserve, not cosmetic: matches the
                            // source's own `onChange(of: focusedKey)`
                            // firing save() when a dirty field loses
                            // focus.
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && isDirty) viewModel.save(ingredient.key)
                            },
                    )
                    Text("/lb", fontSize = 11.sp, color = colors.mutedForeground)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.primary.copy(alpha = if (isDirty && !isSaving) 1f else 0.35f))
                            .clickable(enabled = isDirty && !isSaving) { viewModel.save(ingredient.key) },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (hasCustom) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(7.dp))
                                .clickable(enabled = !isDeleting) { viewModel.reset(ingredient.key) },
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = colors.mutedForeground)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reset to reference price", tint = colors.mutedForeground, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
