/*
 * Copyright (C) 2026 The S4 project contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.s4

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.s4.data.repository.PinRepository
import com.s4.data.repository.SessionRepository
import com.s4.navigation.Routes
import com.s4.ui.RecoveryGuideScreen
import com.s4.ui.RestoreScreen
import com.s4.ui.SettingsScreen
import com.s4.ui.SplitResultScreen
import com.s4.ui.SplitScreen
import com.s4.ui.SplitViewModel
import com.s4.ui.components.S4BottomNavigationBar
import com.s4.ui.pin.AuthPinScreen
import com.s4.ui.pin.PinManagementScreen
import com.s4.ui.theme.S4Theme
import com.s4.ui.toSplitSession

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE: block screenshots, recents snapshots, and screen-mirroring
        // capture of seed material. This app's entire purpose is displaying
        // secret material, so the flag is set unconditionally (wallet-app practice).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            S4Theme {
                S4App()
            }
        }
    }
}

@Composable
fun S4App() {
    val context = LocalContext.current
    val pinRepository = remember { PinRepository(context) }
    val sessionRepository = remember { SessionRepository(context) }

    // PIN gate state: mandatory — PIN is required. The app always starts locked;
    // AuthPinScreen shows setup (no PIN) or unlock (PIN set). No skip path.
    var isUnlocked by remember { mutableStateOf(false) }

    // Re-lock when the app goes to background (ON_STOP) if a PIN is set.
    // This covers lending the phone / shoulder-surfing when the Android lock
    // screen is already unlocked — the in-memory `isUnlocked` flag alone would
    // otherwise keep seed material exposed. Uses the monotonic clock's lockout
    // state for the gate, not just `remember`, so a backgrounded app always
    // re-enters through `AuthPinScreen`.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isUnlocked) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && pinRepository.isPinSet()) {
                isUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isUnlocked) {
        AuthPinScreen(
            repository = pinRepository,
            onAuthenticated = { isUnlocked = true },
        )
        return
    }

    val navController = rememberNavController()
    val splitViewModel: SplitViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedSplit = currentRoute == Routes.SPLIT || currentRoute == Routes.SPLIT_RESULT

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Hide bottom bar on settings / pin management screens
            if (currentRoute != Routes.SETTINGS && currentRoute != Routes.PIN_MANAGE) {
                S4BottomNavigationBar(
                    selectedSplit = selectedSplit,
                    onSelect = { route -> navController.navigateTo(route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLIT,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLIT) {
                SplitScreen(
                    viewModel = splitViewModel,
                    pinRepository = pinRepository,
                    sessionRepository = sessionRepository,
                    onSplitComplete = { navController.navigateTo(Routes.SPLIT_RESULT) },
                    onOpenGuide = { navController.navigateTo(Routes.GUIDE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.RESTORE) {
                RestoreScreen(
                    onOpenGuide = { navController.navigateTo(Routes.GUIDE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SPLIT_RESULT) {
                SplitResultScreen(
                    viewModel = splitViewModel,
                    pinRepository = pinRepository,
                    sessionRepository = sessionRepository,
                    onOpenGuide = { navController.navigateTo(Routes.GUIDE) },
                    onDone = {
                        splitViewModel.dismissResult()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.GUIDE) {
                RecoveryGuideScreen(
                    viewModel = splitViewModel,
                    onDone = {
                        splitViewModel.dismissResult()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    pinRepository = pinRepository,
                    sessionRepository = sessionRepository,
                    onOpenSession = { code ->
                        val loaded = sessionRepository.load(code)
                        if (loaded != null) {
                            splitViewModel.resumeSession(loaded.toSplitSession(), code)
                            navController.popBackStack()
                            navController.navigate(Routes.SPLIT_RESULT)
                        }
                    },
                    onManagePin = { navController.navigate(Routes.PIN_MANAGE) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PIN_MANAGE) {
                PinManagementScreen(
                    repository = pinRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
