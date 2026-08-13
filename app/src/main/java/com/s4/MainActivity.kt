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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.s4.ui.RecoveryGuideScreen
import com.s4.ui.RestoreScreen
import com.s4.ui.SplitResultScreen
import com.s4.ui.SplitScreen
import com.s4.ui.SplitViewModel
import com.s4.ui.theme.MonoMeta
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
    val selectedSplit = currentRoute == SPLIT_ROUTE || currentRoute == SPLIT_RESULT_ROUTE

    Scaffold(
        modifier              = Modifier.fillMaxSize(),
        containerColor        = MaterialTheme.colorScheme.background,
        contentWindowInsets   = WindowInsets(0, 0, 0, 0),
        bottomBar             = {
            S4BottomNavigationBar(
                selectedSplit = selectedSplit,
                onSelect      = { route -> navController.navigateTo(route) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = SPLIT_ROUTE,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(SPLIT_ROUTE) {
                SplitScreen(
                    viewModel      = splitViewModel,
                    onSplitComplete = { navController.navigateTo(SPLIT_RESULT_ROUTE) },
                    onOpenGuide    = { navController.navigateTo(GUIDE_ROUTE) },
                )
            }
            composable(RESTORE_ROUTE) {
                RestoreScreen(onOpenGuide = { navController.navigateTo(GUIDE_ROUTE) })
            }
            composable(SPLIT_RESULT_ROUTE) {
                SplitResultScreen(
                    viewModel    = splitViewModel,
                    onOpenGuide  = { navController.navigateTo(GUIDE_ROUTE) },
                    onDone       = {
                        // Drop the in-memory session (shares + entropy) when the user
                        // finishes — the Recovery Guide must not re-show this wallet later.
                        splitViewModel.dismissResult()
                        navController.popBackStack()
                    },
                )
            }
            composable(GUIDE_ROUTE) {
                RecoveryGuideScreen(
                    viewModel = splitViewModel,
                    onDone    = {
                        splitViewModel.dismissResult()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

/** Header bar: brand header mark with app name and hairline separator. */
@Composable
fun S4HeaderBar() {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "S4",
                    style = MonoMeta.value.copy(
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 9.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text  = "Shamir's Secret Sharing",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = "SLIP-39 · offline · open source",
                    style = MonoMeta.value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(outlineColor),
        )
    }
}

/** Authentic Material 3 Bottom Navigation Bar for Split / Restore. */
@Composable
private fun S4BottomNavigationBar(selectedSplit: Boolean, onSelect: (String) -> Unit) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(outlineColor),
        )
        NavigationBar(
            containerColor = surfaceColor,
            contentColor   = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            modifier       = Modifier.fillMaxWidth(),
        ) {
            NavigationBarItem(
                selected = selectedSplit,
                onClick  = { onSelect(SPLIT_ROUTE) },
                icon     = {
                    Icon(
                        imageVector        = SplitIcon,
                        contentDescription = "Split",
                    )
                },
                label    = {
                    Text(
                        text  = "Split",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tabSplit"),
            )
            NavigationBarItem(
                selected = !selectedSplit,
                onClick  = { onSelect(RESTORE_ROUTE) },
                icon     = {
                    Icon(
                        imageVector        = RestoreIcon,
                        contentDescription = "Restore",
                    )
                },
                label    = {
                    Text(
                        text  = "Restore",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tabRestore"),
            )
        }
    }
}

// ── Custom M3 Navigation Icons ───────────────────────────────────────────────

private val SplitIcon: ImageVector = ImageVector.Builder(
    name = "SplitIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color(0xFF818CF8)),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(16f, 3f)
        lineTo(21f, 3f)
        lineTo(21f, 8f)
        moveTo(4f, 20f)
        lineTo(21f, 3f)
        moveTo(21f, 16f)
        lineTo(21f, 21f)
        lineTo(16f, 21f)
        moveTo(15f, 15f)
        lineTo(21f, 21f)
        moveTo(4f, 4f)
        lineTo(9f, 9f)
    }
}.build()

private val RestoreIcon: ImageVector = ImageVector.Builder(
    name = "RestoreIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color(0xFF818CF8)),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(3f, 12f)
        curveTo(3f, 7.029f, 7.029f, 3f, 12f, 3f)
        curveTo(16.971f, 3f, 21f, 7.029f, 21f, 12f)
        curveTo(21f, 16.971f, 16.971f, 21f, 12f, 21f)
        curveTo(8.5f, 21f, 5.5f, 19f, 4f, 16f)
        moveTo(3f, 8f)
        lineTo(3f, 12f)
        lineTo(7f, 12f)
    }
}.build()

private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

private const val SPLIT_ROUTE        = "split"
private const val RESTORE_ROUTE      = "restore"
private const val SPLIT_RESULT_ROUTE = "splitResult"
private const val GUIDE_ROUTE        = "recoveryGuide"
