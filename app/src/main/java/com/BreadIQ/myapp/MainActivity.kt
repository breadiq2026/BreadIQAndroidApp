package com.BreadIQ.myapp

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.BreadIQ.myapp.core.DeepLinkDestination
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.deepLinkDestination
import com.BreadIQ.myapp.data.TemperatureUnitStore
import com.BreadIQ.myapp.data.local.DatabaseProvider
import com.BreadIQ.myapp.navigation.BreadIQDestination
import com.BreadIQ.myapp.navigation.BreadIQRoutes
import com.BreadIQ.myapp.screens.AuthScreen
import com.BreadIQ.myapp.screens.DataStoreErrorScreen
import com.BreadIQ.myapp.screens.SetNewPasswordScreen
import com.BreadIQ.myapp.ui.bakedetail.BakeDetailScreen
import com.BreadIQ.myapp.ui.calculator.AutolyseGuidanceScreen
import com.BreadIQ.myapp.ui.calculator.CalculatorScreen
import com.BreadIQ.myapp.ui.calculator.ImportScreen
import com.BreadIQ.myapp.ui.calculator.NutritionAnalysisScreen
import com.BreadIQ.myapp.ui.currentbake.CurrentBakeScreen
import com.BreadIQ.myapp.ui.lexicon.LexiconScreen
import com.BreadIQ.myapp.ui.queue.QueueScreen
import com.BreadIQ.myapp.ui.recipes.RecipesScreen
import com.BreadIQ.myapp.ui.schedule.ScheduleScreen
import com.BreadIQ.myapp.ui.settings.ConnectBrowserScreen
import com.BreadIQ.myapp.ui.settings.IngredientCostsScreen
import com.BreadIQ.myapp.ui.settings.SettingsScreen
import com.BreadIQ.myapp.ui.theme.BreadIQTheme
import com.BreadIQ.myapp.ui.subscription.SubscriptionScreen
import com.BreadIQ.myapp.viewmodel.AuthViewModel
import com.BreadIQ.myapp.viewmodel.AuthViewModelFactory
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.CalculatorViewModelFactory
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModel
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModelFactory
import com.BreadIQ.myapp.viewmodel.autolyseGuidance
import com.BreadIQ.myapp.viewmodel.selectedShape

