package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.model.AppThemeMode
import com.example.model.MoreMenuItemId
import com.example.model.ThemeDisplayMode

typealias AppThemeMode = com.example.model.AppThemeMode
typealias MoreMenuItemId = com.example.model.MoreMenuItemId

// Geometric Balance Color Scheme (Clean #F7F9FC background, #6750A4 accent, #1A1C1E text)
private val GeometricBalanceColorScheme = lightColorScheme(
    primary = GeoPrimary,
    onPrimary = GeoOnPrimary,
    primaryContainer = GeoPrimaryContainer,
    onPrimaryContainer = GeoOnPrimaryContainer,
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = GeoBackground,
    onBackground = GeoOnBackground,
    surface = GeoSurface,
    onSurface = GeoOnSurface,
    surfaceVariant = GeoSurfaceVariant,
    onSurfaceVariant = GeoOnSurfaceVariant,
    outline = GeoOutline,
    outlineVariant = GeoOutlineVariant,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B)
)

private val PurpleColorScheme = GeometricBalanceColorScheme

private val GoldColorScheme = lightColorScheme(
    primary = GoldAccentPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF8E1),
    onPrimaryContainer = Color(0xFF5D4002),
    secondary = Color(0xFF795548),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEBE9),
    onSecondaryContainer = Color(0xFF271A11),
    background = GoldAccentBackground,
    onBackground = GeoOnBackground,
    surface = GoldAccentSurface,
    onSurface = GeoOnSurface,
    surfaceVariant = Color(0xFFF7F4EC),
    onSurfaceVariant = Color(0xFF73777F),
    outline = GoldAccentOutline,
    outlineVariant = Color(0xFFEFE9DC),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B)
)

// Dark Theme Variants
private val DarkGeometricBalanceColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF87171),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1515),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val DarkPurpleColorScheme = DarkGeometricBalanceColorScheme

private val DarkGoldColorScheme = darkColorScheme(
    primary = Color(0xFFFFD54F),
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF5D4037),
    onPrimaryContainer = Color(0xFFFFECB3),
    secondary = Color(0xFFD7CCC8),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF4E342E),
    onSecondaryContainer = Color(0xFFEFEBE9),
    background = Color(0xFF181511),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF181511),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF2E2720),
    onSurfaceVariant = Color(0xFFD7CCC8),
    outline = Color(0xFFA1887F),
    outlineVariant = Color(0xFF4E342E),
    error = Color(0xFFF87171),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1515),
    onErrorContainer = Color(0xFFFFDAD6)
)

// Dynamic status colors adapted to light/dark surfaces
val ColorScheme.statusGreen: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF4ADE80) else Color(0xFF16A34A)

val ColorScheme.statusGreenContainer: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF14301D) else Color(0xFFF0FDF4)

val ColorScheme.statusRed: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFFF87171) else Color(0xFFDC2626)

val ColorScheme.statusRedContainer: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF3B1515) else Color(0xFFFEF2F2)

val ColorScheme.statusBlue: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF60A5FA) else Color(0xFF2563EB)

val ColorScheme.statusBlueContainer: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF14243B) else Color(0xFFEFF6FF)

val ColorScheme.statusAmber: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFFFBBF24) else Color(0xFFD97706)

val ColorScheme.statusAmberContainer: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF382910) else Color(0xFFFFFBEB)

@Composable
fun SmallStoreTheme(
    themeMode: AppThemeMode = AppThemeMode.NEUTRAL,
    displayMode: ThemeDisplayMode = ThemeDisplayMode.LIGHT,
    content: @Composable () -> Unit
) {
    val isDark = when (displayMode) {
        ThemeDisplayMode.LIGHT -> false
        ThemeDisplayMode.DARK -> true
        ThemeDisplayMode.AUTO -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) {
        when (themeMode) {
            AppThemeMode.NEUTRAL -> DarkGeometricBalanceColorScheme
            AppThemeMode.PURPLE -> DarkPurpleColorScheme
            AppThemeMode.GOLD -> DarkGoldColorScheme
        }
    } else {
        when (themeMode) {
            AppThemeMode.NEUTRAL -> GeometricBalanceColorScheme
            AppThemeMode.PURPLE -> PurpleColorScheme
            AppThemeMode.GOLD -> GoldColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep backwards-compatibility for existing references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val displayMode = if (darkTheme) ThemeDisplayMode.DARK else ThemeDisplayMode.LIGHT
    SmallStoreTheme(themeMode = AppThemeMode.NEUTRAL, displayMode = displayMode, content = content)
}
