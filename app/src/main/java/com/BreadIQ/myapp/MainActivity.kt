package com.BreadIQ.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.BreadIQ.myapp.navigation.BreadIQDestination
import com.BreadIQ.myapp.screens.CalculatorScreen
import com.BreadIQ.myapp.screens.CurrentBakeScreen
import com.BreadIQ.myapp.screens.LexiconScreen
import com.BreadIQ.myapp.screens.QueueScreen
import com.BreadIQ.myapp.screens.RecipesScreen
import com.BreadIQ.myapp.ui.theme.BreadIQTheme

/**
 * Entry point / replaces `RootView.swift` + `MainTabView.swift`. Hosts
 * the 5-tab bottom navigation shell; each tab body is a placeholder
 * pending its own porting pass (see PORTING_PLAN.md). Auth-gating
 * (RootView switches between AuthScreen and MainTabView based on
 * AuthStore's session) is not wired up yet — this always shows the
 * main tab shell.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BreadIQTheme {
                BreadIQApp()
            }
        }
    }
}

@Composable
fun BreadIQApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                BreadIQDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BreadIQDestination.CALCULATOR.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BreadIQDestination.CALCULATOR.route) { CalculatorScreen() }
            composable(BreadIQDestination.RECIPES.route) { RecipesScreen() }
            composable(BreadIQDestination.LEXICON.route) { LexiconScreen() }
            composable(BreadIQDestination.QUEUE.route) { QueueScreen() }
            composable(BreadIQDestination.CURRENT_BAKE.route) { CurrentBakeScreen() }
        }
    }
}
