package cz.jaro.gymceska.rozvrh.manual

import androidx.compose.foundation.layout.PaddingValues
import cz.jaro.gymceska.Result
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import cz.jaro.gymceska.ActionScope
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.rozvrh.FindMeResult
import cz.jaro.gymceska.rozvrh.FindMeSettings
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.editor.findMeSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalTime

@Composable
fun RozvrhManualNavigation(
    navigator: Navigator,
    findMe: (FindMeSettings) -> StateFlow<Result<FindMeResult>>,
    hodiny: List<ClosedRange<LocalTime>>,
    vybratRozvrh: (Timetable) -> Unit,
    remove: () -> Unit,
    loaded: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Manuál",
    actions = {
        Actions(hodiny, vybratRozvrh, findMe, remove, loaded)
    },
    currentDestination = Route.RozvrhManual(""),
    navigator = navigator,
    content = content,
    minorNavigationItems = {
        MinorNavigationItem(
            destination = Route.RozvrhEditor(""),
            title = "Editor",
            icon = Icons.Default.Edit,
        )
        MinorNavigationItem(
            destination = Route.RozvrhManual(""),
            title = "Manuální",
            icon = Icons.Default.TableChart,
        )
        MinorNavigationItem(
            destination = Route.Nastaveni,
            title = "Nastavení",
            icon = Icons.Default.Settings,
        )
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionScope.Actions(
    hodiny: List<ClosedRange<LocalTime>>,
    vybratRozvrh: (Timetable) -> Unit,
    findMe: (FindMeSettings) -> StateFlow<Result<FindMeResult>>,
    remove: () -> Unit,
    loaded: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    Action(
        onClick = {
            findMeSettings(hodiny, vybratRozvrh, findMe, coroutineScope)
        },
        icon = Icons.Default.Search,
        title = "Najdi mi",
    )
    if (loaded) Action(
        onClick = remove,
        icon = Icons.Default.Delete,
        title = "Zrušit rozvrhy",
    )
}