package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cz.jaro.better_dialog.AlertDialogManager
import cz.jaro.better_dialog.AlertDialogState
import cz.jaro.better_dialog.AlertDialogStyle
import cz.jaro.better_dialog.createMaterial
import cz.jaro.better_dialog.showMaterial
import cz.jaro.gymceska.ActionScope
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.topHeaders
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock.System
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun RozvrhNavigation(
    stahnoutVse: () -> Unit,
    navigator: Navigator,
    findMe: (FindMeSettings) -> StateFlow<FindMeResult?>,
    result: Result<out TimetableData>?,
    vybratRozvrh: (Timetable) -> Unit,
    currentlyDownloading: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    titleContent = {
        if (currentlyDownloading)
            Icon(Icons.Default.CloudDownload, null, Modifier.padding(horizontal = 16.dp))
    },
    title = "Rozvrh",
    actions = {
        Actions(stahnoutVse, result, vybratRozvrh, findMe)
    },
    currentDestination = Route.Rozvrh(""),
    navigator = navigator,
    content = content,
    minorNavigationItems = {
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
    stahnoutVse: () -> Unit,
    result: Result<out TimetableData>?,
    vybratRozvrh: (Timetable) -> Unit,
    findMe: (FindMeSettings) -> StateFlow<FindMeResult?>,
) {
    Action(
        onClick = stahnoutVse,
        icon = Icons.Default.CloudDownload,
        title = "Stáhnout vše",
    )
    val coroutineScope = rememberCoroutineScope()
    Action(
        onClick = {
            findMeSettigs(result, vybratRozvrh, findMe, coroutineScope)
        },
        icon = Icons.Default.Search,
        title = "Najdi mi",
    )
}

lateinit var state: MutableState<FindMeSettings>

@OptIn(ExperimentalMaterial3Api::class)
fun findMeSettigs(
    result: Result<out TimetableData>?,
    chooseTimetable: (Timetable) -> Unit,
    findMe: (FindMeSettings) -> StateFlow<FindMeResult?>,
    coroutineScope: CoroutineScope,
): AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>> {
    if (!::state.isInitialized) state = mutableStateOf(FindMeSettings(
        findRoom = true,
        type = TimetableType.defaultByDay(
            today().dayOfWeek.let { if (time() > LocalTime(15, 45)) it + 1 else it }
        ),
        dayIndex = today().dayOfWeek.isoDayNumber
            .let { if (time() > LocalTime(15, 45)) it + 1 else it }
            .let { if (it > 5) 1 else it } - 1,
        lessonIndices = listOf(
            result?.timetable?.topHeaders()?.indexOfFirst {
                try {
                    val cas = it.teacherLike.split(" - ").first()
                    val hm = cas.split(":")
                    (System.now() - 10.minutes).toLocalDateTime(TimeZone.currentSystemDefault())
                        .time < LocalTime(hm[0].toInt(), hm[1].toInt())
                } catch (_: Exception) {
                    false
                }
            }?.coerceAtLeast(0) ?: 0
        ),
    ))
    var state by state
    return AlertDialogManager.Global.showMaterial(
        confirmButton = {
            TextButton(
                onClick = {
                    val loading = AlertDialogManager.Global.showMaterial(
                        state = "Hledám...",
                        confirmButton = {},
                        title = { Text(customState) },
                        content = { CircularProgressIndicator() },
                    )

                    val result = findMeResult(state, chooseTimetable)

                    // Nejste připojeni k internetu a nemáte staženou offline verzi všech rozvrhů tříd
                    coroutineScope.launch {
                        findMe(state).collect {
                            if (it == null) {
                                loading.customState = "Stahování rozvrhů"
                                loading.show()
                                result.hide()
                            } else {
                                result.show()
                                result.customState = it
                                loading.hide()
                            }
                        }
                    }
                    hide()
                }
            ) {
                Text(text = "Vyhledat")
            }
        },
        dismissButton = { TextButton(::hide) { Text("Zrušit") } },
        title = { Text("Najdi mi") },
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                Vybiratko(
                    seznam = listOf("volnou učebnu", "volného učitele"),
                    index = if (state.findRoom) 0 else 1,
                    onClick = { i, _ ->
                        state = state.copy(findRoom = i == 0, filters = emptyList())
                    },
                    label = "Najdi mi",
                    zaskrtavatko = { false },
                )
                Vybiratko(
                    seznam = TimetableType.entries.map { it.nameWhen },
                    value = state.type.nameWhen,
                    onClick = { i, _ ->
                        state = state.copy(type = TimetableType.entries[i])
                    },
                    zaskrtavatko = { false },
                )
                Vybiratko(
                    seznam = Seznamy.dny4Pad,
                    index = state.dayIndex,
                    onClick = { i, _ ->
                        state = state.copy(dayIndex = i)
                    },
                    zaskrtavatko = { false },
                )
                Vybiratko(
                    value = "${state.lessonIndices.joinToString(" a ") { "$it." }} hodinu",
                    seznam = Seznamy.hodiny4Pad,
                    onClick = { i, _ ->
                        state = state.copy(
                            lessonIndices = if (i in state.lessonIndices) state.lessonIndices - i
                            else /*if (i !in state.lessonIndices)*/ state.lessonIndices + i
                        )
                    },
                    zaskrtavatko = {
                        Seznamy.hodiny4Pad.indexOf(it) in state.lessonIndices
                    },
                    zavirat = false
                )
                if (state.findRoom) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze odemčené učebny", Modifier.weight(1F))
                    Switch(
                        checked = FindMeFilter.JustUnlocked in state.filters,
                        onCheckedChange = {
                            state = state.copy(
                                filters =
                                    if (FindMeFilter.JustUnlocked in state.filters)
                                        state.filters - FindMeFilter.JustUnlocked
                                    else
                                        state.filters + FindMeFilter.JustUnlocked
                            )
                        },
                    )
                }
                if (state.findRoom) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze celé učebny", Modifier.weight(1F))
                    Switch(
                        checked = FindMeFilter.JustWhole in state.filters,
                        onCheckedChange = {
                            state = state.copy(
                                filters =
                                    if (FindMeFilter.JustWhole in state.filters)
                                        state.filters - FindMeFilter.JustWhole
                                    else
                                        state.filters + FindMeFilter.JustWhole
                            )
                        },
                    )
                }
                if (!state.findRoom) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze moji vyučující", Modifier.weight(1F))
                    Switch(
                        checked = FindMeFilter.JustMy in state.filters,
                        onCheckedChange = {
                            state = state.copy(
                                filters =
                                    if (FindMeFilter.JustMy in state.filters)
                                        state.filters - FindMeFilter.JustMy
                                    else
                                        state.filters + FindMeFilter.JustMy
                            )
                        },
                    )
                }
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        )
    )
}

