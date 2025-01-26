package cz.jaro.gymceska.rozvrh.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.flattenMergeStates
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.FindMeSettings
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.Timetable.Class
import cz.jaro.gymceska.rozvrh.Timetable.DenVjec
import cz.jaro.gymceska.rozvrh.Timetable.HodinaVjec
import cz.jaro.gymceska.rozvrh.Timetable.Room
import cz.jaro.gymceska.rozvrh.Timetable.Teacher
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.TvorbaRozvrhu
import cz.jaro.gymceska.rozvrh.copy
import cz.jaro.gymceska.rozvrh.dny
import cz.jaro.gymceska.rozvrh.editCells
import cz.jaro.gymceska.rozvrh.editor.CellAddress
import cz.jaro.gymceska.rozvrh.hodiny
import cz.jaro.gymceska.rozvrh.timetable
import cz.jaro.gymceska.rozvrh.upravitTabulku
import cz.jaro.gymceska.topHeaders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class RozvrhManualViewModel(
    private val params: Parameters,
    private val timetableSource: LocalTimetableSource,
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
                    'D' -> DenVjec::class
                    'H' -> HodinaVjec::class
                    'M' -> Room::class
                    'T' -> Class::class
                    'V' -> Teacher::class
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
        is DenVjec -> "D-$zkratka"
        is HodinaVjec -> "H-$zkratka"
        is Room -> "M-$zkratka"
        is Class -> "T-$zkratka"
        is Teacher -> "V-$zkratka"
    }

    lateinit var navigator: Navigator

    private val classListSource = timetableSource.classListSource
    val loaded = timetableSource.type.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds)) { it != null }
    val tridy = classListSource.classes
    val mistnosti = classListSource.rooms
    val vyucujici = classListSource.teachers
    private val vyucujici2 = classListSource.vyucujici2

    val hodiny = tridy.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds)) {
        timetableSource.getTimetable(
            klass = tridy.value.firstOrNull() ?: return@mapState emptyList(),
        ).value.timetable.also(::println)?.topHeaders()?.map {
            it.subtitle.split(" - ").map(::toLocalTime).toRange()
        } ?: emptyList()
    }

    val vjec = combineStates(
        viewModelScope,
        tridy, mistnosti, vyucujici,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { tridy, mistnosti, vyucujici ->
        if (params.decoded == null) return@combineStates null
        val zkratka = params.decoded.zkratka
        when (params.decoded.type) {
            Class::class -> tridy.find { it.zkratka == zkratka }
            Room::class -> mistnosti.find { it.zkratka == zkratka }
            Teacher::class -> vyucujici.find { it.zkratka == zkratka }
            DenVjec::class -> Seznamy.dny.find { it.zkratka == zkratka }
            HodinaVjec::class -> Seznamy.hodiny.find { it.zkratka == zkratka }
            else -> error("Invalid type")
        }
    }

    private fun RozvrhManual(
        vjec: Timetable,
    ) = Route.RozvrhManual(
        vjec = vjec.encodeArgument(),
    )

    fun vybratRozvrh(vjec: Timetable) {
        viewModelScope.launch {
            navigator.navigate(
                RozvrhManual(
                    vjec = vjec,
                )
            )
        }
    }

    val zoom = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::zoom)
    val alwaysTwoRowCells = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::alwaysTwoRowCells)

    val result = vjec.mapState(viewModelScope) { vjec ->
        if (vjec == null) MutableStateFlow(null)
        else when (vjec) {
            is Class -> timetableSource.getTimetable(
                klass = vjec,
            ).upravitTabulku(viewModelScope) {
                it.editCells { cell ->
                    if (cell is Cell.Data) cell.copy(klass = "") else cell
                }
            }

            is Teacher,
            is Room,
                -> TvorbaRozvrhu.createTimetableForTeacherOrRoom(
                viewModelScope,
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it) },
            ).upravitTabulku(viewModelScope) { week ->
                week.editCells { cell ->
                    if (cell is Cell.Normal) when (vjec) {
                        is Teacher -> cell.copy(teacher = "")
                        is Room -> cell.copy(room = "")
                        else -> cell
                    }
                    else cell
                }
            }

            is DenVjec,
            is HodinaVjec,
                -> TvorbaRozvrhu.createTimetableForDayOrLesson(
                viewModelScope,
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it) },
            )
        }
    }.flattenMergeStates(viewModelScope)

    fun findMe(
        settings: FindMeSettings,
    ) = cz.jaro.gymceska.rozvrh.findMe(
        settings = settings, coroutineScope = viewModelScope,
        classes = tridy.value, rooms = mistnosti.value, teachers = vyucujici.value,
        nonTrainers = vyucujici2.value, getTimetable = timetableSource::getTimetable,
    )

    fun loadFile() {
        removeTimetable()
        viewModelScope.launch {
            timetableSource.loadFile()
        }
    }

    fun removeTimetable() {
        timetableSource.removedSavedTimetable()
    }
}

fun <T: Cell, U> List<List<List<T>>>.editCells(
    editCell: (T) -> U,
) = map { day ->
    day.map { lesson ->
        lesson.mapNotNull { cell ->
            editCell(cell)
        }
    }
}

fun <T: Cell, U> List<List<List<T>>>.editCellsIndexed(
    editCell: (CellAddress, T) -> U,
) = mapIndexed { dayIndex, day ->
    day.mapIndexed { lessonIndex, lesson ->
        lesson.mapIndexed { cellIndex, cell ->
            editCell(CellAddress(dayIndex - 1, lessonIndex - 1, cellIndex), cell)
        }.filterNotNull()
    }
}

fun toLocalTime(it: String) = it.split(":").map(String::toInt).toLocalTime()
fun List<LocalTime>.toRange() = this[0]..this[1]
fun List<Int>.toLocalTime() = LocalTime(this[0], this[1])