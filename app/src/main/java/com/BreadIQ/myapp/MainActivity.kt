package com.BreadIQ.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.BreadIQ.myapp.navigation.BreadIQDestination
import com.BreadIQ.myapp.navigation.BreadIQRoutes
import com.BreadIQ.myapp.screens.AuthScreen
import com.BreadIQ.myapp.screens.CurrentBakeScreen
import com.BreadIQ.myapp.screens.QueueScreen
import com.BreadIQ.myapp.ui.calculator.AutolyseGuidanceScreen
import com.BreadIQ.myapp.ui.calculator.CalculatorScreen
import com.BreadIQ.myapp.ui.calculator.NutritionAnalysisScreen
import com.BreadIQ.myapp.ui.lexicon.LexiconScreen
import com.BreadIQ.myapp.ui.recipes.RecipesScreen
import com.BreadIQ.myapp.ui.theme.BreadIQTheme
import com.BreadIQ.myapp.viewmodel.AuthViewModel
import com.BreadIQ.myapp.viewmodel.AuthViewModelFactory
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.CalculatorViewModelFactory
import com.BreadIQ.myapp.viewmodel.autolyseGuidance
import com.BreadIQ.myapp.viewmodel.selectedShape

/**
 * Entry point / replaces `RootView.swift` + `MainTabView.swift`. Hosts
 * the 5-tab bottom navigation shell; each tab body is a placeholder
 * pending its own porting pass (see PORTING_PLAN.md).
 *
 * **Auth-gating now wired up** (PORTING_PLAN.md step 3), matching
 * `RootView.swift`'s three-state split — loading / signed-out /
 * signed-in — via `AuthViewModel.uiState`: a loading spinner while the
 * initial session check is in flight, [AuthScreen] when there's no
 * session, the tab shell when there is. Deliberately narrower than
 * `RootView.swift`, which this same three-way split sits inside of on
 * iOS: no password-recovery deep-link branch (no deep-link infra exists
 * on Android yet — see `AuthServicing`'s own doc comment), no bake-session
 * reconciliation or subscription-store login/logout binding (those depend
 * on ported features — `BakeSessionEngine`, `SubscriptionStore` — that
 * don't exist here yet either). Just the auth split, which is what this
 * step's source-file list actually asked for.
 */
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BreadIQTheme {
                val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
                when {
                    uiState.isLoading -> LoadingScreen()
                    uiState.currentUser == null -> AuthScreen(authViewModel)
                    else -> BreadIQApp()
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun BreadIQApp() {
    val navController = rememberNavController()

    // Recipes' "Load into Calculator" handoff — the Compose counterpart
    // of AppRouter.pendingRecipe (set alongside a tab switch, consumed
    // and cleared by the Calculator route once). A plain remembered
    // value at this shared composable scope rather than a new app-wide
    // router class, since this is the only cross-tab handoff that
    // exists on Android so far — see CalculatorViewModel.loadFromRecipe's
    // own doc comment for the consumption side.
    var pendingRecipeId by remember { mutableStateOf<Int?>(null) }

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
            composable(BreadIQDestination.CALCULATOR.route) {
                val context = LocalContext.current
                val calculatorViewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModelFactory(context))
                LaunchedEffect(pendingRecipeId) {
                    val id = pendingRecipeId ?: return@LaunchedEffect
                    calculatorViewModel.loadFromRecipe(id)
                    pendingRecipeId = null
                }
                CalculatorScreen(
                    viewModel = calculatorViewModel,
                    onOpenNutrition = { navController.navigate(BreadIQRoutes.NUTRITION_ANALYSIS) },
                    onOpenAutolyse = { navController.navigate(BreadIQRoutes.AUTOLYSE_GUIDANCE) },
                )
            }
            // Both detail screens below read from the SAME CalculatorViewModel
            // instance as the Calculator route (scoped to that back stack
            // entry) rather than passing FormulaResult/AutolyseGuidance through
            // route arguments — see BreadIQRoutes' own doc comment.
            composable(BreadIQRoutes.NUTRITION_ANALYSIS) {
                val context = LocalContext.current
                val parentEntry = remember { navController.getBackStackEntry(BreadIQDestination.CALCULATOR.route) }
                val calculatorViewModel: CalculatorViewModel = viewModel(parentEntry, factory = CalculatorViewModelFactory(context))
                val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
                state.formulaResult?.let { formulaResult ->
                    NutritionAnalysisScreen(
                        result = formulaResult, styleValue = state.selectedStyle.value, styleLabel = state.selectedStyle.label,
                        yeastType = state.yeastType, sweetenerType = state.sweetenerType, blend = state.flourBlend,
                        shapeValue = state.selectedShapeValue, shapeLabel = state.selectedShape?.label ?: state.selectedShapeValue,
                        numLoaves = state.numLoaves.toInt(),
                        onDismiss = { navController.popBackStack() },
                    )
                }
            }
            composable(BreadIQRoutes.AUTOLYSE_GUIDANCE) {
                val context = LocalContext.current
                val parentEntry = remember { navController.getBackStackEntry(BreadIQDestination.CALCULATOR.route) }
                val calculatorViewModel: CalculatorViewModel = viewModel(parentEntry, factory = CalculatorViewModelFactory(context))
                val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
                AutolyseGuidanceScreen(guidance = state.autolyseGuidance, onDismiss = { navController.popBackStack() })
            }
            composable(BreadIQDestination.RECIPES.route) {
                RecipesScreen(
                    onLoadIntoCalculator = { recipeId ->
                        pendingRecipeId = recipeId
                        navController.navigate(BreadIQDestination.CALCULATOR.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(BreadIQDestination.LEXICON.route) { LexiconScreen() }
            composable(BreadIQDestination.QUEUE.route) { QueueScreen() }
            composable(BreadIQDestination.CURRENT_BAKE.route) { CurrentBakeScreen() }
        }
    }
}
