package cz.jaro.gymceska.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

actual fun areDynamicColorsSupported() = false

@Composable
actual fun dynamicDarkColorScheme(): ColorScheme = error("Dynamic color schemes are on supported in the browser")

@Composable
actual fun dynamicLightColorScheme(): ColorScheme = error("Dynamic color schemes are on supported in the browser")

@Composable
actual fun SetStatusBarColor(statusBarColor: Color, isAppearanceLightStatusBars: Boolean) {}