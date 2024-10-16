package cz.jaro.gymceska.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cz.jaro.gymceska.theme.SetStatusBarColor
import cz.jaro.gymceska.theme.Theme
import cz.jaro.gymceska.theme.areDynamicColorsSupported
import cz.jaro.gymceska.theme.dynamicDarkColorScheme
import cz.jaro.gymceska.theme.dynamicLightColorScheme

@Composable
fun GymceskaTheme(
    useDarkTheme: Boolean,
    useDynamicColor: Boolean,
    theme: Theme,
    content: @Composable () -> Unit,
) {

    val colorScheme = when {
        useDynamicColor && areDynamicColorsSupported() -> when {
            useDarkTheme -> dynamicDarkColorScheme()
            else -> dynamicLightColorScheme()
        }

        else -> when {
            useDarkTheme -> theme.darkColorScheme
            else -> theme.lightColorScheme
        }
    }

    SetStatusBarColor(colorScheme.background, !useDarkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}