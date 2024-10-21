package cz.jaro.gymceska

import androidx.navigation.NavController

actual fun Navigator(
    navController: NavController,
) = object : Navigator {
    override fun navigate(route: Route) {
        navController.navigate(route)
    }

    override fun navigateUp() {
        navController.navigateUp()
    }
}

actual fun setAppTitle(title: String) {}