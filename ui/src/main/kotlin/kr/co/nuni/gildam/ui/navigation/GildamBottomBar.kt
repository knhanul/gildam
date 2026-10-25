package kr.co.nuni.gildam.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 하단 탭 3개: 기록 / 기록 목록 / 설정 */
enum class GildamTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any
) {
    RECORD("기록", Icons.Filled.FiberManualRecord, Icons.Outlined.FiberManualRecord, LiveRecordingRoute),
    LIST("기록 목록", Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted, ActivityListRoute),
    SETTINGS("설정", Icons.Filled.Settings, Icons.Outlined.Settings, SettingsRoute)
}

@Composable
fun GildamBottomBar(
    currentTab: GildamTab?,
    onSelectTab: (GildamTab) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.onSurfaceVariant.copy(alpha = 0.12f))
        NavigationBar(
            containerColor = colors.surfaceVariant,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp
        ) {
            GildamTab.entries.forEach { tab ->
                val selected = tab == currentTab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onPrimaryContainer,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}
