package cz.jaro.gymceska.rozvrh

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import cz.jaro.gymceska.ClassListSource
import cz.jaro.gymceska.Day
import cz.jaro.gymceska.Lesson
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Success
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Week
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.cornerHeader
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.startHeaders
import cz.jaro.gymceska.successOrElse
import cz.jaro.gymceska.topHeaders
import cz.jaro.gymceska.ukoly.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

object TvorbaRozvrhu {

    private fun <T : Cell> T.toLesson() = listOf(this)
    private fun <T : Cell> lesson(it: T) = it.toLesson()
    private fun <T : Cell> List<T>.toLessons() = map(::lesson)

    private fun constructTimetable(
        cornerHeader: Cell.Header,
        topHeaders: List<Cell.Header>,
        startHeaders: List<Cell.Header>,
        data: Week,
    ): TimetableData = listOf(
        listOf(lesson(cornerHeader)) + topHeaders.toLessons()
    ) + startHeaders.zip(data).map { (header, day) ->
        listOf(lesson(header)) + day
    }

    private val dny = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne", "Rden", "Pi")

    fun createTimetableForClass(
        type: TimetableType,
        doc: Document,
        klass: String,
    ) = constructTimetable(
        cornerHeader = Cell.Header(
            title = weekParity(type),
        ),
        topHeaders = doc
            .timetableBody()
            .hourWrapper()
            .take(13)
            .map(::processHeader),
        startHeaders = dny.take(5).mapIndexed { i, day ->
            Cell.Header(
                title = day,
                subtitle = date(type, i),
            )
        },
        data = doc
            .timetableBody()
            .getElementsByClass("bk-timetable-row")
            .map { timeTableRow ->
                processTimetableRow(timeTableRow, klass)
            }
    )

    private fun processHeader(lesson: Element): Cell.Header {
        val num = lesson.getElementsByClass("num").first()!!
        val hour = lesson.getElementsByClass("hour").first()!!

        return Cell.Header(
            title = num.text(),
            subtitle = hour.text(),
        )
    }

    private fun processTimetableRow(
        timeTableRow: Element,
        klass: String
    ): Day = timeTableRow
        .getElementsByClass("bk-cell-wrapper").first()!!
        .getElementsByClass("bk-timetable-cell")
        .take(13)
        .map { timetableCell ->
            processLesson(timetableCell, klass)
        }

    private fun processLesson(
        timetableCell: Element,
        klass: String
    ): Lesson = timetableCell.getElementsByClass("day-item").first()
        ?.getElementsByClass("day-item-hover")
        ?.flatMap { dayItemHover ->
            processCell(dayItemHover, klass)
        }
        ?.distinct()
        ?.mergeTrainings(klass)
        ?.ifEmpty {
            lesson(Cell.Empty)
        }
        ?: timetableCell.getElementsByClass("day-item-volno").first()
            ?.getElementsByClass("day-off")?.first()
            ?.let {
                lesson(
                    Cell.DayOff(
                        reasonText = it.text(),
                        klass = klass,
                    )
                )
            }
        ?: lesson(Cell.Empty)

    private fun List<Cell.DataOrEmpty>.mergeTrainings(
        klass: String
    ): List<Cell.DataOrEmpty> {
        val st = filterIsInstance<Cell.Normal>()
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
        return if (st != null)
            filterIsInstance<Cell.Normal>()
                .filter { it.subject != "ST" } + filter { it !is Cell.Normal } + st
        else this
    }

    private fun processCell(
        dayItemHover: Element,
        klass: String
    ): List<Cell.DataOrEmpty> {
        val data = dayItemHover.attr("data-detail")
            .let<String, CellData>(Json::decodeFromString)
        val baseCells = when (data) {
            is CellData.Normal -> dayItemHover.getElementsByClass("day-flex")
                .first()?.let { dayFlex ->
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
                        subjectName = data.subjecttext.substringBefore(
                            " | ",
                            ""
                        ),
                        teacherName = data.teacher ?: "",
                        theme = data.theme ?: "",
                    ).toLesson()
                } ?: lesson(Cell.Empty)

            is CellData.Absent -> Cell.Absent(
                reason = data.absentinfo ?: "",
                reasonText = data.infoAbsentName ?: "",
                klass = klass,
            ).toLesson()

            is CellData.Removed -> data.removedinfo
                ?.removeSuffix(")")
                ?.split(")")
                ?.map { part ->
                    Cell.Removed(
                        reasonText = part.substringBefore(" ("),
                        subject = part.substringIn("(", ", "),
                        teacherName = part.substringAfterLast(", ", ""),
                        klass = klass,
                    )
                } ?: lesson(Cell.Empty)
        }

