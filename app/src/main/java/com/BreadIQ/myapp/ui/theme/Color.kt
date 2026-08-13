package com.BreadIQ.myapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ported from the iOS app's `BreadIQColors.swift` (itself a port of
 * `constants/colors.ts` from the original Expo app). Keep these two
 * files' hex values in sync if the design tokens change on either
 * platform — there is no shared source of truth between them yet.
 */
object BreadIQLightColors {
    val text = Color(0xFF0F172A)
    val tint = Color(0xFF1B3A8C)

    val background = Color(0xFFFFFFFF)
    val foreground = Color(0xFF0F172A)

    val card = Color(0xFFFFFFFF)
    val cardForeground = Color(0xFF0F172A)

    val primary = Color(0xFF1B3A8C)
    val primaryForeground = Color(0xFFFFFFFF)

    val secondary = Color(0xFFF3F4F6)
    val secondaryForeground = Color(0xFF1B3A8C)

    val muted = Color(0xFFF3F4F6)
    val mutedForeground = Color(0xFF6E7480)

    val accent = Color(0xFFC4520A)
    val accentForeground = Color(0xFFFFFFFF)

    val destructive = Color(0xFFEF4444)
    val destructiveForeground = Color(0xFFFFFFFF)
    val destructiveBackground = Color(0xFFFEF2F2)

    val border = Color(0xFFE4E6ED)
    val input = Color(0xFFE4E6ED)

    val orange = Color(0xFFC4520A)
    val orangeLight = Color(0xFFFFF4EE)
    val navyLight = Color(0xFFEEF1FB)

    val success = Color(0xFF16A34A)
    val successBackground = Color(0xFFF0FDF4)
    val warning = Color(0xFFCA8A04)
    val warningBackground = Color(0xFFFEF9C3)
}

object BreadIQDarkColors {
    val text = Color(0xFFF1F5F9)
    val tint = Color(0xFF4A72D4)

    val background = Color(0xFF090F1E)
    val foreground = Color(0xFFF1F5F9)

    val card = Color(0xFF111827)
    val cardForeground = Color(0xFFF1F5F9)

    val primary = Color(0xFF4A72D4)
    val primaryForeground = Color(0xFFFFFFFF)

    val secondary = Color(0xFF1E2A45)
    val secondaryForeground = Color(0xFFA8B9E8)

    val muted = Color(0xFF1E2A45)
    val mutedForeground = Color(0xFF8896B3)

    val accent = Color(0xFFC4520A)
    val accentForeground = Color(0xFFFFFFFF)

    val destructive = Color(0xFFEF4444)
    val destructiveForeground = Color(0xFFFFFFFF)
    val destructiveBackground = Color(0xFF2A1414)

    val border = Color(0xFF1E2A45)
    val input = Color(0xFF1E2A45)

    val orange = Color(0xFFC4520A)
    val orangeLight = Color(0xFF2A1A0E)
    val navyLight = Color(0xFF131D35)

    val success = Color(0xFF4ADE80)
    val successBackground = Color(0xFF12261A)
    val warning = Color(0xFFFACC15)
    val warningBackground = Color(0xFF2A2410)
}

/** `colors.radius` — shared corner radius across every atom, ported as-is. */
val BreadIQCornerRadius = 6
