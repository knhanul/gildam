package kr.co.nuni.gildam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightWarmEditorialScheme = lightColorScheme(
    primary = ForestGreenLight,
    onPrimary = OnForestGreenLight,
    primaryContainer = ForestGreenContainerLight,
    onPrimaryContainer = OnForestGreenContainerLight,
    secondary = TerracottaRouteLight,
    onSecondary = OnTerracottaLight,
    secondaryContainer = TerracottaContainerLight,
    tertiary = SlateWaypointLight,
    background = WarmIvoryBackground,
    surface = WarmIvoryBackground,
    surfaceVariant = SandStoneSurfaceContainer,
    onBackground = OnWarmPaper,
    onSurface = OnWarmPaper,
    onSurfaceVariant = OnWarmPaperVariant
)

private val DarkNightOledScheme = darkColorScheme(
    primary = SageGreenDark,
    onPrimary = OnSageGreenDark,
    primaryContainer = ForestContainerDark,
    onPrimaryContainer = OnForestContainerDark,
    secondary = WarmCoralRouteDark,
    tertiary = MistBlueWaypointDark,
    background = TrueDarkCharcoalBackground,
    surface = TrueDarkCharcoalBackground,
    surfaceVariant = ElevatedDarkSurfaceContainer,
    onBackground = OnDarkSurface,
    onSurface = OnDarkSurface,
    onSurfaceVariant = OnDarkSurfaceVariant
)

@Composable
fun GildamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkNightOledScheme else LightWarmEditorialScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GildamTypography,
        content = content
    )
}
