package cz.jaro.gymceska.rozvrh.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.FirebaseClassListSource.Companion.toJson
import cz.jaro.gymceska.LessonForEdit
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.TimetableDataForEdit
import cz.jaro.gymceska.Timetables
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.WeekForEdit
import cz.jaro.gymceska.ZadnaData
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.TvorbaRozvrhu
import cz.jaro.gymceska.rozvrh.copy
import cz.jaro.gymceska.rozvrh.dny
import cz.jaro.gymceska.rozvrh.hodiny
import cz.jaro.gymceska.rozvrh.manual.LocalTimetableSource
import cz.jaro.gymceska.rozvrh.manual.editCells
import cz.jaro.gymceska.rozvrh.manual.editCellsIndexed
import cz.jaro.gymceska.rozvrh.timetable
import cz.jaro.gymceska.rozvrh.upravitTabulku
import cz.jaro.gymceska.topHeaders
import cz.jaro.gymceska.ukoly.today
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RozvrhEditorViewModel(
    private val params: Parameters,
    private val savedTimetableSource: LocalTimetableSource,
    private val editedTimetableSource: EditedTimetableSource,
    settings: SettingsFlow,
) : ViewModel() {

    data class Parameters(
        val arg: String,
    ) {
        internal val decoded = decodeArgument(arg)

        private fun decodeArgument(arg: String): DecodedArgumnt? {
            val isHome = '-' !in arg
            if (isHome) return null
            val typ = arg.split('-')[0].single()
            val zkratka = arg.split('-')[1]
            val modifikatory = arg.split('-').getOrNull(2).orEmpty()
            return DecodedArgumnt(
                zkratka = zkratka,
                type = when (typ) {
                    'D' -> Timetable.DenVjec::class
                    'H' -> Timetable.HodinaVjec::class
                    'M' -> Timetable.Room::class
                    'T' -> Timetable.Class::class
                    'V' -> Timetable.Teacher::class
                    else -> error("Invalid type")
                },
                stalost = TimetableType.entries.find { it.name.first() in modifikatory },
                mujRozvrh = mapOf('M' to true, 'C' to false).entries.find { it.key in modifikatory }?.value,
            )
        }
    }

    internal data class DecodedArgumnt(
        val zkratka: String,
        val type: KClass<out Timetable>,
        val stalost: TimetableType?,
        val mujRozvrh: Boolean?,
    )

    private fun Timetable.encodeArgument() = when (this) {
        is Timetable.DenVjec -> "D-$zkratka"
        is Timetable.HodinaVjec -> "H-$zkratka"
        is Timetable.Room -> "M-$zkratka"
        is Timetable.Class -> "T-$zkratka"
        is Timetable.Teacher -> "V-$zkratka"
    }

    lateinit var navigator: Navigator

    private val _isShowingChanges = MutableStateFlow(true)
    val isShowingChanges = _isShowingChanges.asStateFlow()

    private val _memory = MutableStateFlow(null as Address?)
    val memory = _memory.asStateFlow()

    private val classListSource = savedTimetableSource.classListSource
    val loaded = savedTimetableSource.type.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds)) { it != null }
    val tridy = classListSource.classes
    val mistnosti = classListSource.rooms
    val vyucujici = classListSource.teachers
    private val vyucujici2 = classListSource.vyucujici2

    val hodiny = tridy.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds)) {
        savedTimetableSource.getTimetable(
            klass = tridy.value.firstOrNull() ?: return@mapState emptyList(),
        ).value.timetable?.topHeaders().orEmpty().map {
            it.subtitle.split(" - ").map(::toLocalTime).toRange()
        }
    }

    val timetable = combineStates(
        viewModelScope,
        tridy, mistnosti, vyucujici,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { tridy, mistnosti, vyucujici ->
        if (params.decoded == null) return@combineStates null
        val zkratka = params.decoded.zkratka
        when (params.decoded.type) {
            Timetable.Class::class -> tridy.find { it.zkratka == zkratka }
            Timetable.Room::class -> mistnosti.find { it.zkratka == zkratka }
            Timetable.Teacher::class -> vyucujici.find { it.zkratka == zkratka }
            Timetable.DenVjec::class -> Seznamy.dny.find { it.zkratka == zkratka }
            Timetable.HodinaVjec::class -> Seznamy.hodiny.find { it.zkratka == zkratka }
            else -> error("Invalid type")
        }
    }

    private fun RozvrhEditor(
        vjec: Timetable,
    ) = Route.RozvrhEditor(
        vjec = vjec.encodeArgument(),
    )

    fun vybratRozvrh(vjec: Timetable) {
        viewModelScope.launch {
            navigator.navigate(
                RozvrhEditor(
                    vjec = vjec,
                )
            )
        }
    }

    val zoom = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::zoom)
    val alwaysTwoRowCells = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::alwaysTwoRowCells)

    private fun getTimetable(klass: Timetable.Class, showChanges: Boolean = isShowingChanges.value) =
        if (showChanges) getEditedTimetable(klass).mapState(viewModelScope) { it?.let(::Uspech) ?: ZadnaData() }
        else getSavedTimetable(klass)

    private fun getSavedTimetable(klass: Timetable.Class) =
        savedTimetableSource.getTimetable(klass).upravitTabulku(viewModelScope) { it.toDataForEdit(klass.zkratka) }

    private fun getEditedTimetable(klass: Timetable.Class) =
        editedTimetableSource.getTimetable(klass).mapState(viewModelScope) { it?.toTimetableDataForEdit(hodiny.value, klass.zkratka) }

    private val update = editedTimetableSource.timetables.map {}

    val result = combine(timetable, isShowingChanges, update) { timetable, isShowingChanges, _ ->
        if (timetable == null) null
        else getTimetable2(timetable, false).let { oldData ->
            oldData.timetable?.let { old ->
                if (isShowingChanges) getTimetable2(timetable, true).upravitTabulku {
                    it.mark(old)
                }
                else oldData
            } ?: oldData
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private fun getTimetable2(
        timetable: Timetable,
        showChanges: Boolean = isShowingChanges.value,
        getKlass: (Timetable.Class) -> StateFlow<Result<TimetableDataForEdit>> = { getTimetable(it, showChanges) },
    ): Result<TimetableDataForEdit> =
        when (timetable) {
            is Timetable.Class -> getKlass(timetable).value.upravitTabulku {
                it.editCells { cell ->
                    when (cell) {
                        is Cell.Edit -> cell.copy(klass = "")
                        is Cell.Header -> cell
                        is Cell.EmptyForEdit -> cell
                    }
                }
            }

            is Timetable.Teacher,
            is Timetable.Room,
                -> TvorbaRozvrhu.createTimetableForTeacherOrRoom(
                viewModelScope,
                target = timetable,
                classListSource = classListSource,
                getTimetable = getKlass,
            ).value.upravitTabulku { week ->
                week.editCellsIndexed { address, cell ->
                    when (cell) {
                        is Cell.Header -> cell
                        is Cell.Edit -> when (timetable) {
                            is Timetable.Teacher -> cell.copy(teacher = "")
                            is Timetable.Room -> cell.copy(room = "")
                            else -> cell
                        }
                        is Cell.EmptyForEdit -> cell
                        is Cell.Empty -> Cell.EmptyForEdit("", address.lessonAddress)
                        else -> null
                    }
                }
            }

            is Timetable.DenVjec,
            is Timetable.HodinaVjec,
                -> TvorbaRozvrhu.createTimetableForDayOrLesson(
                viewModelScope,
                target = timetable,
                classListSource = classListSource,
                getTimetable = getKlass,
            ).value.upravitTabulku { week ->
                week.editCellsIndexed { address, cell ->
                    when (cell) {
                        is Cell.Header -> cell
                        is Cell.Edit -> cell
                        is Cell.EmptyForEdit -> cell
                        is Cell.Empty -> Cell.EmptyForEdit("", address.lessonAddress)
                        else -> null
                    }
                }
            }
        }

    fun najdiMivolnouTridu(
        den: Int,
        hodiny: List<Int>,
        progress: (String) -> Unit,
        onComplete: (List<Timetable.Room>?) -> Unit,
    ) {
        viewModelScope.launch {
            val plneTridy = tridy.value.flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                getTimetable(trida).value.let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.timetable
                }.justTimetable()[den].slice(hodiny).flatMap { hodina ->
                    hodina.map { bunka ->
                        bunka.roomLike
                    }
                }
            }
            progress("Už to skoro je")

            val vysledek = mistnosti.value.filter { it.zkratka !in plneTridy }.toMutableList()

            onComplete(vysledek)
        }
    }

    fun najdiMiVolnehoUcitele(
        den: Int,
        hodiny: List<Int>,
        progress: (String) -> Unit,
        onComplete: (List<Timetable.Teacher>?) -> Unit,
    ) {
        viewModelScope.launch {
            val zaneprazdneniUcitele = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                getTimetable(trida).value.let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.timetable
                }.drop(1)[den].drop(1).slice(hodiny).flatMap { hodina ->
                    hodina.map { bunka ->
                        bunka.teacherLike
                    }
                }
            }
            progress("Už to skoro je")

            val vysledek =
                vyucujici.value.drop(1).filter { it.zkratka !in zaneprazdneniUcitele && it.zkratka in vyucujici2.value }.toMutableList()

            onComplete(vysledek)
        }
    }

    fun loadFile() {
        removeTimetable()
        viewModelScope.launch {
            savedTimetableSource.removedSavedTimetable()
            editedTimetableSource.removedEditedTimetable()
            savedTimetableSource.loadFile()
            delay(500.milliseconds)
            savedTimetableSource.copyTimetables {
                editedTimetableSource.loadData(
                    it?.map { klass, data ->
                        println(klass)
                        AdvancedWeek(data.toDataForEdit(klass).justTimetable().removeEmptys())
                    }
                )
            }
        }
    }

    fun edit(modify: AdvancedWeek.() -> AdvancedWeek) = viewModelScope.launch {
        (timetable.value as? Timetable.Class)?.let {
            editedTimetableSource.editTimetable(it, modify)
        }
    }

    fun removeTimetable() {
        savedTimetableSource.removedSavedTimetable()
    }

    fun download() {
        viewModelScope.launch {
            val data = editedTimetableSource.timetables.value
                ?.map { k, w -> w.toTimetableDataForEdit(hodiny.value, k).toTimetableData() }
                .toJson()
            val today = today()

            FileKit.saveFile(
                extension = "rozvrh",
                baseName = "UPRAVNY_ROZVRH-${today.year}-${today.monthNumber}-${today.dayOfMonth}",
                bytes = data.encodeToByteArray(),
            )
        }
    }

    fun uploadChanges() {
        viewModelScope.launch {
            editedTimetableSource.removedEditedTimetable()
            editedTimetableSource.loadFile()
        }
    }

    fun changeView() {
        _isShowingChanges.value = !_isShowingChanges.value
    }

    fun findConflicts() = findConflicts { getTimetable2(it).timetable?.justTimetable() }

    private fun findConflictsAfterEdit(edit: AdvancedWeek.() -> AdvancedWeek): List<String> {
        require(timetable.value is Timetable.Class)
        val edited = editedTimetableSource.getTimetable(timetable.value as Timetable.Class).mapState(viewModelScope) { it?.edit() }

        val conflicts = findConflicts { requestedTimetable ->
            getTimetable2(requestedTimetable) { klass ->
                when (klass) {
                    timetable.value -> edited.mapState(viewModelScope) { it?.days?.let(::Uspech) ?: ZadnaData() }
                    else -> getTimetable(klass)
                }
            }.timetable?.justTimetable()
        }

        return conflicts
    }

    private fun findConflicts(
        getTimetable: (Timetable) -> WeekForEdit?,
    ): List<String> {
        val p1 = vyucujici.value.flatMap { teacher ->
            val week = getTimetable(teacher)
            week?.flatMapIndexed { dayIndex, day ->
                day.mapIndexed { lessonIndex, lesson ->
                    if (lesson.size > 1 && !lesson.areAllCellsSame()) "V ${teacher.zkratka} ${Seznamy.dny1Pad[dayIndex]} ${Seznamy.hodiny1Pad[lessonIndex]}"
                    else null
                }
            }.orEmpty()
        }.filterNotNull()
        val p2 = mistnosti.value.flatMap { room ->
            val week = getTimetable(room)
            week?.flatMapIndexed { dayIndex, day ->
                day.mapIndexed { lessonIndex, lesson ->
                    if (lesson.size > 1 && !lesson.areAllCellsSame()) "M ${room.zkratka} ${Seznamy.dny1Pad[dayIndex]} ${Seznamy.hodiny1Pad[lessonIndex]}"
                    else null
                }
            }.orEmpty()
        }.filterNotNull()
        return p1 + p2
    }

    fun findConflictsAfterSwitch(
        address1: LessonAddress,
        address2: LessonAddress,
    ) = findConflictsAfterEdit {
        switchLessons(address1, address2)
    }

    fun remember(address: Address?) {
        _memory.value = address
    }

    fun move(
        to: LessonAddress,
    ) {
        val memory = memory.value
        require(memory != null)
        edit {
            when (memory) {
                is LessonAddress -> moveLesson(memory, to)
                is CellAddress -> moveCell(memory, to)
            }
        }
        remember(null)
    }

    fun switch(
        with: CellAddress,
    ) {
        val memory = memory.value
        require(memory != null)
        edit {
            when (memory) {
                is LessonAddress -> switchLessons(memory, with.lessonAddress)
                is CellAddress -> switchCells(memory, with)
            }
        }
        remember(null)
    }

    fun findSwitch(address: CellAddress): List<String> {
        val timetable = timetable.value
        require(timetable is Timetable.Class)
        val busyTeachers = tridy.value.flatMap { klass ->
            val lesson = editedTimetableSource.getTimetable(klass).value?.get(address.lessonAddress).orEmpty()
            lesson.map { cell ->
                cell.teacher
            }
        }

        val classWeek = editedTimetableSource.getTimetable(timetable).value
        val teachersInClass = classWeek?.flatMapCells { cell ->
            cell.teacher
        }.orEmpty()

        val freeTeachers = vyucujici.value.map { it.zkratka }
            .filter { it !in busyTeachers }
            .filter { it in teachersInClass }

        val currentTeachers = classWeek?.get(address.lessonAddress).orEmpty().map { it.teacher }

        val currentTeacherSchedules = currentTeachers.mapNotNull { v ->
            getTimetable2(vyucujici.value.first { it.zkratka == v }).timetable?.justTimetable()
        }

        val freeLessons = currentTeacherSchedules.first().indices.flatMap { dayIndex ->
            currentTeacherSchedules.first().first().indices.map { lessonIndex ->
                val areAllFree = currentTeacherSchedules.all { week ->
                    val lesson = week[dayIndex][lessonIndex]
                    lesson.singleOrNull() is Cell.EmptyForEdit
                }
                if (areAllFree) LessonAddress(dayIndex, lessonIndex) else null
            }
        }.filterNotNull()

        val swappableLessons = classWeek?.flatMapLessonsIndexed { lessonAddress, lesson ->
            val areTeachersFree = lesson.all { c -> c.teacher in freeTeachers }
            if (areTeachersFree && lessonAddress in freeLessons) lessonAddress else null
        }.orEmpty().filterNotNull()

        return swappableLessons.map {
            "H ${Seznamy.dny1Pad[it.dayIndex]} ${Seznamy.hodiny1Pad[it.lessonIndex]}"
        }
    }

    fun whatIsWhere(address: CellAddress): List<String> {
        val vse = tridy.value.map { klass ->
            val lesson = editedTimetableSource.getTimetable(klass).value?.get(address.lessonAddress).orEmpty()
            "${klass.zkratka}: " + lesson.joinToString { bunka ->
                "${bunka.subject} (${bunka.room})"
            }
        }

        return vse.map { "T $it" }
    }

    fun findRoom(address: CellAddress): List<String> {
        val fullRooms = tridy.value.flatMap { klass ->
            val lesson = editedTimetableSource.getTimetable(klass).value?.get(address.lessonAddress).orEmpty()
            lesson.map { cell ->
                cell.room
            }
        }

        val freeRooms = mistnosti.value.filter { it.zkratka !in fullRooms }

        return freeRooms.map { "U ${it.zkratka}" }
    }

    fun editRoom(address: CellAddress, room: String) {
        edit {
            editCell(address) {
                it.copy(room = room)
            }
        }
    }

    val changes = editedTimetableSource.timetables.combineStates(viewModelScope, savedTimetableSource.timetables) { new, old ->
        if (new == null || old == null) return@combineStates emptyList()
        (old.timetables.flatMap { (klass, tyden) ->
            tyden.toDataForEdit(klass).justTimetable().flatMapIndexed { iDne, den ->
                den.flatMapIndexed { iHodiny, hodina ->
                    hodina.mapIndexed { iBunky, bunka ->
                        Triple(0, bunka, CellAddress(iDne, iHodiny, iBunky))
                    }
                }
            }
        } + new.timetables.flatMap { (_, tyden) ->
            tyden.flatMapCellsIndexed { address, cell ->
                Triple(1, cell, address)
            }
        }).groupBy { it.second.klass to it.second.address }.mapNotNull { (_, v) ->
            if (v.first().second.address is LessonAddress) return@mapNotNull null

            println(v)
            val oldB = v.single { it.first == 0 }
            val newB = v.single { it.first == 1 }
            if (oldB.second == newB.second && oldB.third == newB.third) null
            else Change(
                from = oldB.second as Cell.Edit,
                fromLocation = oldB.third,
                to = newB.second as Cell.Edit,
                toLocation = newB.third,
            )
        }
    }
}

