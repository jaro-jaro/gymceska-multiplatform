package cz.jaro.gymceska.rozvrh.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.LocalTimetableSource
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Cell
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
import cz.jaro.gymceska.rozvrh.hodiny
import cz.jaro.gymceska.rozvrh.tabulka
import cz.jaro.gymceska.rozvrh.upravitTabulku
import cz.jaro.gymceska.topHeaders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.js.JsName
import kotlin.jvm.JvmName
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
        ).tabulka.also(::println)?.topHeaders()?.map {
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

    val result = vjec.map { vjec ->
        if (vjec == null) null
        else when (vjec) {
            is Class -> timetableSource.getTimetable(
                klass = vjec,
            ).upravitTabulku {
                it.editCells { cell ->
                    if (cell is Cell.Data) cell.copy(klass = "") else cell
                }
            }

            is Teacher,
            is Room,
                -> TvorbaRozvrhu.createTimetableForTeacherOrRoom(
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it) },
            ).upravitTabulku { week ->
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
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it) },
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    fun najdiMivolnouTridu(
        den: Int,
        hodiny: List<Int>,
        progress: (String) -> Unit,
        onComplete: (List<Room>?) -> Unit,
    ) {
        viewModelScope.launch {
            val plneTridy = tridy.value.flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                timetableSource.getTimetable(trida).let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.rozvrh
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
        onComplete: (List<Teacher>?) -> Unit,
    ) {
        viewModelScope.launch {
            val zaneprazdneniUcitele = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                timetableSource.getTimetable(trida).let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.rozvrh
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
            timetableSource.loadFile()
        }
    }

    fun removeTimetable() {
        timetableSource.removedSavedTimetable()
    }
}

fun TimetableData.editCells(
    editCell: (Cell) -> Cell,
) = map { day ->
    day.editCells(editCell)
}

@JsName("editCellsOfDay")
@JvmName("editCellsOfDay")
fun List<List<Cell>>.editCells(
    editCell: (Cell) -> Cell,
) = map { lesson ->
    lesson.map { cell ->
        editCell(cell)
    }
}

fun toLocalTime(it: String) = it.split(":").map(String::toInt).toLocalTime()
fun List<LocalTime>.toRange() = this[0]..this[1]
fun List<Int>.toLocalTime() = LocalTime(this[0], this[1])