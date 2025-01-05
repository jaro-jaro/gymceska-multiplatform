package cz.jaro.gymceska.rozvrh.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jaro.better_dialog.globalDialogManager
import cz.jaro.better_dialog.showMaterial
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.TimetableDataForEdit
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.ZadnaData
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.LocalCellZoom
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.Tabulka
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.Vybiratko
import cz.jaro.gymceska.viewModel
import kotlinx.datetime.LocalTime
import org.koin.core.Koin

@Composable
fun RozvrhEditor(
    args: Route.RozvrhEditor,
    navigator: Navigator,
    koin: Koin,
) {
    val viewModel = koin.viewModel<RozvrhEditorViewModel>(
        RozvrhEditorViewModel.Parameters(
            arg = args.vjec,
        )
    )

    LaunchedEffect(Unit) {
        viewModel.navigator = navigator
    }

    val tabulka by viewModel.result.collectAsStateWithLifecycle()
    val realVjec by viewModel.timetable.collectAsStateWithLifecycle()

    val tridy by viewModel.tridy.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val mistnosti by viewModel.mistnosti.collectAsStateWithLifecycle()
    val vyucujici by viewModel.vyucujici.collectAsStateWithLifecycle()
    val hodiny by viewModel.hodiny.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val alwaysTwoRowCells by viewModel.alwaysTwoRowCells.collectAsStateWithLifecycle()
    val isShowingChanges by viewModel.isShowingChanges.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()

    RozvrhEditorContent(
        result = tabulka,
        timetable = realVjec,
        selectTimetable = viewModel::vybratRozvrh,
        navigator = navigator,
        findFreeClassroom = viewModel::najdiMivolnouTridu,
        findFreeTeacher = viewModel::najdiMiVolnehoUcitele,
        classes = tridy,
        rooms = mistnosti,
        teachers = vyucujici,
        hodiny = hodiny,
        zoom = zoom,
        alwaysTwoRowCells = alwaysTwoRowCells,
        reset = viewModel::removeTimetable,
        load = viewModel::loadFile,
        loaded = loaded,
        isShowingChanges = isShowingChanges,
        changes = changes,
        memory = memory,
        download = viewModel::download,
        upload = viewModel::uploadChanges,
        changeView = viewModel::changeView,
        findConflicts = viewModel::findConflicts,
        findConflictsAfterSwitch = viewModel::findConflictsAfterSwitch,
        remember = viewModel::remember,
        move = viewModel::move,
        switch = viewModel::switch,
        findSwitch = viewModel::findSwitch,
        whatIsWhere = viewModel::whatIsWhere,
        findRoom = viewModel::findRoom,
        editRoom = viewModel::editRoom,
    )
}

