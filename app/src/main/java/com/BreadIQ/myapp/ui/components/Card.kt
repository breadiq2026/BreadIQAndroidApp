package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.BreadIQ.myapp.ui.theme.BreadIQCornerRadius
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `UI/Card.swift`.
 *
 * The source's shadow parameters (offset, opacity, radius) don't
 * actually change with `elevated` — only the shadow COLOR toggles
 * between black and fully transparent, so a non-elevated card has zero
 * visible shadow. Matched here by simply not applying the `shadow`
 * modifier at all when `elevated` is false, rather than porting a
 * literal "shadow with a transparent color" (Compose's `shadow`
 * modifier has no color parameter to make transparent in the first
 * place — elevation is what drives it).
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    val shape = RoundedCornerShape(BreadIQCornerRadius.dp)
    Box(
        modifier = modifier
            .let { if (elevated) it.shadow(elevation = 2.dp, shape = shape, clip = false) else it }
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(padding),
    ) {
        content()
    }
}
