package cz.jaro.gymceska.rozvrh.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import cz.jaro.better_dialog.AlertDialogManager
import cz.jaro.better_dialog.createMaterial
import cz.jaro.better_dialog.showMaterial
import cz.jaro.better_dialog.showSimple
import cz.jaro.gymceska.ActionScope
import cz.jaro.gymceska.Downloading
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Offline
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.Success
import cz.jaro.gymceska.rozvrh.FindMeResult
import cz.jaro.gymceska.rozvrh.FindMeSettings
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.Vybiratko
import cz.jaro.gymceska.rozvrh.dny
import cz.jaro.gymceska.rozvrh.replaceLast
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock.System
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun RozvrhEditorNavigation(
    navigator: Navigator,
    findMe: (FindMeSettings) -> StateFlow<Result<FindMeResult>>,
    lessons: List<ClosedRange<LocalTime>>,
    selectTimetable: (Timetable) -> Unit,
    remove: () -> Unit,
    loaded: Boolean,
    changes: List<Change>,
    findConflicts: () -> List<String>,
    download: () -> Unit,
    upload: () -> Unit,
    changeView: () -> Unit,
    isShowingChanges: Boolean,
    rooms: List<Timetable.Room>,
    teachers: List<Timetable.Teacher>,
    memory: Address?,
    forget: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Editor",
    actions = {
        Actions(
            lessons, selectTimetable, findMe, remove, loaded,
            changes, findConflicts, download, upload, changeView, isShowingChanges, rooms, teachers
        )
    },
    currentDestination = Route.RozvrhEditor(""),
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
    navigationIcon = if (memory != null) ({
        IconButton(
            onClick = {
                forget()
            }
        ) {
            Icon(if (memory is LessonAddress) Icons.Default.FolderCopy else Icons.Default.FileCopy, "Zapomenout")
        }
    }) else null
)

@Composable
private fun ActionScope.Actions(
    lessons: List<ClosedRange<LocalTime>>,
    chooseTimetable: (Timetable) -> Unit,
    findMe: (FindMeSettings) -> StateFlow<Result<FindMeResult>>,
    remove: () -> Unit,
    loaded: Boolean,
    changes: List<Change>,
    findConflicts: () -> List<String>,
    download: () -> Unit,
    upload: () -> Unit,
    changeView: () -> Unit,
    isShowingChanges: Boolean,
    rooms: List<Timetable.Room>,
    teachers: List<Timetable.Teacher>,
) {
    val clipboardManager = LocalClipboardManager.current
    Action(
        onClick = {
            showChanges(changes, clipboardManager)
        },
        title = "Zkopírovat změny",
        icon = Icons.AutoMirrored.Filled.Sort,
    )
    var c by remember { mutableIntStateOf(0) }
    Action(
        onClick = {
            c = 0
            showConflicts(rooms, teachers, chooseTimetable, findConflicts())
        },
        icon = Icons.Default.PriorityHigh,
        title = "Najít konflikty",
    )
    Action(
        onClick = {
            c = 0
            download()
        },
        icon = Icons.Default.Download,
        title = "Stáhnout změny",
    )
    Action(
        onClick = {
            c = 0
            upload()
        },
        icon = Icons.Default.Upload,
        title = "Nahrát změny",
    )
    Action(
        onClick = {
            c = 0
            changeView()
        },
        icon = if (isShowingChanges) Icons.Default.Visibility else Icons.Default.VisibilityOff,
        title = if (isShowingChanges) "Skrýt změny" else "Zobrazit změny",
    )

    val coroutineScope = rememberCoroutineScope()
    Action(
        onClick = {
            findMeSettings(lessons, chooseTimetable, findMe, coroutineScope)
        },
        icon = Icons.Default.Search,
        title = "Najdi mi",
    )
    if (loaded) Action(
        onClick = {
            if (c++ == 3) {
                c = 0
                remove()
            }
        },
        icon = Icons.Default.Delete,
        title = "Zrušit rozvrhy",
    )
}

private fun showConflicts(
    rooms: List<Timetable.Room>,
    teachers: List<Timetable.Teacher>,
    selectTimetable: (Timetable) -> Unit,
    conflicts: List<String>,
) = AlertDialogManager.Global.showMaterial(
    confirmButton = { TextButton(::hide) { Text("OK") } },
    content = {
        LazyColumn {
            item { Text("Nalezeno:") }
            items(conflicts.toList()) { text ->
                Text(text.drop(2), Modifier.clickable {
                    if (text.startsWith("M")) {
                        rooms.find { it.zkratka == text.split(" ")[1] }?.let { selectTimetable(it) }
                    }
                    if (text.startsWith("V")) {
                        teachers.find { it.zkratka == text.split(" ")[1] }?.let { selectTimetable(it) }
                    }
                    hide()
                })
            }
        }
    }
)

