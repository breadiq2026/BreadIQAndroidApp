package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.StagedImportListItem
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `Screens/PendingImportsListScreen.swift` —
 * sheet-presented picker for the Chrome-extension companion's
 * pending-imports inbox, the cross-device counterpart to the Safari
 * extension's same-device deep link. Presented from `CalculatorScreen`'s
 * import status banner whenever [CalculatorUiState.pendingStagedImports]
 * (kept fresh by [com.BreadIQ.myapp.viewmodel.CalculatorViewModel.refreshPendingStagedImports])
 * is non-empty.
 *
 * Deliberately thin, matching the source exactly: picking a row only
 * calls [onSelect], which `CalculatorScreen` wires straight to
 * [com.BreadIQ.myapp.viewmodel.CalculatorViewModel.selectStagedImport] —
 * the EXISTING single-token `ImportStagingFetching` -> `CalculatorImportMapping`
 * -> `ImportReviewScreen` pipeline a Safari deep link already drives,
 * unchanged. This screen never touches ingredients, mapping, or
 * calculator state itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingImportsListScreen(
    items: List<StagedImportListItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PendingImportsHeader(onDismiss)

            if (items.isEmpty()) {
                PendingImportsEmptyState()
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    items.forEach { item ->
                        PendingImportRow(item = item, onTap = { onSelect(item.token) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingImportsHeader(onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text("Recipes from Your Browser", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Text("Staged from the Chrome extension", fontSize = 11.sp, color = colors.mutedForeground)
        }
        Icon(
            Icons.Filled.Cancel, contentDescription = "Close", tint = colors.mutedForeground,
            modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun PendingImportRow(item: StagedImportListItem, onTap: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primary.copy(alpha = 0.08f)),
        ) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                item.recipeName?.takeIf { it.isNotEmpty() } ?: "Untitled recipe",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground,
            )
            Text(domainFor(item.sourceURL), fontSize = 12.sp, color = colors.mutedForeground)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(12.dp))
    }
}

/**
 * `domain(for:)` — a different shape from [com.BreadIQ.myapp.core.ImportReviewFormatting.sourceDomain]
 * (returns "Unknown page" rather than null, and strips a leading "www."),
 * so kept local to this screen just like the source keeps its own
 * private `domain(for:)` rather than sharing one helper between the two.
 */
private fun domainFor(sourceURL: String?): String {
    if (sourceURL.isNullOrEmpty()) return "Unknown page"
    val host = android.net.Uri.parse(sourceURL).host ?: return "Unknown page"
    return host.removePrefix("www.")
}

@Composable
private fun PendingImportsEmptyState() {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        Icon(Icons.Filled.Inbox, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(28.dp))
        Text("Nothing waiting right now.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground)
    }
}