/**
 * Entry point / replaces `RootView.swift` + `MainTabView.swift`. Hosts
 * the 5-tab bottom navigation shell — all five tabs (Calculator,
 * Recipes, Lexicon, Queue, Current Bake) are now real, plus the pushed
 * Bake Detail and Schedule destinations (see PORTING_PLAN.md).
 *
 * **Auth-gating now wired up** (PORTING_PLAN.md step 3), matching
 * `RootView.swift`'s three-state split — loading / signed-out /
 * signed-in — via `AuthViewModel.uiState`: a loading spinner while the
 * initial session check is in flight, [AuthScreen] when there's no
 * session, the tab shell when there is. **Now also matches `RootView.swift`'s
 * password-recovery branch, checked ahead of the signed-out/signed-in
 * split** — a pending `DeepLinkDestination.PasswordRecovery` means "set
 * a new password" regardless of `currentUser`, since a recovery session
 * is limited/task-specific, not a normal sign-in (see [DeepLinkDestination]'s
 * own doc comment for the deep-link infrastructure that produces it).
 * Deliberately still narrower than `RootView.swift` in one respect: no
 * bake-session reconciliation (that depends on a ported feature —
 * `BakeSessionEngine` — that doesn't exist here yet).
 *
 * **Deep-link delivery, `singleTask` + `onNewIntent`**: `AndroidManifest.xml`'s
 * `MainActivity` `<activity>` now declares `android:launchMode="singleTask"`,
 * so a deep link tapped while this Activity is already running/backgrounded
 * reuses the live instance and calls [onNewIntent] instead of spawning a
 * second `MainActivity` on top of it (which would never reach the
 * composition below at all). [pendingDeepLinkUri] captures both delivery
 * paths — [onCreate]'s own `intent?.data` for a cold launch via link, and
 * [onNewIntent] for an already-running one — the direct analog of the
 * source's two delivery paths (`Linking.getInitialURL()` /
 * `Linking.addEventListener("url", ...)`) funneling into the same
 * `handleURLDeepLink`.
 *
 * **A second, OUTER gate now wraps all of the above — [DbOpenState]** —
 * the direct analog of `BreadIQApp.swift`'s own `if let modelContainer
 * { RootView() /* includes its own auth branching */ } else {
 * DataStoreErrorScreen() }`. The entire auth-gated block above (loading/
 * signed-out/signed-in) only ever renders once Room's eagerly-forced
 * open ([DatabaseProvider.openEagerly]) has actually succeeded — see
 * [DbOpenState]'s own doc comment for why this ordering, not the new
 * screen's visual layout, is the actual point of this restructuring.
 */
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(applicationContext)
    }

    /**
     * Activity-scoped, matching [SubscriptionViewModel]'s own doc
     * comment on why — a single shared instance readable from this
     * Activity's login/logout binding below, from `CalculatorViewModel`
     * (via [CalculatorViewModelFactory]), and from `SubscriptionScreen`
     * itself.
     */
    private val subscriptionViewModel: SubscriptionViewModel by viewModels {
        SubscriptionViewModelFactory(applicationContext)
    }

    /**
     * Single shared instance, constructed once — same reasoning as
     * [subscriptionViewModel] above, applied to a second cross-cutting
     * preference with the identical "must propagate live to an
     * already-alive `CalculatorViewModel`" shape (see
     * [TemperatureUnitStore]'s own doc comment). Not a `ViewModel`
     * itself: it exposes a `StateFlow` but has no coroutine work of its
     * own beyond that, so a plain `by lazy` field is enough — no
     * `ViewModelProvider.Factory` needed.
     */
    private val temperatureUnitStore: TemperatureUnitStore by lazy {
        TemperatureUnitStore(applicationContext.getSharedPreferences("breadiq_prefs", android.content.Context.MODE_PRIVATE))
    }

    /**
     * Requested once, early — see `core/BakeNotificationScheduler.kt`'s
     * own doc comment on why this can't be lazy the way the iOS source's
     * `requestAuthorization()` is (Android's runtime permission dialog
     * can only be shown through a live Activity's launcher; none of this
     * app's ViewModels have one). No-op result callback: every real
     * schedule call re-checks the permission itself right before
     * scheduling, matching the source's own per-call guard — this
     * launcher's only job is to show the system dialog once.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * The most recently received deep-link `Uri`, from either delivery
     * path (see this class's own doc comment). An Activity field (`by
     * mutableStateOf`), not `remember`ed inside the composable, since
     * [onNewIntent] runs outside composition and needs somewhere to
     * actually write the new value.
     */
    private var pendingDeepLinkUri by mutableStateOf<Uri?>(null)

    /**
     * The token from a captured `DeepLinkDestination.ImportToken` — set,
     * never yet read. `PendingImportsListScreen` (a separate, explicitly
     * out-of-scope follow-up session) is the intended consumer; this
     * session only makes sure a tapped import link isn't silently
     * dropped on the floor.
     */
    private var pendingImportToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBakeNotificationPermissionsIfNeeded()
        pendingDeepLinkUri = intent?.data
        setContent {
            BreadIQTheme {
                val appContext = applicationContext
                var dbOpenState by remember { mutableStateOf<DbOpenState>(DbOpenState.Checking) }
                // Incremented by DataStoreErrorScreen's "Try Again"/
                // "Erase & Start Fresh" actions to re-trigger the
                // LaunchedEffect below without relaunching the app —
                // the direct analog of the source's own onRetry/
                // onEraseAndRetry closures re-assigning modelContainer.
                var dbRetryKey by remember { mutableIntStateOf(0) }

                LaunchedEffect(dbRetryKey) {
                    dbOpenState = DbOpenState.Checking
                    dbOpenState = DatabaseProvider.openEagerly(appContext).fold(
                        onSuccess = { DbOpenState.Ready },
                        onFailure = { DbOpenState.Failed(it) },
                    )
                }

                when (val state = dbOpenState) {
                    DbOpenState.Checking -> LoadingScreen()
                    is DbOpenState.Failed -> DataStoreErrorScreen(
                        error = state.error,
                        onRetry = { dbRetryKey++ },
                        onEraseAndRetry = {
                            DatabaseProvider.eraseLocalStore(appContext)
                            dbRetryKey++
                        },
                    )
                    DbOpenState.Ready -> {
                        val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

                        // RevenueCat identity binding (PORTING_PLAN.md step 6) —
                        // the direct analog of RootView.swift's own AuthPhase/
                        // subscriptionAction(for:) three-way branch. Deliberately
                        // keyed on currentUser?.id alone, not the whole
                        // CurrentUser? value — see authPhase()'s own doc comment.
                        val phase = authPhase(isLoading = uiState.isLoading, userId = uiState.currentUser?.id)
                        LaunchedEffect(phase) {
                            when (val action = subscriptionAction(phase)) {
                                is SubscriptionBindingAction.Login -> subscriptionViewModel.login(action.userId)
                                SubscriptionBindingAction.Logout -> subscriptionViewModel.logout()
                                null -> Unit
                            }
                        }

                        // Deep-link routing (see this class's own doc
                        // comment) — the direct analog of AppRouter's
                        // handleURLDeepLink applying urlDeepLinkDestination's
                        // decision.
                        val destination = remember(pendingDeepLinkUri) {
                            pendingDeepLinkUri?.let { deepLinkDestination(it) } ?: DeepLinkDestination.None
                        }
                        LaunchedEffect(destination) {
                            // ImportToken clears pendingDeepLinkUri but keeps
                            // pendingImportToken set — see that field's own
                            // doc comment on why nothing consumes it yet.
                            if (destination is DeepLinkDestination.ImportToken) {
                                pendingImportToken = destination.token
                                pendingDeepLinkUri = null
                            }
                        }

                        // Matches RootView.swift's real branch order exactly:
                        // isLoading, THEN pendingPasswordRecovery (regardless
                        // of currentUser), THEN signed-out, THEN signed-in.
                        when {
                            uiState.isLoading -> LoadingScreen()
                            destination is DeepLinkDestination.PasswordRecovery -> SetNewPasswordScreen(
                                accessToken = destination.accessToken,
                                refreshToken = destination.refreshToken,
                                authViewModel = authViewModel,
                                onComplete = { pendingDeepLinkUri = null },
                            )
                            uiState.currentUser == null -> AuthScreen(authViewModel)
                            else -> BreadIQApp(authViewModel, subscriptionViewModel, temperatureUnitStore)
                        }
                    }
                }
            }
        }
    }

    /**
     * `POST_NOTIFICATIONS` (API 33+ runtime permission, real system
     * dialog) and `SCHEDULE_EXACT_ALARM` (API 31+; no in-app dialog
     * exists for it on API 33+, only a Settings deep link) — both gate
     * real notification scheduling in `BakeNotificationScheduler`. Both
     * checks short-circuit if already decided, same "ask once" shape as
     * the source's own `requestAuthorization()`, just triggered from app
     * launch instead of the first schedule call (see that file's own
     * doc comment for why).
     *
     * **Known rough edge, left as-is deliberately**: if the user denies
     * `SCHEDULE_EXACT_ALARM`, this redirects to Settings again on every
     * subsequent launch until they grant it — a production app would
     * likely gate this behind a dedicated in-app settings toggle instead
     * of an unconditional launch-time prompt. Not built here; flagged
     * rather than silently accepted.
     */
    private fun requestBakeNotificationPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() == false) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
    }

    /**
     * Reached only because `AndroidManifest.xml` declares
     * `android:launchMode="singleTask"` on this Activity — without it,
     * a deep link tapped while already running would spawn a second
     * `MainActivity` instance instead of delivering here. `setIntent(intent)`
     * keeps `getIntent()` consistent with what actually arrived, matching
     * the standard `onNewIntent` contract.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkUri = intent.data
    }
}

/**
 * The three states `RootView.swift`'s own auth-gating effect actually
 * distinguishes.
 *
 * **Deliberately keyed on `currentUser?.id`, not the whole `CurrentUser?`
 * value.** The source's effect depends on Supabase's `session` object
 * specifically — which changes on sign-in/out/token refresh — NOT on
 * `userName`, a separate piece of state updated independently via
 * `AuthStore.setDisplayName`. If this were keyed on the full
 * `CurrentUser` (which also carries `displayName`), a display-name edit
 * would spuriously re-fire this binding, redundantly re-calling
 * `subscriptionViewModel.login(id)` with the same id. `.id` alone
 * captures exactly the transitions that should actually re-fire this —
 * signed in vs. out, and which user — without that spurious re-fire.
 */
private sealed class AuthPhase {
    data object Loading : AuthPhase()
    data object SignedOut : AuthPhase()
    data class SignedIn(val userId: String) : AuthPhase()
}

private fun authPhase(isLoading: Boolean, userId: String?): AuthPhase = when {
    isLoading -> AuthPhase.Loading
    userId != null -> AuthPhase.SignedIn(userId)
    else -> AuthPhase.SignedOut
}

private sealed class SubscriptionBindingAction {
    data class Login(val userId: String) : SubscriptionBindingAction()
    data object Logout : SubscriptionBindingAction()
}

/** `nil` while still loading — the source's `if (loading) return;` early exit, doing neither a login nor a logout call yet. */
private fun subscriptionAction(phase: AuthPhase): SubscriptionBindingAction? = when (phase) {
    AuthPhase.Loading -> null
    AuthPhase.SignedOut -> SubscriptionBindingAction.Logout
    is AuthPhase.SignedIn -> SubscriptionBindingAction.Login(phase.userId)
}

/**
 * The three states this Activity's outer, database-open gate actually
 * distinguishes — the direct analog of `BreadIQApp.swift`'s own
 * `modelContainer`/`modelContainerError` pair, just widened to a real
 * sealed type instead of two independently-nilable `@State` fields,
 * since Compose has no single-assignment-then-branch idiom that maps
 * onto two separately-optional properties as cleanly as SwiftUI's `if
 * let` does.
 *
 * **Why this gate has to wrap the entire auth-gated block, not sit
 * alongside it**: `CalculatorViewModelFactory`/`IngredientCostsViewModelFactory`/
 * every other DB-touching factory in this app calls
 * `DatabaseProvider.getInstance(...)` and assumes it just works — none
 * of them are equipped to handle a broken database. As long as
 * [DbOpenState.Ready] is required before [AuthScreen]/[BreadIQApp] ever
 * render (exactly like iOS's `RootView` sits entirely inside the `if
 * let modelContainer` branch), none of those factories can ever be
 * reached with a broken database underneath them.
 */
private sealed class DbOpenState {
    data object Checking : DbOpenState()
    data object Ready : DbOpenState()
    data class Failed(val error: Throwable?) : DbOpenState()
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun BreadIQApp(
    authViewModel: AuthViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    temperatureUnitStore: TemperatureUnitStore,
) {
    val navController = rememberNavController()

    // Recipes' "Load into Calculator" handoff — the Compose counterpart
    // of AppRouter.pendingRecipe (set alongside a tab switch, consumed
    // and cleared by the Calculator route once). A plain remembered
    // value at this shared composable scope rather than a new app-wide
    // router class, since this is the only cross-tab handoff that
    // exists on Android so far — see CalculatorViewModel.loadFromRecipe's
    // own doc comment for the consumption side.
    var pendingRecipeId by remember { mutableStateOf<Int?>(null) }

    // Calculator's "Schedule Bake" / Current Bake's "Reschedule" handoff
    // to the Schedule route — same shape as pendingRecipeId above, just
    // carrying a RawScheduledBakePlan instead of a recipe id. Consumed
    // once by the Schedule route's own composable body below.
    var pendingSchedulePlan by remember { mutableStateOf<RawScheduledBakePlan?>(null) }

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
                val calculatorViewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModelFactory(context, subscriptionViewModel, temperatureUnitStore))
                LaunchedEffect(pendingRecipeId) {
                    val id = pendingRecipeId ?: return@LaunchedEffect
                    calculatorViewModel.loadFromRecipe(id)
                    pendingRecipeId = null
                }
                CalculatorScreen(
                    viewModel = calculatorViewModel,
                    onOpenNutrition = { navController.navigate(BreadIQRoutes.NUTRITION_ANALYSIS) },
                    onOpenAutolyse = { navController.navigate(BreadIQRoutes.AUTOLYSE_GUIDANCE) },
                    onOpenImport = { navController.navigate(BreadIQRoutes.IMPORT) },
                    onOpenSubscription = { navController.navigate(BreadIQRoutes.SUBSCRIPTION) },
                    onOpenSettings = { navController.navigate(BreadIQRoutes.SETTINGS) },
                    onStartedBake = { sessionId -> navController.navigate("bake_detail/$sessionId") },
                    onScheduleBake = { plan ->
                        pendingSchedulePlan = plan
                        navController.navigate(BreadIQRoutes.SCHEDULE)
                    },
                )
            }
            // Both detail screens below read from the SAME CalculatorViewModel
            // instance as the Calculator route (scoped to that back stack
            // entry) rather than passing FormulaResult/AutolyseGuidance through
            // route arguments — see BreadIQRoutes' own doc comment.
            composable(BreadIQRoutes.NUTRITION_ANALYSIS) {
                val context = LocalContext.current
                val parentEntry = remember { navController.getBackStackEntry(BreadIQDestination.CALCULATOR.route) }
                val calculatorViewModel: CalculatorViewModel = viewModel(parentEntry, factory = CalculatorViewModelFactory(context, subscriptionViewModel, temperatureUnitStore))
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
                val calculatorViewModel: CalculatorViewModel = viewModel(parentEntry, factory = CalculatorViewModelFactory(context, subscriptionViewModel, temperatureUnitStore))
                val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
                AutolyseGuidanceScreen(guidance = state.autolyseGuidance, onDismiss = { navController.popBackStack() })
            }
            // Does NOT read from the Calculator route's shared CalculatorViewModel
            // the way the two routes above do — ImportScreen is genuinely
            // self-contained (its own ImportViewModel, no "Apply to
            // Calculator" action), see its own doc comment.
            composable(BreadIQRoutes.IMPORT) {
                ImportScreen(onClose = { navController.popBackStack() })
            }
            // Also doesn't read from the Calculator route's shared
            // CalculatorViewModel — reads the shared SubscriptionViewModel
            // directly instead (the same instance this whole BreadIQApp
            // was handed), matching SubscriptionScreen.swift's own
            // `@Environment(SubscriptionStore.self)` access.
            composable(BreadIQRoutes.SUBSCRIPTION) {
                SubscriptionScreen(viewModel = subscriptionViewModel, onClose = { navController.popBackStack() })
            }
            // Settings + Connect a Browser (execution-order item 7) — reads
            // the same shared authViewModel/subscriptionViewModel/
            // temperatureUnitStore this whole BreadIQApp was handed, not
            // fresh instances, matching SettingsScreen.swift's own
            // `@Environment` access to all three source stores.
            composable(BreadIQRoutes.SETTINGS) {
                SettingsScreen(
                    authViewModel = authViewModel,
                    subscriptionViewModel = subscriptionViewModel,
                    temperatureUnitStore = temperatureUnitStore,
                    onOpenSubscription = { navController.navigate(BreadIQRoutes.SUBSCRIPTION) },
                    onOpenConnectBrowser = { navController.navigate(BreadIQRoutes.CONNECT_BROWSER) },
                    onOpenIngredientCosts = { navController.navigate(BreadIQRoutes.INGREDIENT_COSTS) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(BreadIQRoutes.CONNECT_BROWSER) {
                ConnectBrowserScreen(onClose = { navController.popBackStack() })
            }
            // Ingredient Costs ("Ingredient Library") — reads the same
            // shared subscriptionViewModel for its premium gate, not a
            // fresh instance, matching SettingsScreen's own access.
            composable(BreadIQRoutes.INGREDIENT_COSTS) {
                IngredientCostsScreen(
                    subscriptionViewModel = subscriptionViewModel,
                    onOpenSubscription = { navController.navigate(BreadIQRoutes.SUBSCRIPTION) },
                    onClose = { navController.popBackStack() },
                )
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
            composable(BreadIQDestination.QUEUE.route) {
                QueueScreen(
                    onStartedBake = { sessionId -> navController.navigate("bake_detail/$sessionId") },
                )
            }
            composable(BreadIQDestination.CURRENT_BAKE.route) {
                CurrentBakeScreen(
                    onOpenBakeDetail = { sessionId -> navController.navigate("bake_detail/$sessionId") },
                    onReschedule = { plan ->
                        pendingSchedulePlan = plan
                        navController.navigate(BreadIQRoutes.SCHEDULE)
                    },
                )
            }
            composable(
                route = BreadIQRoutes.BAKE_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                BakeDetailScreen(
                    sessionId = sessionId,
                    onDismiss = { navController.popBackStack() },
                )
            }
            composable(BreadIQRoutes.SCHEDULE) {
                val plan = pendingSchedulePlan
                if (plan == null) {
                    // Route was reached without a plan handed off (e.g. process
                    // restore) — nothing to schedule, bounce back immediately.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ScheduleScreen(
                        plan = plan,
                        onCancel = {
                            pendingSchedulePlan = null
                            navController.popBackStack()
                        },
                        onScheduled = {
                            pendingSchedulePlan = null
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
