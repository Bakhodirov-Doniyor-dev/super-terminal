package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenDark,
    secondary = SageGreenDark,
    tertiary = WarmClayDark,
    background = MineralSlateDarkBg,
    surface = MineralSlateDarkSurface,
    onPrimary = Color(0xFF0F2B1E),
    onSecondary = Color(0xFF1B2A22),
    onTertiary = Color(0xFF3F1B00),
    onBackground = LightTextDarkMode,
    onSurface = LightTextDarkMode,
    primaryContainer = Color(0xFF1A4331),
    onPrimaryContainer = Color(0xFFADF7D1),
    surfaceVariant = Color(0xFF2C3131),
    onSurfaceVariant = Color(0xFFDFE4E4),
    error = AlertRustDark,
    onError = Color(0xFF670012)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenLight,
    secondary = SageGreenLight,
    tertiary = WarmClayLight,
    background = WheatSandLightBg,
    surface = WheatSandLightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkTextLightMode,
    onSurface = DarkTextLightMode,
    primaryContainer = Color(0xFFD2F8D2),
    onPrimaryContainer = Color(0xFF023015),
    surfaceVariant = Color(0xFFE4E9E2),
    onSurfaceVariant = Color(0xFF434842),
    error = AlertRustLight,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
