package com.BreadIQ.myapp.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ported from the iOS app's `BreadIQColors.swift` (itself a port of
 * `constants/colors.ts` from the original Expo app). Keep these two
 * files' hex values in sync if the design tokens change on either
 * platform — there is no shared source of truth between them yet.
 *
 * A plain interface, not just two standalone objects — added alongside
 * [LocalBreadIQColors] below (PORTING_PLAN.md's Calculator-screen step)
 * so shared atoms (`Card`, `Badge`, `BreadIQButton`, and every
 * Calculator card) can read "the current theme's full token set" once,
 * the same way iOS code calls `BreadIQColors.primary` and gets an
 * automatically-resolving light/dark value with zero explicit threading.
 * Material3's `ColorScheme` (already wired in `Theme.kt`) only has slots
 * for a handful of these tokens (`primary`, `background`, `surface`,
 * etc.) — most of this palette (`muted`, `orange`, `navyLight`,
 * `successBackground`, ...) has no Material3 equivalent at all, so it
 * needs its own composition-local rather than living on
 * `MaterialTheme.colorScheme`.
 */
interface BreadIQColorTokens {
    val text: Color
    val tint: Color

    val background: Color
    val foreground: Color

    val card: Color
    val cardForeground: Color

    val primary: Color
    val primaryForeground: Color

    val secondary: Color
    val secondaryForeground: Color

    val muted: Color
    val mutedForeground: Color

    val accent: Color
    val accentForeground: Color

    val destructive: Color
    val destructiveForeground: Color
    val destructiveBackground: Color

    val border: Color
    val input: Color

    val orange: Color
    val orangeLight: Color
    val navyLight: Color

    val success: Color
    val successBackground: Color
    val warning: Color
    val warningBackground: Color
}

object BreadIQLightColors : BreadIQColorTokens {
    override val text = Color(0xFF0F172A)
    override val tint = Color(0xFF1B3A8C)

    override val background = Color(0xFFFFFFFF)
    override val foreground = Color(0xFF0F172A)

    override val card = Color(0xFFFFFFFF)
    override val cardForeground = Color(0xFF0F172A)

    override val primary = Color(0xFF1B3A8C)
    override val primaryForeground = Color(0xFFFFFFFF)

    override val secondary = Color(0xFFF3F4F6)
    override val secondaryForeground = Color(0xFF1B3A8C)

    override val muted = Color(0xFFF3F4F6)
    override val mutedForeground = Color(0xFF6E7480)

    override val accent = Color(0xFFC4520A)
    override val accentForeground = Color(0xFFFFFFFF)

    override val destructive = Color(0xFFEF4444)
    override val destructiveForeground = Color(0xFFFFFFFF)
    override val destructiveBackground = Color(0xFFFEF2F2)

    override val border = Color(0xFFE4E6ED)
    override val input = Color(0xFFE4E6ED)

    override val orange = Color(0xFFC4520A)
    override val orangeLight = Color(0xFFFFF4EE)
    override val navyLight = Color(0xFFEEF1FB)

    override val success = Color(0xFF16A34A)
    override val successBackground = Color(0xFFF0FDF4)
    override val warning = Color(0xFFCA8A04)
    override val warningBackground = Color(0xFFFEF9C3)
}

object BreadIQDarkColors : BreadIQColorTokens {
    override val text = Color(0xFFF1F5F9)
    override val tint = Color(0xFF4A72D4)

    override val background = Color(0xFF090F1E)
    override val foreground = Color(0xFFF1F5F9)

    override val card = Color(0xFF111827)
    override val cardForeground = Color(0xFFF1F5F9)

    override val primary = Color(0xFF4A72D4)
    override val primaryForeground = Color(0xFFFFFFFF)

    override val secondary = Color(0xFF1E2A45)
    override val secondaryForeground = Color(0xFFA8B9E8)

    override val muted = Color(0xFF1E2A45)
    override val mutedForeground = Color(0xFF8896B3)

    override val accent = Color(0xFFC4520A)
    override val accentForeground = Color(0xFFFFFFFF)

    override val destructive = Color(0xFFEF4444)
    override val destructiveForeground = Color(0xFFFFFFFF)
    override val destructiveBackground = Color(0xFF2A1414)

    override val border = Color(0xFF1E2A45)
    override val input = Color(0xFF1E2A45)

    override val orange = Color(0xFFC4520A)
    override val orangeLight = Color(0xFF2A1A0E)
    override val navyLight = Color(0xFF131D35)

    override val success = Color(0xFF4ADE80)
    override val successBackground = Color(0xFF12261A)
    override val warning = Color(0xFFFACC15)
    override val warningBackground = Color(0xFF2A2410)
}

/** `colors.radius` — shared corner radius across every atom, ported as-is. */
val BreadIQCornerRadius = 6

/**
 * Provided by [com.BreadIQ.myapp.ui.theme.BreadIQTheme]. Defaults to the
 * light palette only as a non-crashing fallback for a `@Preview` that
 * doesn't wrap itself in `BreadIQTheme` — every real call site in the app
 * renders inside `BreadIQTheme`, which always overrides this.
 */
val LocalBreadIQColors = staticCompositionLocalOf<BreadIQColorTokens> { BreadIQLightColors }
