package com.fitnessapp.pulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseDarkColorScheme = darkColorScheme(
    background = AppBackground,
    surface = Surface,
    surfaceVariant = Surface2,
    primary = Ember,
    onPrimary = Color.White,
    secondary = Sage,
    onSecondary = SageOnColor,
    tertiary = Gold,
    onTertiary = GoldOnColor,
    onBackground = Paper,
    onSurface = Paper,
    error = Ember,
    outline = Line
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseDarkColorScheme,
        typography = PulseTypography,
        content = content
    )
}