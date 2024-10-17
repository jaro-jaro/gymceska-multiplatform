package cz.jaro.gymceska

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import cz.jaro.compose_dialog.dialogState
import cz.jaro.gymceska.nastaveni.Nastaveni
import cz.jaro.gymceska.rozvrh.Rozvrh
import cz.jaro.gymceska.ukoly.SpravceUkolu
import cz.jaro.gymceska.ukoly.Ukoly
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.analytics.logEvent
import org.koin.core.Koin
import kotlin.reflect.KType

inline fun <reified T : Route> typeMap() = when (T::class) {
    Route.Rozvrh::class -> mapOf(
        serializationTypePair<Int?>(),
        serializationTypePair<Boolean?>(),
    )

    else -> emptyMap<KType, NavType<*>>()
}

@Composable
fun MainContent(
    deeplink: String,
    jePotrebaAktualizovatAplikaci: Boolean,
    aktualizovatAplikaci: () -> Unit,
    koin: Koin,
) {
    if (jePotrebaAktualizovatAplikaci) {
        var zobrazitDialog by remember { mutableStateOf(true) }

        if (zobrazitDialog) AlertDialog(
            onDismissRequest = {
                zobrazitDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        zobrazitDialog = false
                        aktualizovatAplikaci()
                    }
                ) {
                    Text("Ano")
                }
            },
            title = {
                Text("Aktualizace aplikace")
            },
            text = {
                Text("Je k dispozici nová verze aplikace, chcete ji aktualizovat?")
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        zobrazitDialog = false
                    }
                ) {
                    Text("Ne")
                }
            },
        )
    }
    Surface {
        cz.jaro.compose_dialog.AlertDialog(dialogState)
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            if (deeplink.isBlank()) return@LaunchedEffect
            while (navController.graphOrNull == null) Unit
            navController.navigate(deeplink)
        }

        LaunchedEffect(Unit) {
            val destinationFlow = navController.currentBackStackEntryFlow

            destinationFlow.collect { destination ->
                Firebase.analytics.logEvent("navigation") {
                    param("route", destination.generateRouteWithArgs().orEmpty())
                }
            }
        }

        val navigator = getNavigator(navController)

        NavHost(
            navController = navController,
            startDestination = Route.Rozvrh(""),
            popEnterTransition = {
                scaleIn(
                    animationSpec = tween(
                        durationMillis = 100,
                        delayMillis = 35,
                    ),
                    initialScale = 1.1F,
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 100,
                        delayMillis = 35,
                    ),
                )
            },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.9F,
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 35,
                        easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f),
                    ),
                )
            },
        ) {
            route<Route.Rozvrh> { Rozvrh(args = it, navigator = navigator, koin = koin) }
            route<Route.Ukoly> { Ukoly(args = it, navigator = navigator, koin = koin) }
            route<Route.SpravceUkolu> { SpravceUkolu(args = it, navigator = navigator, koin = koin) }
            route<Route.Nastaveni> { Nastaveni(args = it, navigator = navigator, koin = koin) }
        }
    }
}

@Composable
expect fun getNavigator(
    navController: NavController,
): Navigator

interface Navigator {
    fun navigate(route: Route)
    fun navigateUp()
}

private val NavController.graphOrNull: NavGraph?
    get() = try {
        graph
    } catch (e: IllegalStateException) {
        null
    }