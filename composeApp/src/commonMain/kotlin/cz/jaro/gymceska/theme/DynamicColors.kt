package cz.jaro.gymceska.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

expect fun areDynamicColorsSupported(): Boolean

@Composable
expect fun dynamicDarkColorScheme(): ColorScheme

@Composable
expect fun dynamicLightColorScheme(): ColorScheme

@Composable
expect fun SetStatusBarColor(statusBarColor: Color, isAppearanceLightStatusBars: Boolean)