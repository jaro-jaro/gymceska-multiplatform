package cz.jaro.gymceska

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import cz.jaro.better_dialog.AlertDialog
import cz.jaro.better_dialog.AlertDialogManager
import cz.jaro.better_dialog.createMaterial
import cz.jaro.gymceska.nastaveni.Nastaveni
import cz.jaro.gymceska.rozvrh.Rozvrh
import cz.jaro.gymceska.rozvrh.editor.RozvrhEditor
import cz.jaro.gymceska.rozvrh.manual.RozvrhManual
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
    )

    else -> emptyMap<KType, NavType<*>>()
}

@Composable
fun MainContent(
    deeplink: String,
    koin: Koin,
    updateApp: () -> Unit = {},
    isAppUpdateNeeded: Boolean = false,
    forceUpdate: Boolean = false,
) {
    Surface {
        AlertDialog(AlertDialogManager.Global)

        val appUpdateDialog = remember {
            AlertDialogManager.Global.createMaterial(
                state = forceUpdate,
                confirmButton = { TextButton(updateApp) { Text("Ano") } },
                title = { Text("Aktualizace aplikace") },
                content = { Text("Je k dispozici nová verze aplikace, chcete ji aktualizovat?") },
                dismissButton = { if (!customState) TextButton(::hide) { Text("Ne") } },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                )
            )
        }

        LaunchedEffect(isAppUpdateNeeded) {
            if (isAppUpdateNeeded) appUpdateDialog.show() else appUpdateDialog.hide()
        }
        LaunchedEffect(forceUpdate) {
            appUpdateDialog.customState = forceUpdate
        }

        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            if (deeplink.isBlank()) return@LaunchedEffect
            while (navController.graphOrNull == null) Unit
            try {
                navController.navigate(deeplink)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }

        LaunchedEffect(Unit) {
            val destinationFlow = navController.currentBackStackEntryFlow

            destinationFlow.collect { destination ->
                Firebase.analytics.logEvent("navigation") {
                    param("route", destination.generateRouteWithArgs().orEmpty())
                }
            }
        }

        val navigator = rememberNavigator(navController)

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
            route<Route.RozvrhManual> {
                RozvrhManual(
                    args = it,
                    navigator = navigator,
                    koin = koin
                )
            }
            route<Route.RozvrhEditor> {
                RozvrhEditor(
                    args = it,
                    navigator = navigator,
                    koin = koin
                )
            }
            route<Route.Ukoly> { Ukoly(args = it, navigator = navigator, koin = koin) }
            route<Route.SpravceUkolu> {
                SpravceUkolu(
                    args = it,
                    navigator = navigator,
                    koin = koin
                )
            }
            route<Route.Nastaveni> { Nastaveni(args = it, navigator = navigator, koin = koin) }
        }
    }
}

private val NavController.graphOrNull: NavGraph?
    get() = try {
        graph
    } catch (_: IllegalStateException) {
        null
    }