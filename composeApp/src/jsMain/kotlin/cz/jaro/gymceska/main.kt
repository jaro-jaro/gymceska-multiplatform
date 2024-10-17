package cz.jaro.gymceska

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.StorageSettings
import com.russhwolf.settings.observable.makeObservable
import cz.jaro.gymceska.rozvrh.Vjec
import cz.jaro.gymceska.ui.theme.GymceskaTheme
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.browser.window
import org.jetbrains.skiko.wasm.onWasmReady
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSettingsApi::class)
fun main() {
    val location = window.location.hash.removePrefix("#") + window.location.search

    val koinApp = initKoin(module {
        single {
            Firebase.initialize(
                null, FirebaseOptions(
                    apiKey = "AIzaSyDmZz7hpKUcz0ltL_ZbVrE2IYMopBczehw",
                    authDomain = "gymceska-b9b4c.firebaseapp.com",
                    databaseUrl = "https://gymceska-b9b4c-default-rtdb.europe-west1.firebasedatabase.app",
                    projectId = "gymceska-b9b4c",
                    storageBucket = "gymceska-b9b4c.appspot.com",
                    gcmSenderId = "513344353318",
                    applicationId = "1:513344353318:web:ca103d8a85fa3565eca2e7",
                    gaTrackingId = "G-JV5HK9ELY7",
                )
            )
        }
        single {
            UserOnlineManager { true }
        }
        single {
            UserIdProvider {
                ""
            }
        }

        single {
            StorageSettings().makeObservable()
        }
    })

    val repo = koinApp.koin.get<Repository>()

    onWasmReady {
        CanvasBasedWindow(
            title = "Gymceska",
        ) {
            val nastaveni by repo.nastaveni.collectAsStateWithLifecycle(Nastaveni(mojeTrida = Vjec.TridaVjec("")))

            GymceskaTheme(
                useDarkTheme = if (nastaveni.darkModePodleSystemu) isSystemInDarkTheme() else nastaveni.darkMode,
                useDynamicColor = nastaveni.dynamicColors,
                theme = nastaveni.tema,
            ) {
                MainContent(
                    deeplink = location,
                    jePotrebaAktualizovatAplikaci = false,
                    aktualizovatAplikaci = {},
                    koin = koinApp.koin,
                )
            }
        }
    }
}

@Composable
actual fun getNavigator(
    navController: NavController,
): Navigator {
    window.onpopstate = {
        navController.navigate(window.location.hash.removePrefix("#"))
    }
    return remember(navController) {
        object : Navigator {
            override fun navigate(route: Route) {
                navController.navigate(route)
                val destination = navController.currentDestination
                val path = route.generateRouteWithArgs(destination ?: return)
                window.history.pushState(null, "", "#$path")
            }

            override fun navigateUp() {
                window.history.back()
            }
        }
    }
}