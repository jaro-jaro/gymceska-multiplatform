package cz.jaro.gymceska

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController

@Composable
fun rememberNavigator(navController: NavController) = remember(navController) { Navigator(navController) }
expect fun Navigator(
    navController: NavController,
): Navigator

interface Navigator {
    fun navigate(route: Route)
    fun navigateUp()
}