@Composable
fun RozvrhEditorContent(
    result: Result<TimetableDataForEdit>?,
    timetable: Timetable?,
    selectTimetable: (Timetable) -> Unit,
    classes: List<Timetable.Class>,
    reset: () -> Unit,
    download: () -> Unit,
    upload: () -> Unit,
    changeView: () -> Unit,
    changes: List<Change>,
    rooms: List<Timetable.Room>,
    teachers: List<Timetable.Teacher>,
    zoom: Float,
    alwaysTwoRowCells: Boolean,
    load: () -> Unit,
    loaded: Boolean,
    hodiny: List<ClosedRange<LocalTime>>,
    findConflicts: () -> List<String>,
    findConflictsAfterSwitch: (LessonAddress, LessonAddress) -> List<String>,
    memory: Address?,
    remember: (Address?) -> Unit,
    navigator: Navigator,
    findFreeClassroom: (Int, List<Int>, (String) -> Unit, (List<Timetable.Room>?) -> Unit) -> Unit,
    findFreeTeacher: (Int, List<Int>, (String) -> Unit, (List<Timetable.Teacher>?) -> Unit) -> Unit,
    isShowingChanges: Boolean,
    move: (LessonAddress) -> Unit,
    switch: (CellAddress) -> Unit,
    findSwitch: (CellAddress) -> List<String>,
    whatIsWhere: (CellAddress) -> List<String>,
    findRoom: (CellAddress) -> List<String>,
    editRoom: (CellAddress, String) -> Unit,
) = RozvrhEditorNavigation(
    navigator, findFreeClassroom, findFreeTeacher, hodiny, selectTimetable, reset, loaded, changes,
    findConflicts, download, upload, changeView, isShowingChanges, rooms, teachers, memory, { remember(null) },
) { paddingValues ->

    fun vysledkyDialog(
        results: List<String>,
        address: Address,
    ) = globalDialogManager.showMaterial(
        confirmButton = { TextButton(::hide) { Text("OK") } },
        content = {
            LazyColumn {
                item {
                    Text("Nalezeno:")
                }
                items(results.toList()) { text ->
                    Text(text.drop(2), Modifier.clickable {
                        if (text.startsWith("M")) {
                            rooms.find { it.zkratka == text.split(" ")[1] }?.let { selectTimetable(it) }
                        }
                        if (text.startsWith("U")) {
                            editRoom(address as CellAddress, text.split(" ")[1])
                        }
                        if (text.startsWith("V")) {
                            teachers.find { it.zkratka == text.split(" ")[1] }?.let { selectTimetable(it) }
                        }
                        if (text.startsWith("T")) {
                            classes.find { it.zkratka == text.split(" ")[1] }?.let { selectTimetable(it) }
                        }
                        hide()
                    }.heightIn(min = 32.dp))
                }
            }
        }
    )

    fun vysledkyDialog2(
        results: List<String>,
        address: LessonAddress,
    ) = globalDialogManager.showMaterial(
        confirmButton = { TextButton(::hide) { Text("OK") } },
        content = {
            LazyColumn {
                item {
                    Text("Nalezeno:")
                }
                items(results.toList()) { text ->
                    Text(text.drop(2), Modifier.clickable {
                        val dayIndex = Seznamy.dny1Pad.indexOf(text.split(" ")[1])
                        val lessonIndex = Seznamy.hodiny1Pad.indexOf(text.split(" ", limit = 3)[2])

                        vysledkyDialog(
                            findConflictsAfterSwitch(LessonAddress(dayIndex, lessonIndex), address),
                            address,
                        )
                    })
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (loaded) Vybiratko(timetable, false, {}, null, selectTimetable, classes, rooms, teachers)
        else {
            TextButton(
                onClick = load,
                Modifier.padding(all = 8.dp),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
            ) {
                Icon(Icons.Default.Upload, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Nahrát rozvrh")
            }
        }
        if (loaded && result != null && timetable != null) when (result) {
            is Uspech -> CompositionLocalProvider(LocalCellZoom provides zoom) {
                fun editCell(address: CellAddress, cell: Cell.DataForEdit<out Address>) = globalDialogManager.showMaterial(
                    confirmButton = { TextButton(::hide) { Text("Zrušit") } },
                    content = {
                        require(memory != address)
                        if (memory is LessonAddress && memory == address.lessonAddress) {
                            TextButton(
                                onClick = {
                                    remember(null)
                                    hide()
                                },
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Default.ContentPasteOff, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Zapomenout")
                            }
                            TextButton(
                                onClick = {
                                    vysledkyDialog2(findSwitch(address), address.lessonAddress)
                                    hide()
                                },
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Default.Shuffle, null, Modifier.size(ButtonDefaults.IconSize))
                                Icon(Icons.Default.Search, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Najít prohození")
                            }
                        } else if (cell !is Cell.EmptyForEdit && memory != null && memory is CellAddress) {
                            TextButton(
                                onClick = {
                                    move(address.lessonAddress)
                                    hide()
                                },
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowRightAlt, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Přesunout sem")
                            }
                            TextButton(
                                onClick = {
                                    switch(address)
                                    hide()
                                },
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Default.Shuffle, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Prohodit")
                            }
                        }
                    }
                )

                Tabulka(
                    vjec = timetable,
                    tabulka = result.timetable,
                    kliklNaNeco = { vjec ->
                        selectTimetable(vjec)
                    },
                    tridy = classes,
                    mistnosti = rooms,
                    vyucujici = teachers,
                    mujRozvrh = false,
                    hodiny = hodiny,
                    horScrollState = rememberScrollState(),
                    verScrollState = rememberScrollState(),
                    alwaysTwoRowCells = alwaysTwoRowCells,
                    stalost = TimetableType.Permanent,
                    icon = { address ->
                        when {
                            timetable !is Timetable.Class -> null
                            address is CellAddress -> {
                                val cell = result.timetable[address]
                                when {
                                    memory == null && cell !is Cell.EmptyForEdit -> Icons.Default.FileCopy
                                    memory == null -> null
                                    memory is LessonAddress && memory == address.lessonAddress -> Icons.Default.MoreVert
                                    memory is CellAddress && memory.lessonAddress == address.lessonAddress -> Icons.Default.FolderCopy

                                    cell is Cell.EmptyForEdit -> Icons.AutoMirrored.Filled.ArrowRightAlt
                                    memory is LessonAddress -> Icons.Default.Shuffle
                                    else -> Icons.Default.MoreVert
                                }
                            }

                            address is LessonAddress && memory != null && memory is CellAddress -> Icons.AutoMirrored.Filled.ArrowRightAlt

                            else -> null
                        }
                    },
                    roomLongClick = { address ->
                        if (timetable !is Timetable.Class) return@Tabulka
                        if (address !is CellAddress) return@Tabulka
                        val cell = result.timetable[address]
                        globalDialogManager.showMaterial(
                            state = cell.room,
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        editRoom(address, customState)
                                        hide()
                                    }
                                ) {
                                    Text("OK")
                                }
                            },
                            content = {
                                TextField(
                                    value = customState,
                                    onValueChange = {
                                        customState = it
                                    },
                                    Modifier
                                        .fillMaxWidth(1F)
                                        .padding(8.dp),
                                    label = {
                                        Text("Učebna")
                                    },
                                )
                                TextButton(
                                    onClick = {
                                        vysledkyDialog(findRoom(address.also(::println)).also(::println), address).also(::println)
                                        hide()
                                    },
                                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                ) {
                                    Icon(Icons.Default.Search, null, Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Najít volnou")
                                }
                                TextButton(
                                    onClick = {
                                        vysledkyDialog(whatIsWhere(address), address)
                                        hide()
                                    },
                                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                ) {
                                    Icon(Icons.Default.QuestionMark, null, Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Co kde je?")
                                }
                            }
                        )
                    },
                    subjectClick = { address ->
                        when {
                            timetable !is Timetable.Class -> Unit
                            address is CellAddress -> {
                                val cell = result.timetable[address]
                                when {
                                    memory == null && cell !is Cell.EmptyForEdit -> remember(address)
                                    memory == null -> Unit
                                    memory is LessonAddress && memory == address.lessonAddress -> editCell(address, cell)
                                    memory is CellAddress && memory.lessonAddress == address.lessonAddress -> remember(memory.lessonAddress)

                                    cell is Cell.EmptyForEdit -> move(address.lessonAddress)
                                    memory is LessonAddress -> switch(address)
                                    else -> editCell(address, cell)
                                }
                            }

                            address is LessonAddress && memory != null && memory is CellAddress -> move(address)
                        }
                    },
                )
            }

            is Error -> Text("Omlouváme se, ale došlo k chybě při načítání rozvrhu. Zkuste to znovu.")
            is ZadnaData -> Text("Nemáte nahrané žádné rozvrhy")
        }
    }
}

operator fun TimetableDataForEdit.get(address: LessonAddress) = justTimetable()[address.dayIndex][address.lessonIndex]
private operator fun TimetableDataForEdit.get(address: CellAddress) = this[address.lessonAddress][address.cellIndex]
