package cz.jaro.gymceska

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    railView: Boolean,
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
            railView = railView,
        )
    }

    @Composable
    override fun Action(onClick: () -> Unit, title: String, icon: ImageVector) {
        Action(
            onClick = onClick,
            title = title,
            icon = icon,
            railView = railView,
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

    val mediumWidth = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
    val expandedWidth = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    val compactHeight = windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
    val expandedHeight = windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.EXPANDED

    val railView = expandedWidth || (mediumWidth && !expandedHeight)

    val scope = ActionScope(railView, currentDestination, navigator)
    val scopedMinorNavigationItems = scopeComposable(scope, minorNavigationItems)
    val scopedActions = scopeComposable(scope, actions)

    if (railView)
        Rail(
            currentDestination = currentDestination,
            navigator = navigator,
            actions = scopedActions,
            titleContent = titleContent ?: floatingActionButton,
            minorNavigationItems = scopedMinorNavigationItems,
            showIcon = !compactHeight,
            content = content,
        )
    else
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
    railView: Boolean,
) = if (railView) {
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
    railView: Boolean,
) = if (railView) {
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
    showIcon: Boolean,
    actions: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    minorNavigationItems: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
    ) {
        NavigationRail(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 80.0.dp),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showIcon) Icon(painterResource(Res.drawable.ic_launcher_foreground), null, Modifier.size(64.dp))
                    titleContent?.invoke()
                    Spacer(Modifier.height(8.dp))
                }
                actions?.let {
                    Column { it() }
                }
                Column {
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
            Box(
                Modifier.weight(1F),
            ) {
                content(PaddingValues())
            }
        }
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