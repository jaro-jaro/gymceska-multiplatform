package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Route.Rozvrh
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Timetable.Class
import cz.jaro.gymceska.rozvrh.Timetable.DenVjec
import cz.jaro.gymceska.rozvrh.Timetable.HodinaVjec
import cz.jaro.gymceska.rozvrh.Timetable.Room
import cz.jaro.gymceska.rozvrh.Timetable.Teacher
import cz.jaro.gymceska.ukoly.unaryPlus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class RozvrhViewModel(
    private val params: Parameters,
    private val repo: Repository,
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

    val tridy = repo.tridy
    val mistnosti = repo.mistnosti
    val vyucujici = repo.vyucujici
    private val vyucujici2 = repo.vyucujici2
    private val odemkleMistnosti = repo.odemkleMistnosti
    private val velkeMistnosti = repo.velkeMistnosti

    val hodiny = flow {
        emit(repo.ziskatRozvrh(
            trida = repo.nastaveni.value.mojeTrida,
            stalost = TimetableType.ThisWeek,
        ).tabulka?.topHeaders()?.map {
            it.subtitle.split(" - ").map(::toLocalTime).toRange()
        } ?: emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    private fun toLocalTime(it: String) = it.split(":").map(String::toInt).toLocalTime()
    private fun List<LocalTime>.toRange() = this[0]..this[1]
    private fun List<Int>.toLocalTime() = LocalTime(this[0], this[1])

    val vjec = combineStates(
        viewModelScope,
        repo.nastaveni, tridy, mistnosti, vyucujici,
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

    private val _mujRozvrh = repo.nastaveni.mapState(
        viewModelScope, SharingStarted.WhileSubscribed(5.seconds)
    ) { nastaveni ->
        params.decoded?.mujRozvrh ?: nastaveni.defaultMujRozvrh
    }

    val mujRozvrh = combineStates(
        viewModelScope,
        _mujRozvrh, repo.nastaveni, vjec,
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
        vjec, repo.nastaveni,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { vjec, nastaveni ->
        vjec == nastaveni.mojeTrida
    }

    val zoom = repo.nastaveni.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::zoom)

    val currentlyDownloading = repo.currentlyDownloading

    val alwaysTwoRowCells = repo.nastaveni.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::alwaysTwoRowCells)

    val result = combine(vjec, mujRozvrh, repo.nastaveni, zobrazitMujRozvrh) { vjec, mujRozvrh, nastaveni, zobrazitMujRozvrh ->
        when (vjec) {
            is Class -> repo.ziskatRozvrh(
                trida = vjec,
                stalost = stalost,
            ).upravitTabulku {
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
                target = vjec,
                type = stalost,
                repo = repo
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
                type = stalost,
                repo = repo
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private fun Week.editCells(
        editCell: (Cell) -> Cell,
    ) = map { day ->
        day.map { lesson ->
            lesson.map { cell ->
                editCell(cell)
            }
        }
    }

    val stahnoutVse: () -> Unit = {
        viewModelScope.launch {
            repo.stahnoutVse()
        }
    }

    fun najdiMivolnouTridu(
        stalost: TimetableType,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        progress: (String) -> Unit,
        onComplete: (List<Room>?) -> Unit,
    ) {
        viewModelScope.launch {
            val plneTridy = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                repo.ziskatRozvrh(trida, stalost).let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.rozvrh
                }.drop(1)[den].drop(1).slice(hodiny).flatMap { hodina ->
                    hodina.map { bunka ->
                        bunka.roomLike
                    }
                }
            }
            progress("Už to skoro je")

            val vysledek = mistnosti.value.drop(1).filter { it.zkratka !in plneTridy }.toMutableList()

            if (FiltrNajdiMi.JenOdemcene in filtry) vysledek.retainAll {
                it.zkratka in odemkleMistnosti.value
            }
            if (FiltrNajdiMi.JenCele in filtry) vysledek.retainAll {
                it.zkratka in velkeMistnosti.value
            }

            onComplete(vysledek)
        }
    }

    fun najdiMiVolnehoUcitele(
        stalost: TimetableType,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        progress: (String) -> Unit,
        onComplete: (List<Teacher>?) -> Unit,
    ) {
        viewModelScope.launch {
            val zaneprazdneniUcitele = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                repo.ziskatRozvrh(trida, stalost).let { result ->
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

            val ucitele = repo.ziskaUcitele(repo.nastaveni.first().mojeTrida)
            if (FiltrNajdiMi.JenSvi in filtry) vysledek.retainAll {
                it.zkratka in ucitele
            }

            onComplete(vysledek)
        }
    }
}