package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.OnlineTimetableSource
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route.Rozvrh
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.flattenMergeStates
import cz.jaro.gymceska.getTeachers
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Timetable.Class
import cz.jaro.gymceska.rozvrh.Timetable.DenVjec
import cz.jaro.gymceska.rozvrh.Timetable.HodinaVjec
import cz.jaro.gymceska.rozvrh.Timetable.Room
import cz.jaro.gymceska.rozvrh.Timetable.Teacher
import cz.jaro.gymceska.topHeaders
import cz.jaro.gymceska.ukoly.unaryPlus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.LocalTime
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class RozvrhViewModel(
    private val params: Parameters,
    private val timetableSource: OnlineTimetableSource,
    private val settings: SettingsFlow,
) : ViewModel() {
    data class Parameters(
        val arg: String,
        val horScrollState: ScrollState,
        val verScrollState: ScrollState,
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

    private fun encodeArgument(
        vjec: Timetable,
        stalost: TimetableType?,
        mujRozvrh: Boolean?,
    ) = buildString {
        val zkratka = vjec.zkratka
        +when (vjec) {
            Class.HOME -> ""
            is DenVjec -> "D-$zkratka"
            is HodinaVjec -> "H-$zkratka"
            is Room -> "M-$zkratka"
            is Class -> "T-$zkratka"
            is Teacher -> "V-$zkratka"
        }
        if (vjec != Class.HOME && (stalost != null || mujRozvrh != null)) +"-"
        if (stalost != null) +stalost.name.first()
        if (mujRozvrh != null) +if (mujRozvrh) 'M' else 'C'
    }

    lateinit var navigator: Navigator

    private val classListSource = timetableSource.classListSource
    val tridy = classListSource.classes
    val mistnosti = classListSource.rooms
    val vyucujici = classListSource.teachers
    private val vyucujici2 = classListSource.vyucujici2
    private val odemkleMistnosti = classListSource.odemkleMistnosti
    private val velkeMistnosti = classListSource.velkeMistnosti

    val hodiny = flow {
        emit(
            timetableSource.getTimetable(
                klass = settings.value.mojeTrida,
                type = TimetableType.ThisWeek,
            ).value.timetable?.topHeaders()?.map {
                it.subtitle.split(" - ").map(::toLocalTime).toRange()
            } ?: emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val vjec = combineStates(
        viewModelScope,
        settings, tridy, mistnosti, vyucujici,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { nastaveni, tridy, mistnosti, vyucujici ->
        if (params.decoded == null) return@combineStates nastaveni.mojeTrida
        val zkratka = params.decoded.zkratka
        when (params.decoded.type) {
            Class::class -> tridy.find { it.zkratka == zkratka }
            Room::class -> mistnosti.find { it.zkratka == zkratka }
            Teacher::class -> vyucujici.find { it.zkratka == zkratka }
            DenVjec::class -> Seznamy.dny.find { it.zkratka == zkratka }
            HodinaVjec::class -> Seznamy.hodiny.find { it.zkratka == zkratka }
            else -> error("Invalid type")
        } ?: nastaveni.mojeTrida
    }

    val stalost = params.decoded?.stalost ?: TimetableType.defaultToday()

    private val _mujRozvrh = settings.mapState(
        viewModelScope, SharingStarted.WhileSubscribed(5.seconds)
    ) { nastaveni ->
        params.decoded?.mujRozvrh ?: nastaveni.defaultMujRozvrh
    }

    val mujRozvrh = combineStates(
        viewModelScope,
        _mujRozvrh, settings, vjec,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { mujRozvrh, nastaveni, vjec ->
        mujRozvrh && vjec == nastaveni.mojeTrida
    }

    private fun Rozvrh(
        vjec: Timetable,
        stalost: TimetableType? = null,
        mujRozvrh: Boolean? = null,
        x: Int? = null,
        y: Int? = null,
    ) = Rozvrh(
        vjec = encodeArgument(
            vjec = vjec,
            stalost = stalost,
            mujRozvrh = mujRozvrh,
        ),
        x = x,
        y = y,
    )

    fun vybratRozvrh(vjec: Timetable) {
        viewModelScope.launch {
            navigator.navigate(
                Rozvrh(
                    vjec = vjec,
                    mujRozvrh = _mujRozvrh.value,
                    stalost = stalost,
                )
            )
        }
    }

    fun zmenitStalost(stalost: TimetableType) {
        viewModelScope.launch {
            navigator.navigate(
                Rozvrh(
                    vjec = vjec.value,
                    mujRozvrh = _mujRozvrh.value,
                    stalost = stalost,
                    x = params.horScrollState.value,
                    y = params.verScrollState.value,
                )
            )
        }
    }

    fun zmenitMujRozvrh() {
        viewModelScope.launch {
            navigator.navigate(
                Rozvrh(
                    vjec = vjec.value,
                    mujRozvrh = !_mujRozvrh.value,
                    stalost = stalost,
                    x = params.horScrollState.value,
                    y = params.verScrollState.value,
                )
            )
        }
    }

    val zobrazitMujRozvrh = combineStates(
        viewModelScope,
        vjec, settings,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { vjec, nastaveni ->
        vjec == nastaveni.mojeTrida
    }

    val zoom = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::zoom)

    val currentlyDownloading = timetableSource.currentlyDownloading

    val alwaysTwoRowCells = settings.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::alwaysTwoRowCells)

    val result = combineStates(
        viewModelScope,
        vjec, mujRozvrh, settings, zobrazitMujRozvrh
    ) { vjec, mujRozvrh, nastaveni, zobrazitMujRozvrh ->
        if (vjec is Class && vjec.odkaz == null) MutableStateFlow(null)
        else when (vjec) {
            is Class -> timetableSource.getTimetable(
                klass = vjec,
                type = stalost,
            ).upravitTabulku(viewModelScope) {
                it.editCells { cell ->
                    if (cell is Cell.Data) cell.copy(klass = "") else cell
                }.filtrovatTabulku(
                    mujRozvrh = mujRozvrh && zobrazitMujRozvrh,
                    mojeSkupiny = nastaveni.mojeSkupiny,
                )
            }

            is Teacher,
            is Room,
                -> TvorbaRozvrhu.createTimetableForTeacherOrRoom(
                coroutineScope = viewModelScope,
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it, stalost) },
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
                coroutineScope = viewModelScope,
                target = vjec,
                classListSource = classListSource,
                getTimetable = { timetableSource.getTimetable(it, stalost) },
            )
        }
    }.flattenMergeStates(coroutineScope = viewModelScope)

    val stahnoutVse: () -> Unit = {
        viewModelScope.launch {
            timetableSource.downloadAll()
        }
    }

    fun najdiMivolnouTridu(
        stalost: TimetableType,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        onComplete: (List<Room>?) -> Unit,
    ) {
        viewModelScope.launch {
            najdiMiVolnouTridu(den, hodiny, tridy.value, mistnosti.value, filtry, odemkleMistnosti.value, velkeMistnosti.value, {
                timetableSource.getTimetable(it, stalost)
            }).collect(onComplete)
        }
    }

    fun najdiMiVolnehoUcitele(
        stalost: TimetableType,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        onComplete: (List<Teacher>?) -> Unit,
    ) {
        viewModelScope.launch {
            najdiMiVolnehoUcitele(den, hodiny, tridy.value, vyucujici.value, timetableSource.getTeachers(settings.value.mojeTrida), filtry, odemkleMistnosti.value, velkeMistnosti.value) {
                timetableSource.getTimetable(it, stalost)
            }.collect(onComplete)
        }
    }
}

suspend fun najdiMiVolnehoUcitele(
    day: Int,
    lessons: List<Int>,
    classes: List<Class>,
    teachers: List<Teacher>,
    myTeachers: Sequence<String> = emptySequence(),
    filters: List<FiltrNajdiMi> = emptyList(),
    zaneprazdneniUcitele: List<String> = emptyList(),
    vyucujici2: List<String> = emptyList(),
    getTimetable: (Class) -> StateFlow<Result<out TimetableData>>,
) = supervisorScope {
    combineStates(this, classes.drop(1).map { trida ->
        getTimetable(trida).mapState(this) { result ->
            if (result !is Uspech) {
                return@mapState null
            }
            result.timetable.drop(1)[day].drop(1).slice(lessons).flatMap { hodina ->
                hodina.map { bunka ->
                    bunka.teacherLike
                }
            }
        }
    }) {
        if (it.any { it == null }) null
        else it.filterNotNull().flatten()
    }.mapState(this) { occupiedTeachers ->
        if (occupiedTeachers == null) return@mapState null

        val result = teachers.drop(1).filter { it.zkratka !in zaneprazdneniUcitele && it.zkratka in vyucujici2 }.toMutableList()

        if (FiltrNajdiMi.JenSvi in filters) result.retainAll {
            it.zkratka in myTeachers
        }

        result
    }
}


suspend fun najdiMiVolnouTridu(
    day: Int,
    lessons: List<Int>,
    classes: List<Class>,
    rooms: List<Room>,
    filters: List<FiltrNajdiMi> = emptyList(),
    odemkleMistnosti: List<String> = emptyList(),
    velkeMistnosti: List<String> = emptyList(),
    getTimetable: (Class) -> StateFlow<Result<out TimetableData>>,
) = supervisorScope {
    combineStates(this, classes.drop(1).map { trida ->
        getTimetable(trida).mapState(this) { result ->
            if (result !is Uspech) {
                return@mapState null
            }
            result.timetable.drop(1)[day].drop(1).slice(lessons).flatMap { hodina ->
                hodina.map { bunka ->
                    bunka.roomLike
                }
            }
        }
    }) {
        if (it.any { it == null }) null
        else it.filterNotNull().flatten()
    }.mapState(this) { occupiedRooms ->
        if (occupiedRooms == null) return@mapState null

        val result = rooms.drop(1).filter { it.zkratka !in occupiedRooms }.toMutableList()

        if (FiltrNajdiMi.JenOdemcene in filters) result.retainAll {
            it.zkratka in odemkleMistnosti
        }
        if (FiltrNajdiMi.JenCele in filters) result.retainAll {
            it.zkratka in velkeMistnosti
        }

        result
    }
}

fun TimetableData.editCells(
    editCell: (Cell) -> Cell,
) = map { day ->
    day.editCells(editCell)
}

@JsName("editCellsOfDay")
@JvmName("editCellsOfDay")
fun <T : Cell> List<List<T>>.editCells(
    editCell: (T) -> T,
) = map { lesson ->
    lesson.map { cell ->
        editCell(cell)
    }
}

fun toLocalTime(it: String) = it.split(":").map(String::toInt).toLocalTime()
fun List<LocalTime>.toRange() = this[0]..this[1]
fun List<Int>.toLocalTime() = LocalTime(this[0], this[1])