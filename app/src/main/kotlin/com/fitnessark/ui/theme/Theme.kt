package com.fitnessark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// One teal accent (hue ~190) in both themes. Dark mode steps up through neutral greys instead of
// navy so the surfaces are easy to tell apart; green is only ever the "saved / done" colour.
val TealDark = Color(0xFF4FC3DC)
val TealLight = Color(0xFF00708A)
val BackgroundDark = Color(0xFF121416)
val SurfaceDark = Color(0xFF1A1D20)
val CardDark = Color(0xFF262B2F)
val OnSurfaceDark = Color(0xFFE3E6E8)
val ErrorRed = Color(0xFFEC8A98)
val SuccessGreen = Color(0xFF4CAF50)

private val DarkColorScheme = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF00313D),
    primaryContainer = Color(0xFF0F4A5A),
    onPrimaryContainer = Color(0xFFC2EDF8),
    secondary = Color(0xFF93B5C1),
    onSecondary = Color(0xFF0E2A33),
    secondaryContainer = Color(0xFF2A4650),
    onSecondaryContainer = Color(0xFFCDE7F0),
    tertiary = Color(0xFFB39DDB),
    onTertiary = Color(0xFF1A0033),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = Color(0xFFB4BCC2),
    error = ErrorRed,
    outline = Color(0xFF4A5359)
)

private val LightColorScheme = lightColorScheme(
    primary = TealLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBEE9F4),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4B6272),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE7FA),
    onSecondaryContainer = Color(0xFF071E2A),
    tertiary = Color(0xFF6B4FA3),
    background = Color(0xFFF6F9FA),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE1E8EC),
    onSurfaceVariant = Color(0xFF41484D),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF71787D)
)

/** "Saved / done" green, readable on the current theme's surfaces (never used for good-vs-bad). */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SuccessGreen else Color(0xFF1B5E20)

@Composable
fun FitnessArkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FitnessArkTypography,
        shapes = FitnessArkShapes,
        content = content
    )
}
