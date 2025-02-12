package cz.jaro.gymceska.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual fun areDynamicColorsSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun dynamicDarkColorScheme() = androidx.compose.material3.dynamicDarkColorScheme(LocalContext.current)

@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun dynamicLightColorScheme() = androidx.compose.material3.dynamicLightColorScheme(LocalContext.current)
