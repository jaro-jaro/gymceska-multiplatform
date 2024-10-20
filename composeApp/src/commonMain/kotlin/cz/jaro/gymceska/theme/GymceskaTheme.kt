package cz.jaro.gymceska.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

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

    CompositionLocalProvider(
        LocalIsDynamicThemeUsed provides useDynamicColor,
        LocalIsDarkThemeUsed provides useDarkTheme,
        LocalTheme provides theme.takeUnless { useDynamicColor }
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

val LocalTheme = staticCompositionLocalOf<Theme?> { error("CompositionLocal LocalTheme not present") }
val LocalIsDynamicThemeUsed = staticCompositionLocalOf<Boolean> { error("CompositionLocal LocalTheme not present") }
val LocalIsDarkThemeUsed = staticCompositionLocalOf<Boolean> { error("CompositionLocal LocalIsDarkThemeUsed not present") }
