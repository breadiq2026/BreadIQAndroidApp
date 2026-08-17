package com.BreadIQ.myapp.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.BuildConfig
import com.BreadIQ.myapp.core.SettingsTierPresentation
import com.BreadIQ.myapp.data.TemperatureUnitStore
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.ui.calculator.CalcChipOption
import com.BreadIQ.myapp.ui.calculator.CalcChipRow
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.AuthViewModel
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.launch

/**
 * Ported from the iOS app's `Screens/SettingsScreen.swift` (minus
 * `SettingsTierPresentation`, split out into its own file).
 *
 * **The account-deletion flow is destructive and irreversible, so its
 * double-confirm structure is ported exactly, not simplified to one
 * dialog.** Tapping "Delete Account" opens dialog 1 (confirm intent);
 * dialog 1's own destructive button opens dialog 2 ("Are you sure? …
 * There is no recovery."); only dialog 2's own confirm button calls
 * [AuthViewModel.deleteAccount] — matching the source's nested
 * `Alert.alert` structure, ported here as two independent booleans, the
 * second set from the first's own confirm action.
 *
 * **"Ingredient Costs" is real now** — it navigates to `IngredientCostsScreen`,
 * ported in the smaller-screens sweep's own follow-up session (was
 * disabled/dimmed here before that, the same established pattern this
 * codebase used for the Settings gear icon itself before the previous
 * session).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    temperatureUnitStore: TemperatureUnitStore,
    onOpenSubscription: () -> Unit = {},
    onOpenConnectBrowser: () -> Unit = {},
    onOpenIngredientCosts: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authState by authViewModel.uiState.collectAsState()
    val subState by subscriptionViewModel.uiState.collectAsState()
    val unit by temperatureUnitStore.unit.collectAsState()

    val tier = subState.tierInfo?.tier ?: "free"
    val isPremium = tier == "premium"
    val isBasic = tier == "basic"
    val trialActive = subState.tierInfo?.trialActive ?: false
    val trialDaysRemaining = subState.tierInfo?.trialDaysRemaining
    val isSubscribed = subState.rcTier != "free"

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirmStep1 by remember { mutableStateOf(false) }
    var showDeleteConfirmStep2 by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var restoreResultMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manageSubscriptionErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SettingsHeader(onClose = onClose)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AccountSection(
                tier = tier, isPremium = isPremium, isBasic = isBasic, trialActive = trialActive,
                trialDaysRemaining = trialDaysRemaining, isSubscribed = isSubscribed, email = authState.currentUser?.email,
                onOpenSubscription = onOpenSubscription,
                onManageSubscription = {
                    openManageSubscription(context) { manageSubscriptionErrorMessage = it }
                },
            )
            PremiumToolsSection(onOpenIngredientCosts = onOpenIngredientCosts)
            BrowserImportSection(onOpenConnectBrowser = onOpenConnectBrowser)
            AppSection(
                unit = unit,
                onUnitChange = { temperatureUnitStore.setUnit(it) },
                isRestoring = subState.isRestoring,
                onRestoreClick = {
                    scope.launch {
                        when (subscriptionViewModel.restorePurchases()) {
                            SubscriptionViewModel.RestoreOutcome.Restored ->
                                restoreResultMessage = "Purchases Restored" to "Your active subscription has been restored."
                            SubscriptionViewModel.RestoreOutcome.NoPurchasesFound ->
                                restoreResultMessage = "No Purchases Found" to "No active subscriptions found for this Google account."
                        }
                    }
                },
            )
            SectionBlock(title = null) {
                DestructiveRow(icon = Icons.AutoMirrored.Filled.Logout, label = "Sign Out") { showSignOutConfirm = true }
            }
            SectionBlock(title = null) {
                DestructiveRow(icon = Icons.Filled.Delete, label = "Delete Account") { showDeleteConfirmStep1 = true }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    scope.launch { authViewModel.signOut() }
                }) { Text("Sign Out", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirmStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmStep1 = false },
            title = { Text("Delete Account") },
            text = { Text("This will permanently delete your account and all saved recipes. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmStep1 = false
                    showDeleteConfirmStep2 = true
                }) { Text("Delete Account", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmStep1 = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirmStep2) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmStep2 = false },
            title = { Text("Are you sure?") },
            text = { Text("Your account and all data will be permanently deleted. There is no recovery.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmStep2 = false
                    scope.launch {
                        val result = authViewModel.deleteAccount()
                        result.onFailure { deleteErrorMessage = it.message }
                    }
                }) { Text("Yes, Delete My Account", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmStep2 = false }) { Text("Cancel") } },
        )
    }

    deleteErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteErrorMessage = null },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deleteErrorMessage = null }) { Text("OK") } },
        )
    }

    restoreResultMessage?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { restoreResultMessage = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { restoreResultMessage = null }) { Text("OK") } },
        )
    }

    manageSubscriptionErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { manageSubscriptionErrorMessage = null },
            title = { Text("Could Not Open") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { manageSubscriptionErrorMessage = null }) { Text("OK") } },
        )
    }
}

/**
 * Play Store account/subscriptions deep link — the Android counterpart
 * to the source's `itms-apps://apps.apple.com/account/subscriptions`
 * (falling back to `https://`). Real, working code today: a plain system
 * `Intent`, not RevenueCat/Billing SDK integration, same boundary the
 * source itself draws. `BuildConfig.APPLICATION_ID` rather than
 * hardcoding "com.BreadIQ.myapp" a second time.
 */
