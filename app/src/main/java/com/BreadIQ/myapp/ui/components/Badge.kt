package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.ui.theme.BreadIQColorTokens
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `UI/Badge.swift`.
 *
 * `success`/`destructive` already read from [BreadIQColorTokens]'s
 * light/dark-adapting `successBackground`/`destructiveBackground`
 * tokens — the iOS file's own doc comment describes fixing a real
 * dark-mode gap where those two variants used to be bare, non-adaptive
 * hex literals; this port only ever had the already-fixed token-based
 * version to work from, so there's no equivalent gap to reproduce here.
 */
enum class BadgeVariant { PRIMARY, ORANGE, MUTED, SUCCESS, DESTRUCTIVE }

private fun BadgeVariant.backgroundColor(colors: BreadIQColorTokens): Color = when (this) {
    BadgeVariant.PRIMARY -> colors.navyLight
    BadgeVariant.ORANGE -> colors.orangeLight
    BadgeVariant.MUTED -> colors.muted
    BadgeVariant.SUCCESS -> colors.successBackground
    BadgeVariant.DESTRUCTIVE -> colors.destructiveBackground
}

private fun BadgeVariant.foregroundColor(colors: BreadIQColorTokens): Color = when (this) {
    BadgeVariant.PRIMARY -> colors.primary
    BadgeVariant.ORANGE -> colors.orange
    BadgeVariant.MUTED -> colors.mutedForeground
    BadgeVariant.SUCCESS -> colors.success
    BadgeVariant.DESTRUCTIVE -> colors.destructive
}

@Composable
fun Badge(
    label: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.MUTED,
) {
    val colors = LocalBreadIQColors.current
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = variant.foregroundColor(colors),
        modifier = modifier
            .clip(CircleShape)
            .background(variant.backgroundColor(colors))
            .padding(PaddingValues(horizontal = 8.dp, vertical = 3.dp)),
    )
}
