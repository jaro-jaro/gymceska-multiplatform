package cz.jaro.gymceska

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import gymceska.composeapp.generated.resources.Res
import gymceska.composeapp.generated.resources.ic_launcher_foreground
import org.jetbrains.compose.resources.painterResource

interface ActionScope {
    @Composable
    fun MinorNavigationItem(
        destination: Route,
        title: String,
        icon: ImageVector,
    )

    @Composable
    fun Action(
        onClick: () -> Unit,
        title: String,
        icon: ImageVector,
    )
}

private fun ActionScope(
    isInTabletMode: Boolean,
    currentDestination: Route,
    navigator: Navigator,
) = object : ActionScope {
    @Composable
    override fun MinorNavigationItem(destination: Route, title: String, icon: ImageVector) {
        MinorNavigationItem(
            destination = destination,
            selected = destination == currentDestination,
            title = title,
            navigator = navigator,
            icon = icon,
            isInTabletMode = isInTabletMode,
        )
    }

    @Composable
    override fun Action(onClick: () -> Unit, title: String, icon: ImageVector) {
        Action(
            onClick = onClick,
            title = title,
            icon = icon,
            isInTabletMode = isInTabletMode,
        )
    }
}

private fun scopeComposable(scope: ActionScope, content: (@Composable ActionScope.() -> Unit)?): (@Composable () -> Unit)? =
    content?.let {
        ({
            content(scope)
        })
    }

@Composable
fun Navigation(
    title: String,
    actions: (@Composable ActionScope.() -> Unit)? = null,
    minorNavigationItems: (@Composable ActionScope.() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    currentDestination: Route,
    navigator: Navigator,
    content: @Composable (PaddingValues) -> Unit,
    navigationIcon: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isInTabletMode = windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
            || windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    val scope = ActionScope(isInTabletMode, currentDestination, navigator)
    val scopedMinorNavigationItems = scopeComposable(scope, minorNavigationItems)
    val scopedActions = scopeComposable(scope, actions)

    if (isInTabletMode) {
        Row {
            Rail(
                currentDestination = currentDestination,
                navigator = navigator,
                actions = scopedActions,
                titleContent = titleContent ?: floatingActionButton,
                minorNavigationItems = scopedMinorNavigationItems,
            )
            content(PaddingValues())
        }
    } else {
        Scaffold(
            bottomBar = {
                BottomBar(
                    currentDestination = currentDestination,
                    navigator = navigator,
                )
            },
            topBar = {
                TopBar(
                    title = title,
                    actions = scopedActions,
                    titleContent = titleContent,
                    navigationIcon = navigationIcon,
                    minorNavigationItems = scopedMinorNavigationItems,
                )
            },
            floatingActionButton = floatingActionButton ?: {},
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    actions: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    minorNavigationItems: (@Composable () -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title)
                titleContent?.invoke()
            }
        },
        actions = { actions?.invoke(); minorNavigationItems?.invoke() },
        navigationIcon = navigationIcon ?: {},
    )
}

@Composable
private fun Action(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector,
    isInTabletMode: Boolean,
) = if (isInTabletMode) {
    NavigationRailItem(
        selected = false,
        onClick = {
            onClick()
        },
        icon = {
            Icon(icon, title)
        },
        label = {
            Text(title, textAlign = TextAlign.Center)
        },
    )
} else {
    IconButton(
        onClick = {
            onClick()
        },
    ) {
        Icon(icon, title)
    }
}

@Composable
private fun MinorNavigationItem(
    destination: Route,
    selected: Boolean,
    title: String,
    navigator: Navigator,
    icon: ImageVector,
    isInTabletMode: Boolean,
) = if (isInTabletMode) {
    NavigationRailItem(
        selected = selected,
        onClick = {
            navigator.navigate(destination)
        },
        icon = {
            Icon(icon, title)
        },
        label = {
            Text(title, textAlign = TextAlign.Center)
        },
    )
} else {
    if (!selected) IconButton(
        onClick = {
            navigator.navigate(destination)
        },
    ) {
        Icon(icon, title)
    }
    else Unit
}

@Composable
private fun Rail(
    currentDestination: Route,
    navigator: Navigator,
    actions: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    minorNavigationItems: (@Composable () -> Unit)? = null,
) {
    NavigationRail(
        Modifier
            .fillMaxHeight()
            .widthIn(max = 80.0.dp),
        header = {
            Icon(painterResource(Res.drawable.ic_launcher_foreground), null, Modifier.size(64.dp))
            titleContent?.invoke()
        },
    ) {
        Spacer(Modifier.weight(1F))
        actions?.let {
            it()
            Spacer(Modifier.weight(1F))
        }
        minorNavigationItems?.invoke()
        NavigationRailItem(
            selected = currentDestination is Route.Rozvrh,
            onClick = {
                navigator.navigate(Route.Rozvrh(""))
            },
            icon = {
                Icon(Icons.Default.TableChart, null)
            },
            label = {
                Text("Rozvrh", textAlign = TextAlign.Center)
            }
        )
        NavigationRailItem(
            selected = currentDestination == Route.Ukoly,
            onClick = {
                navigator.navigate(Route.Ukoly)
            },
            icon = {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null)
            },
            label = {
                Text("Domácí úkoly", textAlign = TextAlign.Center)
            }
        )
    }
}

@Composable
private fun BottomBar(
    currentDestination: Route,
    navigator: Navigator,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentDestination is Route.Rozvrh,
            onClick = {
                navigator.navigate(Route.Rozvrh(""))
            },
            icon = {
                Icon(Icons.Default.TableChart, null)
            },
            label = {
                Text("Rozvrh")
            }
        )
        NavigationBarItem(
            selected = currentDestination == Route.Ukoly,
            onClick = {
                navigator.navigate(Route.Ukoly)
            },
            icon = {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null)
            },
            label = {
                Text("Domácí úkoly")
            }
        )
    }
}