package kr.co.nuni.gildam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/** 색 팔레트 후보. 기본은 CLASSIC(현재 숲색). 최종 선택 전까지 기본값을 바꾸지 않습니다. */
enum class GildamPalette(val labelKo: String) {
    CLASSIC("숲길 (현재)"),
    SUNNY("A · 햇살 산책"),
    MINT("B · 민트 소풍"),
    SUNSET("C · 노을 러닝")
}

private val SunnyLight = lightColorScheme(
    primary = Color(0xFFF2552C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCF), onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF1FA65A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F2D6), onSecondaryContainer = Color(0xFF002110),
    tertiary = Color(0xFFFFB400), onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFFFFE9B0), onTertiaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFFFF8F3), surface = Color(0xFFFFF8F3), surfaceVariant = Color(0xFFFFEADF),
    onBackground = Color(0xFF231A16), onSurface = Color(0xFF231A16), onSurfaceVariant = Color(0xFF6B5A52),
    outline = Color(0xFFB09A90), outlineVariant = Color(0xFFEBD6CC)
)
private val SunnyDark = darkColorScheme(
    primary = Color(0xFFFF8A65), onPrimary = Color(0xFF3B0A00),
    primaryContainer = Color(0xFF7A2510), onPrimaryContainer = Color(0xFFFFDCCF),
    secondary = Color(0xFF5FD98E), onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF0D5230), onSecondaryContainer = Color(0xFFC9F2D6),
    tertiary = Color(0xFFFFCC4D), onTertiary = Color(0xFF3D2A00),
    background = Color(0xFF16110F), surface = Color(0xFF16110F), surfaceVariant = Color(0xFF2A201C),
    onBackground = Color(0xFFF3E6E0), onSurface = Color(0xFFF3E6E0), onSurfaceVariant = Color(0xFFC7B3AA),
    outline = Color(0xFF8F7A71), outlineVariant = Color(0xFF3E322D)
)
private val MintLight = lightColorScheme(
    primary = Color(0xFF00A39A), onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2EA), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFFFFB81C), onSecondary = Color(0xFF2B1F00),
    secondaryContainer = Color(0xFFFFEBB0), onSecondaryContainer = Color(0xFF2B1F00),
    tertiary = Color(0xFF3FD69A), onTertiary = Color(0xFF00301B),
    tertiaryContainer = Color(0xFFC4F7DE), onTertiaryContainer = Color(0xFF00301B),
    background = Color(0xFFF4FCFA), surface = Color(0xFFF4FCFA), surfaceVariant = Color(0xFFDDF3EE),
    onBackground = Color(0xFF13201E), onSurface = Color(0xFF13201E), onSurfaceVariant = Color(0xFF4C625E),
    outline = Color(0xFF8AA39E), outlineVariant = Color(0xFFC8E3DD)
)
private val MintDark = darkColorScheme(
    primary = Color(0xFF4FDBD0), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFB8F2EA),
    secondary = Color(0xFFFFCB52), onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300), onSecondaryContainer = Color(0xFFFFEBB0),
    tertiary = Color(0xFF7BEFBF), onTertiary = Color(0xFF00391F),
    background = Color(0xFF0E1615), surface = Color(0xFF0E1615), surfaceVariant = Color(0xFF1B2826),
    onBackground = Color(0xFFDDEDEA), onSurface = Color(0xFFDDEDEA), onSurfaceVariant = Color(0xFFA5BDB8),
    outline = Color(0xFF6F8884), outlineVariant = Color(0xFF2C3D3A)
)
private val SunsetLight = lightColorScheme(
    primary = Color(0xFF6A3DE8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DDFF), onPrimaryContainer = Color(0xFF1E0060),
    secondary = Color(0xFFFF4D7E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2), onSecondaryContainer = Color(0xFF3E0019),
    tertiary = Color(0xFFFF9A1F), onTertiary = Color(0xFF2E1500),
    tertiaryContainer = Color(0xFFFFE2C2), onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFCF9FF), surface = Color(0xFFFCF9FF), surfaceVariant = Color(0xFFEFE9FB),
    onBackground = Color(0xFF1C1A24), onSurface = Color(0xFF1C1A24), onSurfaceVariant = Color(0xFF5C5670),
    outline = Color(0xFF9A93AE), outlineVariant = Color(0xFFDCD4EE)
)
private val SunsetDark = darkColorScheme(
    primary = Color(0xFFB9A3FF), onPrimary = Color(0xFF2F0D8A),
    primaryContainer = Color(0xFF4A22B8), onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Color(0xFFFF85A6), onSecondary = Color(0xFF5E1131),
    secondaryContainer = Color(0xFF7D2748), onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFFFB866), onTertiary = Color(0xFF4A2800),
    background = Color(0xFF13111A), surface = Color(0xFF13111A), surfaceVariant = Color(0xFF221E2E),
    onBackground = Color(0xFFE8E2F2), onSurface = Color(0xFFE8E2F2), onSurfaceVariant = Color(0xFFB7AFC9),
    outline = Color(0xFF7F7894), outlineVariant = Color(0xFF332D44)
)

fun gildamColorScheme(palette: GildamPalette, dark: Boolean) = when (palette) {
    GildamPalette.CLASSIC -> if (dark) DarkNightOledScheme else LightWarmEditorialScheme
    GildamPalette.SUNNY -> if (dark) SunnyDark else SunnyLight
    GildamPalette.MINT -> if (dark) MintDark else MintLight
    GildamPalette.SUNSET -> if (dark) SunsetDark else SunsetLight
}

@Composable
fun GildamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: GildamPalette = GildamPalette.CLASSIC,
    content: @Composable () -> Unit
) {
    val colorScheme = gildamColorScheme(palette, darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GildamTypography,
        content = content
    )
}