private fun openManageSubscription(context: android.content.Context, onFailure: (String) -> Unit) {
    val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${BuildConfig.APPLICATION_ID}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onFailure("Please go to Settings → your account → Subscriptions to manage your plan.")
    }
}

// MARK: - Header

@Composable
private fun SettingsHeader(onClose: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 14.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.foreground, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

// MARK: - Account section

@Composable
private fun AccountSection(
    tier: String, isPremium: Boolean, isBasic: Boolean, trialActive: Boolean, trialDaysRemaining: Int?,
    isSubscribed: Boolean, email: String?, onOpenSubscription: () -> Unit, onManageSubscription: () -> Unit,
) {
    SectionBlock(title = "ACCOUNT") {
        TierRow(isPremium, isBasic, trialActive, trialDaysRemaining, onOpenSubscription)
        if (trialActive && trialDaysRemaining != null) {
            TrialBanner(onClick = onOpenSubscription)
        }
        if (!isPremium && !trialActive) {
            SettingsRow(
                icon = Icons.Filled.Bolt,
                label = "Upgrade Plan",
                description = if (isBasic) "Unlock 5 flour types, SpeedRun™ & more" else "Start with Basic or go straight to Premium",
                badge = if (isBasic) "→ Premium" else "Basic / Premium",
                badgeColor = Color(0xFFF97316),
                onClick = onOpenSubscription,
            )
        }
        if (isSubscribed) {
            SettingsRow(
                icon = Icons.Filled.CreditCard,
                label = "Manage Subscription",
                description = "Cancel or change your plan via the Play Store",
                onClick = onManageSubscription,
            )
        }
        if (email != null) {
            InfoRow(label = "Email", value = email, showsDivider = false)
        }
    }
}

@Composable
private fun TierRow(isPremium: Boolean, isBasic: Boolean, trialActive: Boolean, trialDaysRemaining: Int?, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val emoji = if (trialActive) "🎁" else if (isPremium) "⭐" else if (isBasic) "✦" else "🔓"
    val iconBackground = if (trialActive) Color(0xFFFFF7ED) else if (isPremium) Color(0xFFFEF3C7) else Color(0xFFF0F4FF)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isPremium, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(iconBackground)) {
                Text(emoji, fontSize = 16.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(SettingsTierPresentation.label(trialActive, isPremium, isBasic), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                Text(
                    SettingsTierPresentation.description(trialActive, trialDaysRemaining, isPremium, isBasic),
                    fontSize = 12.sp, color = if (trialActive) Color(0xFFF97316) else colors.mutedForeground,
                )
            }
            if (!isPremium && !trialActive) {
                Text(
                    "Upgrade", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF97316)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (trialActive || !isPremium) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        }
    }
}

@Composable
private fun TrialBanner(onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Text(
            "⚡ Subscribe before your trial ends to keep Premium access",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC2410C),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).background(Color(0xFFFFF7ED)).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

// MARK: - Premium Tools section

@Composable
private fun PremiumToolsSection(onOpenIngredientCosts: () -> Unit) {
    SectionBlock(title = "PREMIUM TOOLS") {
        SettingsRow(
            icon = Icons.Filled.AttachMoney,
            label = "Ingredient Costs",
            description = "Set your actual prices for precise cost analysis",
            badge = "⭐ Premium",
            onClick = onOpenIngredientCosts,
        )
    }
}

// MARK: - Browser Import section

/**
 * ██ STUB-BACKEND ██ — the screen this pushes to ([ConnectBrowserScreen])
 * calls a pairing-code endpoint that doesn't exist on `api-server` yet.
 * The row itself is real and always available (no premium gate — the
 * Chrome-extension discovery memo scoped this as a plain account
 * feature, not a paid tool).
 */
@Composable
private fun BrowserImportSection(onOpenConnectBrowser: () -> Unit) {
    SectionBlock(title = "BROWSER IMPORT") {
        SettingsRow(
            icon = Icons.Filled.Link,
            label = "Connect a Browser",
            description = "Pair the Chrome extension to import recipes from your desktop",
            onClick = onOpenConnectBrowser,
        )
    }
}

// MARK: - App section

@Composable
private fun AppSection(unit: TemperatureUnit, onUnitChange: (TemperatureUnit) -> Unit, isRestoring: Boolean, onRestoreClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    SectionBlock(title = "APP") {
        InfoRow(label = "Version", value = BuildConfig.VERSION_NAME, showsDivider = true)
        TemperatureUnitRow(unit, onUnitChange)
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().clickable(enabled = !isRestoring, onClick = onRestoreClick).padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.08f))) {
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
                }
            }
            Text(
                if (isRestoring) "Restoring…" else "Restore Purchases", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = colors.foreground, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * Reuses [CalcChipRow]'s existing chip-style picker (already established
 * for the calculator's cold-retard fridge-temp choice) rather than
 * inventing a new control. The disclosure line below it matches this
 * project's existing convention of flagging a known gap rather than
 * silently omitting it — hand-written reference prose stays °F-only
 * regardless of this setting.
 */
@Composable
private fun TemperatureUnitRow(unit: TemperatureUnit, onUnitChange: (TemperatureUnit) -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Temperature Unit", fontSize = 14.sp, color = colors.mutedForeground)
            Spacer(Modifier.weight(1f))
            CalcChipRow(
                options = TemperatureUnit.entries.map { CalcChipOption(value = it.rawValue, label = it.symbol) },
                selected = unit.rawValue,
                onSelect = { value -> TemperatureUnit.entries.firstOrNull { it.rawValue == value }?.let(onUnitChange) },
            )
        }
        Text("Technique guides and reference tips are shown in °F for now.", fontSize = 11.sp, color = colors.mutedForeground)
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

// MARK: - Shared row builders

@Composable
private fun SectionBlock(title: String?, content: @Composable () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = colors.mutedForeground)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(12.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector, label: String, description: String? = null, badge: String? = null,
    badgeColor: Color? = null, enabled: Boolean = true, onClick: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    val alpha = if (enabled) 1f else 0.5f
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.08f * alpha)),
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary.copy(alpha = alpha), modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground.copy(alpha = alpha))
                    if (badge != null) {
                        Text(
                            badge, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                            color = if (badgeColor != null) Color.White else Color(0xFF92400E),
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background((badgeColor ?: Color(0xFFFEF3C7)).copy(alpha = alpha))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (description != null) {
                    Text(description, fontSize = 12.sp, color = colors.mutedForeground.copy(alpha = alpha))
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedForeground.copy(alpha = alpha), modifier = Modifier.size(15.dp))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

@Composable
private fun InfoRow(label: String, value: String, showsDivider: Boolean) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, fontSize = 14.sp, color = colors.mutedForeground)
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, maxLines = 1)
        }
        if (showsDivider) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        }
    }
}

@Composable
private fun DestructiveRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.destructive.copy(alpha = 0.08f))) {
            Icon(icon, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(17.dp))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.destructive)
    }
}
