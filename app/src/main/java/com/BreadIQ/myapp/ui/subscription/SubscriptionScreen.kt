package com.BreadIQ.myapp.ui.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.PurchaseOutcome
import com.BreadIQ.myapp.core.SubscriptionFormatting
import com.BreadIQ.myapp.model.SubscriptionPackage
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModel
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModelFactory
import kotlinx.coroutines.launch

/**
 * `tierFeatures` — port of `subscription.tsx`'s static feature-comparison
 * copy, verbatim, real marketing copy, not placeholder. **One deliberate
 * word substitution**: the source's "1/3 active bake(s) on iOS" lines
 * literally say "iOS" — correct there, wrong verbatim on this platform
 * (an Android user reading "on iOS" in the Android app would be
 * reading an obvious copy bug, not a legitimate cross-platform
 * difference) — substituted "Android" for "iOS" in those two lines
 * only, everything else transcribed exactly.
 */
private val tierFeatures: Map<String, List<String>> = mapOf(
    "basic" to listOf(
        "3 flour types per recipe",
        "Pre-ferments (Poolish & Biga)",
        "10 recipes in your library",
        "Ingredient cost estimates",
        "1 active bake on Android",
    ),
    "premium" to listOf(
        "Everything in Basic",
        "Up to 5 flour types per recipe",
        "Cold ferment & diastatic malt",
        "SpeedRun™ mode",
        "50 recipes in your library",
        "Custom ingredient cost input",
        "Recipe export & sharing",
        "3 active bakes on Android",
    ),
)

private data class RestoreResultMessage(val title: String, val body: String, val dismissesOnConfirm: Boolean)

/**
 * Ported from the iOS app's `Screens/SubscriptionScreen.swift` — the
 * paywall UI. `packages` arrives already classified into `tier`/`period`
 * via `SubscriptionPackageClassification` at the
 * `SubscriptionViewModel`/`PurchasesServicing` boundary — see that
 * type's own doc comment for why classification happens once there
 * instead of being re-derived by string matching here. Everything below
 * is a straightforward filter over an already-trustworthy field.
 *
 * Reached from Calculator's upgrade-prompt alert's new "See Plans"
 * button (`CalculatorScreen.kt`) via
 * [com.BreadIQ.myapp.navigation.BreadIQRoutes.SUBSCRIPTION] — no full
 * Settings/account-management surface exists yet to reach this from on
 * Android (a separate, later item); this entry point is enough for this
 * session.
 */
