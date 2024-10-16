package cz.jaro.gymceska.nastaveni

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Route

@Composable
fun NastaveniNavigation(
    navigate: (Route) -> Unit,
    navigateBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Nastavení",
    navigationIcon = {
        IconButton(
            onClick = navigateBack
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět")
        }
    },
    currentDestination = Route.Nastaveni,
    navigateToDestination = navigate,
    content = content,
    minorNavigationItems = {
        MinorNavigationItem(
            destination = Route.Nastaveni,
            title = "Nastavení",
            icon = Icons.Default.Settings,
        )
    },
)