package cz.jaro.gymceska

import androidx.navigation.NavController
import kotlinx.browser.window
import org.w3c.dom.url.URL

actual fun Navigator(
    navController: NavController,
): Navigator {
    window.onpopstate = {
        navController.navigate(window.location.hash.removePrefix("#"))
    }
    return object : Navigator {
        override fun navigate(route: Route) {
            navController.navigate(route)
            val destination = navController.currentDestination
            val path = route.generateRouteWithArgs(destination ?: return)
            val url = URL(window.location.protocol + window.location.host + "/$path")
            val pathWithoutSearch = url.pathname.removePrefix("/")
            window.history.pushState(null, "", "#$pathWithoutSearch")
        }

        override fun navigateUp() {
            window.history.back()
        }
    }
}