private fun TimetableData.toDataForEdit(klass: String): TimetableDataForEdit = editCells { cell, address ->
    cell.run {
        when (this) {
            is Cell.Absent, is Cell.DayOff, is Cell.Removed, Cell.Empty -> Cell.EmptyForEdit(klass, address.lessonAddress, false)
            is Cell.ForEdit -> this
            is Cell.Normal -> Cell.Edit(room, subject, teacher, klass, group, address, false)
            is Cell.ST -> Cell.Edit("", subject, "T", klass, "", address, false)
        }
    }
}

fun <T> TimetableData.editCells(
    editCell: (Cell, CellAddress) -> T,
) = mapIndexed { dayIndex, day ->
    day.mapIndexed { lessonIndex, lesson ->
        lesson.mapIndexed { cellIndex, cell ->
            editCell(cell, CellAddress(dayIndex - 1, lessonIndex - 1, cellIndex))
        }
    }
}

fun TimetableDataForEdit.editLessons(
    editLesson: (List<CellForEdit>, LessonAddress) -> List<CellForEdit>,
): TimetableDataForEdit = mapIndexed { dayIndex, day ->
    day.mapIndexed { lessonIndex, lesson ->
        @Suppress("UNCHECKED_CAST")
        if (dayIndex == 0 || lessonIndex == 0) lesson
        else editLesson(lesson as List<CellForEdit>, LessonAddress(dayIndex - 1, lessonIndex - 1))
    }
}