private fun showChanges(
    changes: List<Change>,
    clipboardManager: ClipboardManager,
) {
    val export = changes.joinToString("\n") { change ->
        val den1 = Seznamy.dny[change.fromLocation.dayIndex].zkratka
        val den2 = Seznamy.dny[change.toLocation.dayIndex].zkratka
        val hodina1 = change.fromLocation.lessonIndex
        val hodina2 = change.toLocation.lessonIndex
        val ucebna1 = change.from.room
        val ucebna2 = change.to.room
        val trida = change.from.klass
        val skupina = change.from.group
        val predmet = change.from.subject
        val ucitel = change.from.teacher
        buildString {
            append("$den1 $hodina1.")
            if (den1 != den2 || hodina1 != hodina2) {
                append(" > ")
                append("$den2 ")
                append("$hodina2.")
            }
            append(" – ")
            append("$trida $skupina $predmet $ucitel ")
            if (ucebna1 != ucebna2) append("($ucebna1 > $ucebna2)")
            else append("($ucebna1)")
        }
    }
    clipboardManager.setText(AnnotatedString(export))
}

@OptIn(ExperimentalMaterial3Api::class)
fun findMeSettings(
    hodiny: List<ClosedRange<LocalTime>>,
    chooseTimetable: (Timetable) -> Unit,
    findMe: (FindMeSettings) -> StateFlow<Result<FindMeResult>>,
    coroutineScope: CoroutineScope,
) = AlertDialogManager.Global.showMaterial(
    state = FindMeSettings(
        findRoom = false,
        dayIndex = today().dayOfWeek.isoDayNumber
            .let { if (time() > LocalTime(15, 45)) it + 1 else it }
            .let { if (it > 5) 1 else it } - 1,
        lessonIndices = listOf(
            hodiny.indexOfFirst {
                (System.now() - 10.minutes).toLocalDateTime(TimeZone.currentSystemDefault()).time < it.start
            }.coerceAtLeast(0)
        ),
    ),
    confirmButton = {
        TextButton(
            onClick = {
                hide()
                val loading = AlertDialogManager.Global.showMaterial(
                    state = "Hledám...",
                    confirmButton = {},
                    title = { Text(customState) },
                    content = { CircularProgressIndicator() },
                )

                val result = findMeResult(customState, chooseTimetable)

                coroutineScope.launch {
                    findMe(customState).collect {
                        if (it is Downloading) {
                            loading.customState = "Stahování rozvrhů"
                            loading.show()
                            result.hide()
                        }
                        else if (it is Offline) {
                            AlertDialogManager.Global.showSimple(
                                confirmButtonText = "Ok",
                                contentText = "Nejste připojeni k internetu a nemáte staženou offline verzi všech rozvrhů tříd"
                            )
                            loading.hide()
                            result.hide()
                        }
                        else if (it is Success) {
                            result.show()
                            result.customState = it.timetable
                            loading.hide()
                        }
                    }
                }
            }
        ) {
            Text(text = "Vyhledat")
        }
    },
    dismissButton = { TextButton(::hide) { Text("Zrušit") } },
    title = { Text("Najdi mi") },
    content = {
        var customState by ::customState
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
        ) {
            Vybiratko(
                seznam = listOf("volnou učebnu", "volného učitele"),
                index = if (customState.findRoom) 0 else 1,
                onClick = { i, _ ->
                    customState = customState.copy(findRoom = i == 0)
                },
                label = "Najdi mi",
                zaskrtavatko = { false },
            )
            Vybiratko(
                seznam = Seznamy.dny4Pad,
                index = customState.dayIndex,
                onClick = { i, _ ->
                    customState = customState.copy(dayIndex = i)
                },
                zaskrtavatko = { false },
            )
            Vybiratko(
                value = "${customState.lessonIndices.joinToString(" a ") { "$it." }} hodinu",
                seznam = Seznamy.hodiny4Pad,
                onClick = { i, _ ->
                    customState = customState.copy(
                        lessonIndices = if (i in customState.lessonIndices) customState.lessonIndices - i
                        else /*if (i !in customState.lessonIndices)*/ customState.lessonIndices + i
                    )
                },
                zaskrtavatko = {
                    Seznamy.hodiny4Pad.indexOf(it) in customState.lessonIndices
                },
                zavirat = false
            )
        }
    },
    properties = DialogProperties(
        dismissOnClickOutside = false,
        dismissOnBackPress = false,
    )
)

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
                Text("Na škole jsou ${Seznamy.dny4Pad[settings.dayIndex]} ${settings.lessonIndices.joinToString { "$it." }.replaceLast(", ", " a ")} hodinu volné tyto učebny:")
            }
            if (settings.findRoom) items(customState.rooms.toList()) {
                Text("${it.nazev}, to je${it.napoveda}", Modifier.clickable {
                    hide()
                    chooseTimetable(it)
                })
            }
            if (!settings.findRoom) item {
                Text("Na škole jsou ${Seznamy.dny4Pad[settings.dayIndex]} ${settings.lessonIndices.joinToString { "$it." }.replaceLast(", ", " a ")} hodinu volní tito učitelé:")
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