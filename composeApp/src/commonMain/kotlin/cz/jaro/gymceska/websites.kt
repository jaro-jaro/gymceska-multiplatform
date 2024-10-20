package cz.jaro.gymceska

import androidx.compose.runtime.Composable

expect val openWebsiteLauncher: (url: String) -> Unit
@Composable get