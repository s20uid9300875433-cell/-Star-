package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = DeepSurfaceVariant,
    onPrimaryContainer = CrimsonRedLight,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = DeepSurfaceVariant,
    onSecondaryContainer = CrimsonRedLight,
    tertiary = AccentGold,
    onTertiary = DeepObsidian,
    background = DeepObsidian,
    onBackground = TextPrimary,
    surface = DeepSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeepSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = Color(0xFFFF5252)
)

@Composable
fun CaroLineAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MahiAiTheme(
    content: @Composable () -> Unit
) {
    CaroLineAiTheme(content = content)
}