private operator fun DayOfWeek.plus(i: Int): DayOfWeek {
    require(i in -6..6) { "i must be in -6..6, got $i" }
    return DayOfWeek(isoDayNumber = (isoDayNumber + i + 7 - 1) % 7 + 1)
}

fun findMeResult(
    settings: FindMeSettings,
    chooseTimetable: (Timetable) -> Unit,
) = AlertDialogManager.Global.createMaterial(
    state = FindMeResult(),
    confirmButton = { TextButton(::hide) { Text("OK") } },
    title = {
        Text(text = "Najdi mi ${if (settings.findRoom) "volnou učebnu" else "volného učitele"}")
    },
    content = {
        LazyColumn {
            if (settings.findRoom) item {
                Text(
                    "Na škole jsou ${settings.type.nameWhen} ${Seznamy.dny4Pad[settings.dayIndex]} ${
                        settings.lessonIndices.joinToString { "$it." }.replaceLast(", ", " a ")
                    } hodinu volné tyto ${settings.filters.text()}učebny:"
                )
            }
            if (settings.findRoom) items(customState.rooms.toList()) {
                Text("${it.nazev}, to je${it.napoveda}", Modifier.clickable {
                    hide()
                    chooseTimetable(it)
                })
            }
            if (!settings.findRoom) item {
                Text(
                    "Na škole jsou ${settings.type.nameWhen} ${Seznamy.dny4Pad[settings.dayIndex]} ${
                        settings.lessonIndices.joinToString { "$it." }.replaceLast(", ", " a ")
                    } hodinu volní tito ${settings.filters.text()}učitelé:"
                )
            }
            if (!settings.findRoom) items(customState.teachers.toList()) {
                Text(it.nazev, Modifier.clickable {
                    hide()
                    chooseTimetable(it)
                })
            }
        }
    }
)

fun String.replaceLast(search: String, replacement: String) =
    reversed().replaceFirst(search.reversed(), replacement).reversed()
