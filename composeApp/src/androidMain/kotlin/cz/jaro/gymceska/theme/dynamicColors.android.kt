package cz.jaro.gymceska.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

actual fun areDynamicColorsSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun dynamicDarkColorScheme() = androidx.compose.material3.dynamicDarkColorScheme(LocalContext.current)

@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun dynamicLightColorScheme() = androidx.compose.material3.dynamicLightColorScheme(LocalContext.current)

@Composable
actual fun SetStatusBarColor(
    statusBarColor: Color,
    isAppearanceLightStatusBars: Boolean,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
        }
    }
}