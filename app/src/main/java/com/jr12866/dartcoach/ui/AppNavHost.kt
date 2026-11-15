package com.jr12866.dartcoach.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jr12866.dartcoach.core.Planner
import com.jr12866.dartcoach.core.ProfileView
import com.jr12866.dartcoach.core.StatsView
import com.jr12866.dartcoach.entitlement.Entitlement

/** The routes the app has. */
object Routes {
    /** The live coach. */
    const val COACH = "coach"

    /** The out chart. */
    const val OUT_CHART = "outchart"

    /** The paywall. */
    const val PAYWALL = "paywall"

    /** The player's own profile. */
    const val PROFILE = "profile"

    /** The setup chart: the middle of the leg. */
    const val SETUP = "setup"

    /** The player's own statistics. */
    const val STATS = "stats"

    /** The settings, including the echo delay measurement. */
    const val SETTINGS = "settings"
}

/**
 * The app's navigation.
 *
 * There is one screen to start with and two more behind it. The nav host is
 * here rather than in the activity so that the screens themselves stay
 * about darts and not about plumbing.
 */
@Composable
fun AppNavHost(
    remaining: Int,
    turns: Int,
    suggestion: String,
    message: String,
    double: String,
    listening: Boolean,
    turnDarts: Int,
    turnScore: Int,
    planKind: String,
    correctionAvailable: Boolean,
    entitlement: Entitlement,
    planner: Planner,
    price: String?,
    purchaseError: String?,
    profileView: ProfileView,
    statsView: StatsView,
    measuredTail: Double?,
    measuringTail: Boolean,
    tailMessage: String,
    onNewGame: () -> Unit,
    onUndo: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onSaveProfile: (ProfileView) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onMeasureTail: () -> Unit,
    onResetTail: () -> Unit,
) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.COACH) {
        composable(Routes.COACH) {
            CoachScreen(
                remaining = remaining,
                turns = turns,
                suggestion = suggestion,
                message = message,
                double = double,
                listening = listening,
                turnDarts = turnDarts,
                turnScore = turnScore,
                planKind = planKind,
                correctionAvailable = correctionAvailable,
                onNewGame = onNewGame,
                onUndo = onUndo,
                onPrivacyPolicy = onPrivacyPolicy,
                onOpenOutChart = { nav.navigate(Routes.OUT_CHART) },
                onOpenSetup = { nav.navigate(Routes.SETUP) },
                onOpenProfile = {
                    // The view is refreshed as well as the screen opened,
                    // so that the numbers are current when it appears.
                    onOpenProfile()
                    nav.navigate(Routes.PROFILE)
                },
                onOpenSettings = {
                    onOpenSettings()
                    nav.navigate(Routes.SETTINGS)
                },
                onRestore = onRestore,
            )
        }

        composable(Routes.OUT_CHART) {
            OutChartScreen(
                planner = planner,
                entitlement = entitlement,
                onBack = { nav.popBackStack() },
                onUpgrade = {
                    nav.navigate(Routes.PAYWALL)
                },
                onOpenSetup = { nav.navigate(Routes.SETUP) },
            )
        }

        composable(Routes.PAYWALL) {
            PaywallScreen(
                price = price,
                error = purchaseError,
                onBack = { nav.popBackStack() },
                onPurchase = onPurchase,
                onRestore = onRestore,
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                view = profileView,
                entitlement = entitlement,
                onBack = { nav.popBackStack() },
                onSave = onSaveProfile,
                onUpgrade = { nav.navigate(Routes.PAYWALL) },
                onOpenStats = { nav.navigate(Routes.STATS) },
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                planner = planner,
                isPro = entitlement.has(Entitlement.Feature.SETUP_COACHING),
                onBack = { nav.popBackStack() },
                onUpgrade = { nav.navigate(Routes.PAYWALL) },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                view = statsView,
                isPro = entitlement.has(Entitlement.Feature.SESSION_STATS),
                onBack = { nav.popBackStack() },
                onUpgrade = { nav.navigate(Routes.PAYWALL) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                measuredTail = measuredTail,
                measuring = measuringTail,
                tailMessage = tailMessage,
                onBack = { nav.popBackStack() },
                onMeasureTail = onMeasureTail,
                onResetTail = onResetTail,
            )
        }
    }
}
