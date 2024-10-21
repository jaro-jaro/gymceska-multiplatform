package cz.jaro.gymceska.rozvrh

import com.fleeksoft.ksoup.nodes.Document
import cz.jaro.gymceska.Offline
import cz.jaro.gymceska.OfflineRuzneCasti
import cz.jaro.gymceska.Online
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

object TvorbaRozvrhu {

    private val dny = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne", "Rden", "Pi")
    fun createTimetableForClass(
        type: TimetableType,
        doc: Document,
        klass: String,
    ): Week = listOf(
        listOf(
            listOf(
                Cell.Header(
                    title = weekParity(type),
                )
            )
        ) + doc
            .body()
            .getElementsByClass("bk-timetable-body").first()!!
            .getElementById("main")!!
            .getElementsByClass("bk-timetable-hours").first()!!
            .getElementsByClass("bk-hour-wrapper")
            .take(10)
            .map { hodina ->
                val num = hodina.getElementsByClass("num").first()!!
                val hour = hodina.getElementsByClass("hour").first()!!

                listOf(
                    Cell.Header(
                        title = num.text(),
                        subtitle = hour.text(),
                    )
                )
            }
    ) + doc
        .body()
        .getElementsByClass("bk-timetable-body").first()!!
        .getElementById("main")!!
        .getElementsByClass("bk-timetable-row")
        .mapIndexed { i, timeTableRow ->
            listOf(
                listOf(
                    Cell.Header(
                        title = dny[i],
                        subtitle = date(type, i),
                    )
                )
            ) + timeTableRow
                .getElementsByClass("bk-cell-wrapper").first()!!
                .getElementsByClass("bk-timetable-cell")
                .take(10)
                .map { timetableCell ->
                    timetableCell.getElementsByClass("day-item").first()
                        ?.getElementsByClass("day-item-hover")
                        ?.flatMap { dayItemHover ->
                            val data = dayItemHover.attr("data-detail").let<String, CellData>(Json::decodeFromString)
                            val baseCell = when (data) {
                                is CellData.Normal -> dayItemHover.getElementsByClass("day-flex").first()?.let { dayFlex ->
                                    Cell.Normal(
                                        room = dayFlex
                                            .getElementsByClass("top").first()!!
                                            .getElementsByClass("right").first()
                                            ?.text()
                                            ?: "",
                                        subject = dayFlex
                                            .getElementsByClass("middle").first()!!
                                            .text(),
                                        teacher = dayFlex
                                            .getElementsByClass("bottom").first()!!
                                            .text(),
                                        group = dayFlex
                                            .getElementsByClass("top").first()!!
                                            .getElementsByClass("left").first()
                                            ?.text()
                                            ?: "",
                                        klass = klass,
                                        changeInfo = data.changeinfo?.takeUnless { it.isBlank() },
                                        subjectName = data.subjecttext.substringBefore(" | ", ""),
                                        teacherName = data.teacher ?: "",
                                        theme = data.theme ?: "",
                                    )
                                } ?: Cell.Empty

                                is CellData.Absent -> Cell.Absent(
                                    reason = data.absentinfo ?: "",
                                    reasonText = data.infoAbsentName ?: "",
                                    klass = klass,
                                )

                                is CellData.Removed -> Cell.Removed(
                                    reasonText = data.removedinfo?.substringBefore(" (") ?: "",
                                    subject = data.removedinfo?.substringInParentheses()?.substringBefore(", ", "") ?: "",
                                    teacherName = data.removedinfo?.substringInParentheses()?.substringAfter(", ", "") ?: "",
                                    klass = klass,
                                )
                            }

                            if (data is CellData.Normal && data.hasAbsent == true) {
                                val bef = data.absentInfoText?.substringBefore(" | ", "") ?: ""
                                listOf(
                                    baseCell,
                                    Cell.Absent(
                                        reason = bef.substringBefore(" (", "Absc"),
                                        reasonText = data.absentInfoText?.substringAfter(" | ", "") ?: "",
                                        group = bef.substringInParentheses(),
                                        klass = klass,
                                    )
                                )
                            } else listOf(baseCell)
                        }
                        ?.distinct()
                        ?.let { cells ->
                            val st = cells.filterIsInstance<Cell.Normal>()
                                .filter { it.subject == "ST" }
                                .ifEmpty { null }
                                ?.let { sts ->
                                    Cell.ST(
                                        klass = klass,
                                        groups = sts.map {
                                            Cell.ST.STGroup(
                                                changeInfo = it.changeInfo,
                                                group = it.group,
                                                theme = it.theme,
                                                teacher = it.teacher,
                                                teacherName = it.teacherName,
                                            )
                                        }.distinct(),
                                    )
                                }
                            if (st != null)
                                cells.filterIsInstance<Cell.Normal>()
                                    .filter { it.subject != "ST" } + cells.filter { it !is Cell.Normal } + st
                            else cells
                        }
                        ?.ifEmpty {
                            listOf(Cell.Empty)
                        }
                        ?: timetableCell.getElementsByClass("day-item-volno").first()
                            ?.getElementsByClass("day-off")?.first()
                            ?.let {
                                listOf(
                                    Cell.DayOff(
                                        reasonText = it.text(),
                                        klass = klass,
                                    )
                                )
                            }
                        ?: listOf(Cell.Empty)
                }
        }

