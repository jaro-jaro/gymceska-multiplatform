package cz.jaro.gymceska

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

actual val openWebsiteLauncher: (url: String) -> Unit
    @Composable
    get() {
        val intent =
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
        val ctx = LocalContext.current
        return {
            intent.launchUrl(ctx, it.toUri())
        }
    }