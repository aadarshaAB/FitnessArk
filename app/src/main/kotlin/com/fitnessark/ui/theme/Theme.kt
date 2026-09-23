package com.fitnessark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors
val CyanPrimary = Color(0xFF00E5FF)
val CyanSecondary = Color(0xFF00B8D9)
val BackgroundDark = Color(0xFF0D0D0D)
val SurfaceDark = Color(0xFF1A1A2E)
val CardDark = Color(0xFF16213E)
val OnSurfaceDark = Color(0xFFE0E0E0)
val ErrorRed = Color(0xFFCF6679)
val SuccessGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFC107)

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D60),
    onPrimaryContainer = CyanPrimary,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF002B36),
    secondaryContainer = Color(0xFF003545),
    onSecondaryContainer = Color(0xFF80DEEA),
    tertiary = Color(0xFFB39DDB),
    onTertiary = Color(0xFF1A0033),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = ErrorRed,
    outline = Color(0xFF37474F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006780),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EAFF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4B6272),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE7FA),
    onSecondaryContainer = Color(0xFF071E2A),
    background = Color(0xFFF8FAFB),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF71787D)
)

@Composable
fun FitnessArkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FitnessArkTypography,
        content = content
    )
}
