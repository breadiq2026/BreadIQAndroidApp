package com.BreadIQ.myapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `Screens/DataStoreErrorScreen.swift`.
 *
 * Shown INSTEAD of the normal app when Room's eagerly-forced open
 * ([com.BreadIQ.myapp.data.local.DatabaseProvider.openEagerly]) fails at
 * launch — the direct analog of `BreadIQApp.swift`'s own
 * `if let modelContainer { RootView() } else { DataStoreErrorScreen() }`
 * branch, now real on Android too via `MainActivity.kt`'s own
 * `DbOpenState` gating. See [com.BreadIQ.myapp.data.local.BreadIQDatabase]'s
 * doc comment for why this needed real wiring, not a straight port, to
 * exist at all — Room's lazy `.build()` has no single "did construction
 * succeed" point the way SwiftData's eager `ModelContainer(for:
 * configurations:)` does. Deliberately doesn't touch Room/any DAO
 * anywhere, directly or indirectly, since the whole reason this screen
 * exists is that no working database may be available at all.
 *
 * **Two recovery actions, matching what's actually possible for this
 * failure class**: "Try Again" (cheap, harmless, occasionally actually
 * works — a transient I/O hiccup or a momentarily-full disk isn't
 * unheard of) and "Erase & Start Fresh" (destructive — deletes the
 * on-disk store outright — the only real recovery for genuine
 * corruption or a failed migration, gated behind the same double-confirm
 * pattern `SettingsScreen`'s account deletion already established,
 * since it's genuinely irreversible).
 */
@Composable
fun DataStoreErrorScreen(
    error: Throwable?,
    onRetry: () -> Unit,
    onEraseAndRetry: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    var showEraseConfirmStep1 by remember { mutableStateOf(false) }
    var showEraseConfirmStep2 by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().background(colors.background).padding(vertical = 32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text("⚠️", fontSize = 44.sp)
        Text("Something's Wrong", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text(
            "BreadIQ couldn't load its local data. This usually clears up on its own — try again first.",
            fontSize = 14.sp, color = colors.mutedForeground, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        if (error != null) {
            Text(
                error.message ?: error.toString(),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = colors.mutedForeground, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 4.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 12.dp),
        ) {
            BreadIQButton(label = "Try Again", variant = BreadIQButtonVariant.PRIMARY, fullWidth = true, onClick = onRetry)
            BreadIQButton(
                label = "Erase & Start Fresh", variant = BreadIQButtonVariant.DESTRUCTIVE, fullWidth = true,
                onClick = { showEraseConfirmStep1 = true },
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }

    if (showEraseConfirmStep1) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmStep1 = false },
            title = { Text("Erase & Start Fresh") },
            text = { Text("This will permanently delete any bakes, queued/scheduled bakes, and saved recipes stored only on this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showEraseConfirmStep1 = false
                    showEraseConfirmStep2 = true
                }) { Text("Erase & Start Fresh", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { showEraseConfirmStep1 = false }) { Text("Cancel") } },
        )
    }

    if (showEraseConfirmStep2) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmStep2 = false },
            title = { Text("Are you sure?") },
            text = { Text("All local data will be permanently deleted. There is no recovery.") },
            confirmButton = {
                TextButton(onClick = {
                    showEraseConfirmStep2 = false
                    onEraseAndRetry()
                }) { Text("Yes, Erase Everything", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { showEraseConfirmStep2 = false }) { Text("Cancel") } },
        )
    }
}