        return if (data is CellData.Normal && data.hasAbsent == true) {
            data.absentInfoText?.split("<br/>")?.map { info ->
                val bef = info.substringBefore(" | ", "")
                Cell.Absent(
                    reason = bef.substringBefore(" (", "Absc"),
                    reasonText = info.substringAfter(" | ", ""),
                    group = bef.substringInParentheses(),
                    klass = klass,
                )
            }.orEmpty() + baseCells
        } else baseCells
    }

    private fun Element.hourWrapper() =
        getElementsByClass("bk-timetable-hours").first()!!
            .getElementsByClass("bk-hour-wrapper")

    private fun Document.timetableBody() = body()
        .getElementsByClass("bk-timetable-body").first()!!
        .getElementById("main")!!

    fun createTimetableForTeacherOrRoom(
        coroutineScope: CoroutineScope,
        target: Timetable,
        classListSource: ClassListSource,
        getTimetable: (klass: Timetable.Class) -> StateFlow<Result<out TimetableData>>,
    ): StateFlow<Result<TimetableData>> {
        require(target is Timetable.Room || target is Timetable.Teacher)

        val classes = classListSource.classes.value

        return combineStates(coroutineScope, classes.map { getTimetable(it) }) { results ->

            val timetables: List<TimetableData> = results.successOrElse { return@combineStates it }

            val novaTabulka = emptyTyden(target)

            timetables.forEach { timetable ->
                timetable.forEachIndexed trida@{ i, den ->
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
            }

            novaTabulka.forEachIndexed { i, den ->
                if (den.getOrNull(1)?.singleOrNull() is Cell.DayOff) return@forEachIndexed
                den.forEachIndexed { j, hodina ->
                    hodina.ifEmpty {
                        novaTabulka[i][j] += Cell.Empty
                    }
                }
            }

            Success(novaTabulka)
        }
    }

    fun createTimetableForDayOrLesson(
        coroutineScope: CoroutineScope,
        target: Timetable,
        classListSource: ClassListSource,
        getTimetable: (klass: Timetable.Class) -> StateFlow<Result<out TimetableData>>,
    ): StateFlow<Result<TimetableData>> {
        require(target is Timetable.DenVjec || target is Timetable.HodinaVjec)

        val classes = classListSource.classes.value

        return combineStates(coroutineScope, classes.map { getTimetable(it) }) { results ->

            val timetables = results.successOrElse { return@combineStates it }

            val novaTabulka = emptyTyden(target, classes.count())


            if (target is Timetable.DenVjec)
                timetables.withIndex().zip(classes) { (i, timetable), klass ->
                    novaTabulka[i + 1][0] = mutableListOf(Cell.Header(title = klass.zkratka))
                    timetable[target.index].drop(1).forEachIndexed den@{ j, hodina ->
                        novaTabulka[0][j + 1] = timetable[0][j + 1].toMutableList()
                        hodina.forEach hodina@{ bunka ->
                            novaTabulka[i + 1][j + 1] += bunka
                        }
                    }
                }

            if (target is Timetable.HodinaVjec)
                timetables.withIndex().zip(classes) { (j, timetable), klass ->
                    novaTabulka[0][j + 1] = mutableListOf(Cell.Header(title = klass.zkratka))
                    timetable.drop(1).forEachIndexed klass@{ i, den ->
                        novaTabulka[i + 1][0] = timetable[i + 1][0].toMutableList()
                        den.drop(1).singleOrGet(target.index - 1).forEach hodina@{ bunka ->
                            novaTabulka[i + 1][j + 1] += bunka
                        }
                    }
                }

            novaTabulka.forEachIndexed { i, den ->
                if (den.getOrNull(1)?.singleOrNull() is Cell.DayOff) return@forEachIndexed
                den.forEachIndexed { j, hodina ->
                    hodina.ifEmpty {
                        novaTabulka[i][j] += Cell.Empty
                    }
                }
            }

            Success(novaTabulka)
        }
    }

    @PublishedApi
    internal fun emptyTyden(
        target: Timetable,
        classCount: Int = 0,
    ): MutableList<MutableList<MutableList<Cell>>> {
        val vyska = when (target) {
            is Timetable.DenVjec -> classCount
            is Timetable.HodinaVjec -> 5
            else -> 5
        }
        val sirka = when (target) {
            is Timetable.DenVjec -> 13
            is Timetable.HodinaVjec -> classCount
            else -> 13
        }

        val newTable = MutableList(vyska + 1) { MutableList(sirka + 1) { mutableListOf<Cell>() } }
        return newTable
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

fun <E> List<E>.singleOrGet(index: Int) = singleOrNull() ?: get(index)

fun TimetableData.filtrovatTabulku(
    mujRozvrh: Boolean = false,
    mojeSkupiny: Set<String> = emptySet(),
): TimetableData =
    listOf(
        listOf(listOf(cornerHeader())) + this.topHeaders()
            .map { listOf(it) }) + startHeaders().zip(this.justTimetable()) { h, day ->
        listOf(listOf(h)) + day.filtrovatDen(mujRozvrh, mojeSkupiny)
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
fun String.substringIn(start: String, end: String) =
    substringAfter(start, "").substringBefore(end, "")