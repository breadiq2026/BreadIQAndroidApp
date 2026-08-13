package com.BreadIQ.myapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ported from the iOS app's `MainTabView.swift`, which itself replaces
 * `(tabs)/_layout.tsx` from the original Expo app. Order, labels, and
 * icon intent match exactly: Calculator, Recipes, Lexicon, Queue,
 * Current Bake. Icons are Material equivalents of iOS's SF Symbols
 * ("function", "book", "text.book.closed", "tray", "timer") — exact
 * glyphs differ between icon sets, matched for intent/recognizability
 * rather than pixel-for-pixel.
 */
enum class BreadIQDestination(val route: String, val label: String, val icon: ImageVector) {
    CALCULATOR(route = "calculator", label = "Calculator", icon = Icons.Filled.Calculate),
    RECIPES(route = "recipes", label = "Recipes", icon = Icons.Filled.MenuBook),
    LEXICON(route = "lexicon", label = "Lexicon", icon = Icons.Outlined.Bookmark),
    QUEUE(route = "queue", label = "Queue", icon = Icons.Filled.Inbox),
    CURRENT_BAKE(route = "current_bake", label = "Current Bake", icon = Icons.Filled.Timer),
}

/**
 * Additional pushed/sheet-presented routes from `AppRouter.swift`
 * (bake detail, settings, ingredient costs, connect browser, the
 * subscription paywall sheet). Stubbed out for now — wired up to real
 * NavHost destinations as each screen gets ported. See PORTING_PLAN.md.
 */
object BreadIQRoutes {
    const val BAKE_DETAIL = "bake_detail/{sessionId}"
    const val SETTINGS = "settings"
    const val INGREDIENT_COSTS = "ingredient_costs"
    const val CONNECT_BROWSER = "connect_browser"
    const val SUBSCRIPTION = "subscription"
}
