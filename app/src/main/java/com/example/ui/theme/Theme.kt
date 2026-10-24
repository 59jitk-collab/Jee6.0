package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlueLight,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueSubtle,
    onPrimaryContainer = ElectricBlueLight,
    secondary = SoftGreen,
    onSecondary = Color.White,
    secondaryContainer = SlateCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = WarmAmber,
    onTertiary = Color.Black,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = WarmAmber,
    error = CoralRed,
    onError = Color.White,
    errorContainer = CoralRedContainer,
    onErrorContainer = CoralRed,
    background = DeepNavyBg,
    onBackground = TextPrimary,
    surface = SlateCard,
    onSurface = TextPrimary,
    surfaceVariant = SlateCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateCardBorder,
    outlineVariant = SlateCardBorderSubtle
)

@Composable
fun JeeStudyOSTheme(
    darkTheme: Boolean = true,
    dimmerMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveScheme = if (dimmerMode) {
        DarkColorScheme.copy(
            background = Color(0xFF04070D),
            surface = Color(0xFF090E17),
            surfaceVariant = Color(0xFF0E1624),
            outline = Color(0xFF162136)
        )
    } else {
        DarkColorScheme
    }

    MaterialTheme(
        colorScheme = effectiveScheme,
        typography = Typography,
        content = content
    )
}
