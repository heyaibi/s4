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

package com.s4.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.R
import com.s4.navigation.Routes
import com.s4.ui.icons.RestoreIcon
import com.s4.ui.icons.SplitIcon
import com.s4.ui.theme.MonoMeta

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
            verticalAlignment = Alignment.CenterVertically,
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
                    text  = stringResource(R.string.app_name),
                    style = MonoMeta.value.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text  = stringResource(R.string.header_brand_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = stringResource(R.string.header_brand_subtitle),
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
fun S4BottomNavigationBar(selectedSplit: Boolean, onSelect: (String) -> Unit) {
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
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NavigationBarItem(
                selected = selectedSplit,
                onClick = { onSelect(Routes.SPLIT) },
                icon = {
                    Icon(
                        imageVector = SplitIcon,
                        contentDescription = stringResource(R.string.nav_split),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_split),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = navItemColors(),
                modifier = Modifier.testTag("tabSplit"),
            )
            NavigationBarItem(
                selected = !selectedSplit,
                onClick = { onSelect(Routes.RESTORE) },
                icon = {
                    Icon(
                        imageVector = RestoreIcon,
                        contentDescription = stringResource(R.string.nav_restore),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_restore),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = navItemColors(),
                modifier = Modifier.testTag("tabRestore"),
            )
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
