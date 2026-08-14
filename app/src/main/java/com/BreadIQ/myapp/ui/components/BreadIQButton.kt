package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.ui.theme.BreadIQCornerRadius
import com.BreadIQ.myapp.ui.theme.BreadIQColorTokens
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `UI/BreadIQButton.swift`.
 *
 * **A real bug the iOS port caught only by looking at a screenshot, not
 * the build, carried forward here deliberately**: dimming must key off
 * [disabled] alone, never off [loading] — `loading` blocks interaction
 * but should not visually dim (the source's own
 * `opacity: disabled ? 0.5 : 1` never referenced loading either). The
 * iOS file's own doc comment tells the story of an earlier draft that
 * read from an environment-wide "is this interactive" signal instead of
 * an explicit `isDisabled` and got this wrong; ported as a direct
 * `disabled` check here for the same reason that fix exists.
 */
enum class BreadIQButtonVariant { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE, ORANGE }

private fun BreadIQButtonVariant.backgroundColor(colors: BreadIQColorTokens): Color = when (this) {
    BreadIQButtonVariant.PRIMARY -> colors.primary
    BreadIQButtonVariant.SECONDARY -> colors.secondary
    BreadIQButtonVariant.GHOST -> Color.Transparent
    BreadIQButtonVariant.DESTRUCTIVE -> colors.destructive
    BreadIQButtonVariant.ORANGE -> colors.orange
}

private fun BreadIQButtonVariant.foregroundColor(colors: BreadIQColorTokens): Color = when (this) {
    BreadIQButtonVariant.PRIMARY -> colors.primaryForeground
    BreadIQButtonVariant.SECONDARY -> colors.foreground
    BreadIQButtonVariant.GHOST -> colors.mutedForeground
    BreadIQButtonVariant.DESTRUCTIVE -> colors.destructiveForeground
    BreadIQButtonVariant.ORANGE -> Color.White
}

/**
 * The source gives both `secondary` AND `ghost` a 1px border, but
 * `ghost`'s border color is literally "transparent" — a 1px transparent
 * border renders identically to no border at all, so this collapses to
 * just `secondary`, matching the iOS port's own simplification.
 */
private val BreadIQButtonVariant.hasBorder: Boolean
    get() = this == BreadIQButtonVariant.SECONDARY

@Composable
fun BreadIQButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BreadIQButtonVariant = BreadIQButtonVariant.PRIMARY,
    disabled: Boolean = false,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val colors = LocalBreadIQColors.current
    val shape = RoundedCornerShape(BreadIQCornerRadius.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha = when {
        disabled -> 0.5f
        isPressed -> 0.75f
        else -> 1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .let { if (fullWidth) it.fillMaxWidth() else it }
            .heightIn(min = 44.dp)
            .alpha(alpha)
            .clip(shape)
            .background(variant.backgroundColor(colors))
            .border(if (variant.hasBorder) 1.dp else 0.dp, colors.border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !disabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = variant.foregroundColor(colors),
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = label,
                color = variant.foregroundColor(colors),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            )
        }
    }
}