@Composable
fun SubscriptionScreen(
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = viewModel(factory = SubscriptionViewModelFactory(LocalContext.current)),
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var selectedTier by remember { mutableStateOf("premium") }
    var selectedPeriod by remember { mutableStateOf("monthly") }
    var confirmPackage by remember { mutableStateOf<SubscriptionPackage?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var purchaseFailureMessage by remember { mutableStateOf<String?>(null) }
    var restoreResultMessage by remember { mutableStateOf<RestoreResultMessage?>(null) }

    val allPackages = state.packages
    val basicPackages = allPackages.filter { it.tier == "basic" }
    val premiumPackages = allPackages.filter { it.tier == "premium" }
    val hasBothTiers = basicPackages.isNotEmpty() && premiumPackages.isNotEmpty()
    val tierPackages = if (selectedTier == "basic") basicPackages else premiumPackages
    val monthlyPackage = tierPackages.firstOrNull { it.period == "monthly" }
    val annualPackage = tierPackages.firstOrNull { it.period == "annual" }
    val selectedPackage = tierPackages.firstOrNull { it.period == selectedPeriod } ?: tierPackages.firstOrNull()
    val trialEligible = SubscriptionFormatting.hasFreeTrialOffer(selectedPackage?.introPrice)
    val trialLabel = SubscriptionFormatting.trialDurationLabel(selectedPackage?.introPrice)
    val accent = if (selectedTier == "basic") Color(0xFF1B3A8C) else Color(0xFFF97316)
    val periodOptions = listOfNotNull(
        monthlyPackage?.let { "monthly" to it },
        annualPackage?.let { "annual" to it },
    )
    val annualSavingsPercentValue = if (annualPackage != null && monthlyPackage != null) {
        SubscriptionFormatting.annualSavingsPercent(annualPackage.price, monthlyPackage.price)
    } else {
        null
    }

    fun handleConfirmPurchase(pkg: SubscriptionPackage, tierAtConfirm: String) {
        confirmPackage = null
        val activity = context.findActivity() ?: return
        scope.launch {
            when (val result = viewModel.purchase(activity, pkg.productIdentifier)) {
                PurchaseOutcome.Success -> successMessage = "Welcome to BreadIQ ${SubscriptionFormatting.tierLabel(tierAtConfirm)}. Your features are now unlocked."
                is PurchaseOutcome.Failure -> {
                    if (!SubscriptionFormatting.isCancellation(result.error.message)) {
                        purchaseFailureMessage = result.error.message.ifEmpty { "Something went wrong. Please try again." }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            SubscriptionHeader(onClose = onClose)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Hero(trialEligible, trialLabel)
                when {
                    state.isLoadingOfferings -> Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    allPackages.isEmpty() -> Text(
                        "No subscription plans available. Check back soon.", fontSize = 14.sp, textAlign = TextAlign.Center,
                        color = colors.mutedForeground, modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    )
                    else -> {
                        if (hasBothTiers) TierToggle(selectedTier, onSelect = { selectedTier = it })
                        if (tierPackages.size > 1) PeriodToggle(periodOptions, selectedPeriod, annualSavingsPercentValue, onSelect = { selectedPeriod = it })
                        FeatureCard(selectedTier, accent)
                        selectedPackage?.let { pkg -> PriceBlock(pkg, selectedPeriod, trialEligible, trialLabel) }
                        PurchaseButton(
                            accent = accent, isPurchasing = state.isPurchasing, hasSelectedPackage = selectedPackage != null,
                            rcTier = state.rcTier, selectedTier = selectedTier, trialEligible = trialEligible, trialLabel = trialLabel,
                            onClick = { selectedPackage?.let { confirmPackage = it } },
                        )
                        LegalFootnote(trialEligible, trialLabel)
                        LegalLinks(
                            onPrivacyClick = { uriHandler.openUri("https://breadiq.io/privacy") },
                            onTermsClick = { uriHandler.openUri("https://breadiq.io/terms") },
                        )
                        RestoreButton(
                            isRestoring = state.isRestoring,
                            onClick = {
                                scope.launch {
                                    when (viewModel.restorePurchases()) {
                                        SubscriptionViewModel.RestoreOutcome.Restored ->
                                            restoreResultMessage = RestoreResultMessage("Purchases Restored", "Your active subscription has been restored.", true)
                                        SubscriptionViewModel.RestoreOutcome.NoPurchasesFound ->
                                            restoreResultMessage = RestoreResultMessage("No Purchases Found", "We couldn't find any active subscriptions to restore.", false)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        confirmPackage?.let { pkg ->
            ConfirmModalOverlay(
                pkg = pkg, period = pkg.period,
                onConfirm = { handleConfirmPurchase(pkg, selectedTier) },
                onCancel = { confirmPackage = null },
            )
        }
    }

    if (successMessage != null) {
        AlertDialog(
            onDismissRequest = { successMessage = null; onClose() },
            title = { Text("Subscribed!") },
            text = { Text(successMessage ?: "") },
            confirmButton = { TextButton(onClick = { successMessage = null; onClose() }) { Text("Let's Bake!") } },
        )
    }
    if (purchaseFailureMessage != null) {
        AlertDialog(
            onDismissRequest = { purchaseFailureMessage = null },
            title = { Text("Purchase Failed") },
            text = { Text(purchaseFailureMessage ?: "") },
            confirmButton = { TextButton(onClick = { purchaseFailureMessage = null }) { Text("OK") } },
        )
    }
    restoreResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {
                val dismisses = message.dismissesOnConfirm
                restoreResultMessage = null
                if (dismisses) onClose()
            },
            title = { Text(message.title) },
            text = { Text(message.body) },
            confirmButton = {
                TextButton(onClick = {
                    val dismisses = message.dismissesOnConfirm
                    restoreResultMessage = null
                    if (dismisses) onClose()
                }) { Text(if (message.dismissesOnConfirm) "Great!" else "OK") }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// MARK: - Header / hero

@Composable
private fun SubscriptionHeader(onClose: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 14.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.foreground, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
            Spacer(Modifier.weight(1f))
            Text("Choose Your Plan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(22.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun Hero(trialEligible: Boolean, trialLabel: String) {
    val colors = LocalBreadIQColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Precise. Scientific. Simple.", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = colors.foreground)
        if (trialEligible) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF0FDF4))
                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("🎁", fontSize = 16.sp)
                Text("Try free for $trialLabel — no charge until your trial ends", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
            }
        } else {
            Text("Unlock the full BreadIQ formula engine.", fontSize = 14.sp, textAlign = TextAlign.Center, color = colors.mutedForeground)
        }
    }
}

// MARK: - Toggles

@Composable
private fun TierToggle(selectedTier: String, onSelect: (String) -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.muted).border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(4.dp),
    ) {
        listOf("basic", "premium").forEach { t ->
            val isOn = selectedTier == t
            Text(
                SubscriptionFormatting.tierLabel(t), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (isOn) Color.White else colors.mutedForeground,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) (if (t == "basic") Color(0xFF1B3A8C) else Color(0xFFF97316)) else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PeriodToggle(options: List<Pair<String, SubscriptionPackage>>, selectedPeriod: String, savePct: Int?, onSelect: (String) -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.muted).border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(4.dp),
    ) {
        options.forEach { (key, _) ->
            val isOn = selectedPeriod == key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) colors.card else Color.Transparent)
                    .border(1.dp, if (isOn) colors.border else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    if (key == "annual") "Annual" else "Monthly", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isOn) colors.foreground else colors.mutedForeground,
                )
                if (key == "annual" && savePct != null) {
                    Text(
                        "Save $savePct%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A),
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Feature card / price block

@Composable
private fun FeatureCard(selectedTier: String, accent: Color) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Text(SubscriptionFormatting.tierLabel(selectedTier), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        (tierFeatures[selectedTier] ?: emptyList()).forEach { feature ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Text(feature, fontSize = 14.sp, color = colors.foreground)
            }
        }
    }
}

@Composable
private fun PriceBlock(pkg: SubscriptionPackage, selectedPeriod: String, trialEligible: Boolean, trialLabel: String) {
    val colors = LocalBreadIQColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        if (trialEligible) {
            Text("Free", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
            Text(
                SubscriptionFormatting.trialPricePeriodText(pkg.priceString, selectedPeriod, trialLabel),
                fontSize = 13.sp, color = colors.mutedForeground,
            )
            Text(
                "Cancel anytime before trial ends — you won't be charged", fontSize = 12.sp, textAlign = TextAlign.Center,
                color = colors.mutedForeground,
            )
        } else {
            Text(pkg.priceString, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Text(
                SubscriptionFormatting.nonTrialPricePeriodText(selectedPeriod, pkg.price, pkg.currencyCode),
                fontSize = 13.sp, color = colors.mutedForeground,
            )
        }
    }
}

// MARK: - Purchase / legal / restore

@Composable
private fun PurchaseButton(
    accent: Color, isPurchasing: Boolean, hasSelectedPackage: Boolean, rcTier: String, selectedTier: String,
    trialEligible: Boolean, trialLabel: String, onClick: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    val disabled = SubscriptionFormatting.isPurchaseDisabled(hasSelectedPackage, isPurchasing, rcTier, selectedTier)
    val onCurrentTier = rcTier == selectedTier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background((if (onCurrentTier) colors.muted else accent).let { if (!hasSelectedPackage || isPurchasing) it.copy(alpha = 0.7f) else it })
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        if (isPurchasing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(
                SubscriptionFormatting.purchaseButtonLabel(rcTier, selectedTier, trialEligible, trialLabel),
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (onCurrentTier) colors.mutedForeground else Color.White,
            )
        }
    }
}

@Composable
private fun LegalFootnote(trialEligible: Boolean, trialLabel: String) {
    val colors = LocalBreadIQColors.current
    val prefix = if (trialEligible) {
        "Free trial lasts $trialLabel. After the trial, payment will be charged to your Google Play account. "
    } else {
        "Payment will be charged to your Google Play account. "
    }
    Text(
        "$prefix" + "Subscription auto-renews unless cancelled at least 24 hours before the end of the current period.",
        fontSize = 11.sp, textAlign = TextAlign.Center, color = colors.mutedForeground, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun LegalLinks(onPrivacyClick: () -> Unit, onTermsClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.clickable(onClick = onPrivacyClick))
        Text("·", fontSize = 12.sp, color = colors.mutedForeground)
        Text("Terms of Use", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.clickable(onClick = onTermsClick))
    }
}

@Composable
private fun RestoreButton(isRestoring: Boolean, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isRestoring, onClick = onClick).padding(vertical = 4.dp),
    ) {
        if (isRestoring) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text("Restore Purchases", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
    }
}

// MARK: - Confirm purchase overlay

/** Port of `subscription.tsx`'s `ConfirmModal` — a custom dimmed overlay with a centered card, not a native dialog, matching the source's own transparent full-screen modal rather than the system presentation. */
@Composable
private fun ConfirmModalOverlay(pkg: SubscriptionPackage, period: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val trialEligible = SubscriptionFormatting.hasFreeTrialOffer(pkg.introPrice)
    val trialLabel = SubscriptionFormatting.trialDurationLabel(pkg.introPrice)
    val periodSuffix = if (period == "annual") " / year" else " / month"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onCancel),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                // Swallow taps on the card itself so they don't fall through to the scrim's own onCancel click.
                .clickable(enabled = false, onClick = {})
                .padding(24.dp),
        ) {
            Text("Confirm Purchase", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            if (trialEligible) {
                Text(
                    "Start your $trialLabel free trial of ${pkg.title}. You won't be charged until the trial ends. After that, ${pkg.priceString}$periodSuffix.",
                    fontSize = 15.sp, color = colors.mutedForeground,
                )
            } else {
                Text(
                    "Purchase ${pkg.title} for ${pkg.priceString}$periodSuffix?",
                    fontSize = 15.sp, color = colors.mutedForeground,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.muted)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 12.dp),
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF97316))
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 12.dp),
                ) {
                    Text("Confirm", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}
