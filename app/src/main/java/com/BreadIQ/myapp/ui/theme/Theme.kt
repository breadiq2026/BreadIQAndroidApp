package com.BreadIQ.myapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = BreadIQLightColors.primary,
    onPrimary = BreadIQLightColors.primaryForeground,
    secondary = BreadIQLightColors.secondary,
    onSecondary = BreadIQLightColors.secondaryForeground,
    background = BreadIQLightColors.background,
    onBackground = BreadIQLightColors.foreground,
    surface = BreadIQLightColors.card,
    onSurface = BreadIQLightColors.cardForeground,
    error = BreadIQLightColors.destructive,
    onError = BreadIQLightColors.destructiveForeground,
    outline = BreadIQLightColors.border,
)

private val DarkColorScheme = darkColorScheme(
    primary = BreadIQDarkColors.primary,
    onPrimary = BreadIQDarkColors.primaryForeground,
    secondary = BreadIQDarkColors.secondary,
    onSecondary = BreadIQDarkColors.secondaryForeground,
    background = BreadIQDarkColors.background,
    onBackground = BreadIQDarkColors.foreground,
    surface = BreadIQDarkColors.card,
    onSurface = BreadIQDarkColors.cardForeground,
    error = BreadIQDarkColors.destructive,
    onError = BreadIQDarkColors.destructiveForeground,
    outline = BreadIQDarkColors.border,
)

/**
 * Root theme wrapper, applied once in [com.BreadIQ.myapp.MainActivity].
 * Deliberately does NOT opt into Android 12+ dynamic (wallpaper-derived)
 * color — BreadIQ has its own fixed brand palette ported from iOS, the
 * same way the iOS app ignores system accent color.
 */
@Composable
fun BreadIQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val breadIQColors = if (darkTheme) BreadIQDarkColors else BreadIQLightColors
    CompositionLocalProvider(LocalBreadIQColors provides breadIQColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
