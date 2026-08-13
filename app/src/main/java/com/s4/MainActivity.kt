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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.s4.navigation.Routes
import com.s4.ui.RecoveryGuideScreen
import com.s4.ui.RestoreScreen
import com.s4.ui.SplitResultScreen
import com.s4.ui.SplitScreen
import com.s4.ui.SplitViewModel
import com.s4.ui.components.S4BottomNavigationBar
import com.s4.ui.theme.S4Theme

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
            S4BottomNavigationBar(
                selectedSplit = selectedSplit,
                onSelect = { route -> navController.navigateTo(route) },
            )
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
                    onSplitComplete = { navController.navigateTo(Routes.SPLIT_RESULT) },
                    onOpenGuide = { navController.navigateTo(Routes.GUIDE) },
                )
            }
            composable(Routes.RESTORE) {
                RestoreScreen(onOpenGuide = { navController.navigateTo(Routes.GUIDE) })
            }
            composable(Routes.SPLIT_RESULT) {
                SplitResultScreen(
                    viewModel = splitViewModel,
                    onOpenGuide = { navController.navigateTo(Routes.GUIDE) },
                    onDone = {
                        // Drop the in-memory session (shares + entropy) when the user
                        // finishes — the Recovery Guide must not re-show this wallet later.
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
