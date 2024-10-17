package cz.jaro.gymceska.ukoly

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Route

@Composable
fun UkolyNavigation(
    navigator: Navigator,
    smiSpravovat: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Domácí úkoly",
    currentDestination = Route.Ukoly,
    navigator = navigator,
    content = content,
    minorNavigationItems = {
        if (smiSpravovat) MinorNavigationItem(
            destination = Route.SpravceUkolu,
            title = "Spravovat úkoly",
            icon = Icons.Default.Edit,
        )
        MinorNavigationItem(
            destination = Route.Nastaveni,
            title = "Nastavení",
            icon = Icons.Default.Settings,
        )
    },
)

@Composable
fun SpravceUkoluNavigation(
    navigator: Navigator,
    navigateBack: () -> Unit,
    pridatUkol: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Spravovat úkoly",
    currentDestination = Route.SpravceUkolu,
    navigator = navigator,
    content = content,
    navigationIcon = {
        IconButton(
            onClick = navigateBack
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět")
        }
    },
    floatingActionButton = {
        FloatingActionButton(
            onClick = {
                pridatUkol()
            },
        ) {
            Icon(Icons.Default.Add, null)
        }
    },
    minorNavigationItems = {
        MinorNavigationItem(
            destination = Route.SpravceUkolu,
            title = "Spravovat úkoly",
            icon = Icons.Default.Edit,
        )
        MinorNavigationItem(
            destination = Route.Nastaveni,
            title = "Nastavení",
            icon = Icons.Default.Settings,
        )
    },
)