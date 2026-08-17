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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.ConnectBrowserFormatting
import com.BreadIQ.myapp.core.PairingCode
import com.BreadIQ.myapp.data.BackendApiClient
import com.BreadIQ.myapp.data.BackendPairingCodeGenerator
import com.BreadIQ.myapp.data.SupabaseAuthService
import com.BreadIQ.myapp.data.SupabaseClientProvider
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ported from the iOS app's `Screens/ConnectBrowserScreen.swift`
 * (minus `ConnectBrowserFormatting`, split out into its own file).
 *
 * "Connect a Browser" — generates a short-lived pairing code for the
 * Chrome-extension companion (`BreadIQChromeExtension/auth.js`'s
 * `redeemPairingCode`), the desktop-to-phone counterpart to the Safari
 * extension's same-device deep link. Pushed from `SettingsScreen` via
 * [com.BreadIQ.myapp.navigation.BreadIQRoutes.CONNECT_BROWSER].
 *
 * ██ STUB-BACKEND ██ — [com.BreadIQ.myapp.core.PairingCodeGenerating.generateCode]
 * (`POST /api/auth/pairing-code/generate`) does not exist on `api-server`
 * yet. This screen is fully built against the agreed contract and renders
 * its real error state today (the code/countdown UI simply never appears
 * until the backend ships) — nothing about the screen itself is a
 * placeholder.
 *
 * **Live countdown**: the source uses a Combine
 * `Timer.publish(every: 1, on: .main, in: .common)`; the direct Compose
 * equivalent is a [LaunchedEffect] running a `while (true) { delay(1000) }`
 * loop — cancelled automatically when this composable leaves composition,
 * unlike the source's `.autoconnect()` timer which lives for the view's
 * lifetime by construction (no manual teardown needed either way).
 */
@Composable
fun ConnectBrowserScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ██ STUB-BACKEND ██ — see this file's own header.
    val pairingCodeGenerator = remember {
        BackendPairingCodeGenerator(
            BackendApiClient(accessTokenProvider = {
                SupabaseAuthService(SupabaseClientProvider.getInstance(context.applicationContext)).currentAccessToken()
            }),
        )
    }

    var pairingCode by remember { mutableStateOf<PairingCode?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }

    val isExpired = pairingCode?.let { ConnectBrowserFormatting.isExpired(it.expiresAt, now) } ?: false

    suspend fun generateCode() {
        isLoading = true
        errorMessage = null
        pairingCodeGenerator.generateCode().fold(
            onSuccess = { code ->
                pairingCode = code
                now = Instant.now()
            },
            onFailure = { error ->
                pairingCode = null
                errorMessage = error.message
            },
        )
        isLoading = false
    }

    LaunchedEffect(Unit) { generateCode() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ConnectBrowserHeader(onClose = onClose)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExplainerCard()
            when {
                isLoading -> LoadingCard()
                pairingCode != null && !isExpired -> CodeCard(pairingCode!!, now)
                errorMessage != null -> ErrorCard(errorMessage!!)
            }
            GenerateButton(
                isLoading = isLoading,
                label = generateButtonLabel(pairingCode, errorMessage, isExpired),
                onClick = { scope.launch { generateCode() } },
            )
        }
    }
}

private fun generateButtonLabel(pairingCode: PairingCode?, errorMessage: String?, isExpired: Boolean): String =
    if (pairingCode == null && errorMessage == null) "Generate Code" else if (isExpired) "Generate New Code" else "Regenerate Code"

@Composable
private fun ConnectBrowserHeader(onClose: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 14.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.foreground, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Connect a Browser", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("Pair the Chrome extension", fontSize = 11.sp, color = colors.mutedForeground)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun ExplainerCard() {
    val colors = LocalBreadIQColors.current
    Text(
        "Install the BreadIQ extension in Chrome, then enter the code below there to link it to your account. Recipes you stage from Chrome will show up here next time you open BreadIQ.",
        fontSize = 13.sp, color = colors.mutedForeground,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(14.dp),
    )
}

@Composable
private fun LoadingCard() {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(24.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Generating code…", fontSize = 13.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun CodeCard(pairingCode: PairingCode, now: Instant) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.navyLight)
            .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(vertical = 28.dp),
    ) {
        Text(
            ConnectBrowserFormatting.displayCode(pairingCode.code),
            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace,
        )
        Text(
            "Expires in ${ConnectBrowserFormatting.countdownText(pairingCode.expiresAt, now)}",
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Text(
        "⚠ $message", fontSize = 12.sp, color = Color(0xFF92400E),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFFBEB)).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp)).padding(10.dp),
    )
}

@Composable
private fun GenerateButton(isLoading: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primary)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