    suspend fun createTimetableForTeacherOrRoom(
        target: Timetable,
        type: TimetableType,
        repo: Repository,
    ): Result {
        require(target is Timetable.Room || target is Timetable.Teacher)

        val seznamNazvu = repo.tridy.value.drop(1)

        val novaTabulka = emptyTyden(target)

        val nejstarsi = seznamNazvu.fold(null as LocalDateTime?) { zatimNejstarsi, trida ->

            val result = repo.ziskatRozvrh(trida, type)

            if (result !is Uspech) return result

            result.rozvrh.forEachIndexed trida@{ i, den ->
                den.forEachIndexed den@{ j, hodina ->
                    if (i == 0 || j == 0) {
                        novaTabulka[i][j] = mutableListOf(hodina.single())
                        return@den
                    }
                    hodina.forEach hodina@{ bunka ->
                        if (bunka is Cell.Empty) {
                            return@hodina
                        }
                        if (bunka is Cell.ST && target is Timetable.Teacher) {
                            val group = bunka.groups.find {
                                it.teacher == target.zkratka
                            } ?: return@hodina
                            novaTabulka[i][j] += Cell.Normal(
                                changeInfo = group.changeInfo,
                                subjectName = bunka.subjectName,
                                subject = bunka.subject,
                                teacher = group.teacher,
                                teacherName = group.teacherName,
                                klass = bunka.klass,
                                group = group.group,
                                theme = group.theme,
                            )
                            return@hodina
                        }
                        val zajimavaVec = when (target) {
                            is Timetable.Teacher -> bunka.teacherLike
                            is Timetable.Room -> bunka.roomLike
                            else -> throw IllegalArgumentException()
                        }
                        if (zajimavaVec == target.zkratka) {
                            novaTabulka[i][j] += bunka
                        }
                    }
                }
            }

            if (result.zdroj !is Offline) zatimNejstarsi
            else if (zatimNejstarsi == null || result.zdroj.ziskano < zatimNejstarsi) result.zdroj.ziskano
            else zatimNejstarsi
        }
        novaTabulka.forEachIndexed { i, den ->
            if (den.getOrNull(1)?.singleOrNull() is Cell.DayOff) return@forEachIndexed
            den.forEachIndexed { j, hodina ->
                hodina.ifEmpty {
                    novaTabulka[i][j] += Cell.Empty
                }
            }
        }
        novaTabulka[0][0][0] = Cell.Header(title = weekParity(type))
        return if (nejstarsi == null) Uspech(novaTabulka, Online)
        else Uspech(novaTabulka, OfflineRuzneCasti(nejstarsi))
    }

    suspend fun createTimetableForDayOrLesson(
        target: Timetable,
        type: TimetableType,
        repo: Repository,
    ): Result {
        require(target is Timetable.DenVjec || target is Timetable.HodinaVjec)

        val seznamNazvu = repo.tridy.value.drop(1)

        val novaTabulka = emptyTyden(target, seznamNazvu.count())

        val nejstarsi = seznamNazvu.fold(null as LocalDateTime?) { zatimNejstarsi, trida ->

            val result = repo.ziskatRozvrh(trida, type)

            if (result !is Uspech) return result

            val rozvrhTridy = result.rozvrh

            if (target is Timetable.DenVjec) {
                novaTabulka[seznamNazvu.indexOf(trida) + 1][0] = mutableListOf(Cell.Header(title = trida.zkratka))
                rozvrhTridy[target.index].drop(1).forEachIndexed den@{ j, hodina ->
                    novaTabulka[0][j + 1] = rozvrhTridy[0][j + 1].toMutableList()
                    hodina.forEach hodina@{ bunka ->
                        novaTabulka[seznamNazvu.indexOf(trida) + 1][j + 1] += bunka
                    }
                }
            }

            if (target is Timetable.HodinaVjec) {
                novaTabulka[0][seznamNazvu.indexOf(trida) + 1] = mutableListOf(Cell.Header(title = trida.zkratka))
                rozvrhTridy.drop(1).forEachIndexed trida@{ i, den ->
                    novaTabulka[i + 1][0] = rozvrhTridy[i + 1][0].toMutableList()
                    den.drop(1).singleOrGet(target.index - 1).forEach hodina@{ bunka ->
                        novaTabulka[i + 1][seznamNazvu.indexOf(trida) + 1] += bunka
                    }
                }
            }

            if (result.zdroj !is Offline) zatimNejstarsi
            else if (zatimNejstarsi == null || result.zdroj.ziskano < zatimNejstarsi) result.zdroj.ziskano
            else zatimNejstarsi
        }
        novaTabulka.forEachIndexed { i, den ->
            if (den.getOrNull(1)?.singleOrNull() is Cell.DayOff) return@forEachIndexed
            den.forEachIndexed { j, hodina ->
                hodina.ifEmpty {
                    novaTabulka[i][j] += Cell.Empty
                }
            }
        }
        novaTabulka[0][0][0] = Cell.Header(title = weekParity(type))
        return if (nejstarsi == null) Uspech(novaTabulka, Online)
        else Uspech(novaTabulka, OfflineRuzneCasti(nejstarsi))
    }