private fun TimetableDataForEdit.mark(original: TimetableDataForEdit) = editLessons { newLesson, address ->
    val isCellDeleted = original[address].find { originalCell -> originalCell.address !in newLesson.map(CellForEdit::address) }
    (newLesson.map { newCell ->
        val originalCell = original[address].find { it.address == newCell.address }
        if (originalCell == null || originalCell != newCell) newCell.copy(isEdited = true) else newCell.copy(isEdited = false)
    } + List(if (isCellDeleted != null) 1 else 0) {
        Cell.EmptyForEdit(isCellDeleted!!.klass, address, true)
    }).let { lesson: LessonForEdit ->
        val empty = lesson.find { it is Cell.EmptyForEdit }
        if (empty != null && lesson.size > 1) lesson - empty else lesson
    }
}

fun toLocalTime(it: String) = it.split(":").map(String::toInt).toLocalTime()
fun List<LocalTime>.toRange() = this[0]..this[1]
fun List<Int>.toLocalTime() = LocalTime(this[0], this[1])

fun <T, U> Timetables<T>.map(transform: (klass: String, T) -> U) = Timetables(
    type = type,
    timetables = timetables.mapValues { transform(it.key, it.value) }
)

private inline fun CellForEdit.ifEdit(
    then: Cell.Edit.() -> CellForEdit,
) = when (this) {
    is Cell.EmptyForEdit -> this
    is Cell.Edit -> then()
}

private fun LessonForEdit.areAllCellsSame() = size > 1 && all {
    it.subject == first().subject && it.teacher == first().teacher && it.room == first().room && it.group == first().group
} || size % 2 == 0 && size > 1 && count { it.subject.startsWith("S:") } == count() / 2 && count { it.subject.startsWith("L:") } == count() / 2
