package cz.jaro.gymceska

import androidx.compose.runtime.Composable
import kotlinx.browser.window

actual val openWebsiteLauncher: (url: String) -> Unit
    @Composable
    get() = { window.open(it, "_blank"); }