    private fun emptyTyden(
        target: Timetable,
        classCount: Int = 0,
    ): MutableList<MutableList<MutableList<Cell>>> {
        val vyska = when (target) {
            is Timetable.DenVjec -> classCount
            is Timetable.HodinaVjec -> 5
            else -> 5
        }
        val sirka = when (target) {
            is Timetable.DenVjec -> 10
            is Timetable.HodinaVjec -> classCount
            else -> 10
        }

        val newTable = MutableList(vyska + 1) { MutableList(sirka + 1) { mutableListOf<Cell>() } }
        return newTable
    }

    fun blankTyden() = emptyTyden(Timetable.Class("")).map { den ->
        den.map {
            listOf(Cell.Empty)
        }
    }

    private fun date(
        stalost: TimetableType,
        dayOfWeekIndex: Int,
    ): String {
        val weekStart = weekStart(stalost)
        val date = weekStart?.plus(DatePeriod(days = dayOfWeekIndex))
        return date?.run { "${dayOfMonth}.\n${monthNumber}." } ?: ""
    }

    private fun weekStart(
        stalost: TimetableType,
    ): LocalDate? {
        val today = today()
        val startOfWeek = today.startOfWeek()
        return when (stalost) {
            TimetableType.ThisWeek -> startOfWeek
            TimetableType.NextWeek -> startOfWeek.plus(DatePeriod(days = 7))
            TimetableType.Permanent -> null
        }
    }

    private fun weekParity(
        stalost: TimetableType,
    ): String {
        val today = today()
        val weekNumber = today.getSchoolWeekNumber()
        return when (stalost) {
            TimetableType.ThisWeek -> (weekNumber % 2 == 0).toParityChar()
            TimetableType.NextWeek -> (weekNumber % 2 == 1).toParityChar()
            TimetableType.Permanent -> null
        }?.toString() ?: ""
    }

    private fun Boolean.toParityChar() = if (this) 'S' else 'L'

    private fun LocalDate.getSchoolWeekNumber(): Int {
        // First week is the week containing 4th September
        val september4th = LocalDate(year, 9, 4)
        val firstWeekStart = september4th.startOfWeek()
        val daysFromFirstWeekStart = firstWeekStart.daysUntil(this)
        return (daysFromFirstWeekStart / 7) + 1
    }

    private fun LocalDate.startOfWeek(): LocalDate {
        val startOfWeekOffset = dayOfWeek.isoDayNumber - 1
        return minus(DatePeriod(days = startOfWeekOffset))
    }
}

//private fun <E> MutableList<E>.takeInPlace(n: Int) = retainAll(take(n))

private fun <E> List<E>.singleOrGet(index: Int) = singleOrNull() ?: get(index)

val Result.tabulka
    get() = when (this) {
        is Uspech -> rozvrh
        else -> null
    }

fun Result.upravitTabulku(edit: (Week) -> Week) = when (this) {
    is Uspech -> copy(rozvrh = edit(rozvrh))
    else -> this
}

fun Week.filtrovatTabulku(
    mujRozvrh: Boolean = false,
    mojeSkupiny: Set<String> = emptySet(),
) = map { den ->
    den.filtrovatDen(mujRozvrh, mojeSkupiny)
}

fun Day.filtrovatDen(
    mujRozvrh: Boolean = false,
    mojeSkupiny: Set<String> = emptySet(),
) = map { hodina ->
    hodina.filtrovatHodinu(mujRozvrh, mojeSkupiny)
}

fun Lesson.filtrovatHodinu(
    mujRozvrh: Boolean = false,
    mojeSkupiny: Set<String> = emptySet(),
): Lesson {
    return if (!mujRozvrh) this
    else filter {
        it.classLike.isBlank() || it.classLike.trim() in mojeSkupiny
    }.map { mojeBunka ->
        val spojene = filter { bunka ->
            mojeBunka.teacherLike == bunka.teacherLike && mojeBunka.roomLike == bunka.roomLike && mojeBunka.subjectLike == bunka.subjectLike
        }
        when (mojeBunka) {
            is Cell.Absent -> mojeBunka.copy(
                group = spojene.map { it.classLike }.distinct().joinToString(", ")
            )

            is Cell.Normal -> mojeBunka.copy(
                group = spojene.map { it.classLike }.distinct().joinToString(", ")
            )

            else -> mojeBunka
        }
    }.ifEmpty { listOf(Cell.Empty) }
}

fun String.substringInParentheses() = substringIn("(", ")")
fun String.substringIn(start: String, end: String) = substringAfter(start, "").substringBefore(end, "")