package cz.jaro.gymceska.rozvrh.editor

import cz.jaro.gymceska.AdvancedDay
import cz.jaro.gymceska.AdvancedLesson
import cz.jaro.gymceska.AdvancedWeekData
import cz.jaro.gymceska.DayForEdit
import cz.jaro.gymceska.LessonForEdit
import cz.jaro.gymceska.MutableDay
import cz.jaro.gymceska.MutableLesson
import cz.jaro.gymceska.MutableWeek
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.TimetableDataForEdit
import cz.jaro.gymceska.WeekForEdit
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.dny
import kotlinx.datetime.LocalTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

sealed interface Address

@Serializable
data class CellAddress(
    val dayIndex: Int,
    val lessonIndex: Int,
    val cellIndex: Int,
) : Address {
    override fun toString() = "$dayIndex.$lessonIndex.$cellIndex"
}

val CellAddress.lessonAddress get() = LessonAddress(dayIndex, lessonIndex)

@Serializable
data class LessonAddress(
    val dayIndex: Int,
    val lessonIndex: Int,
) : Address {
    override fun toString() = "$dayIndex.$lessonIndex"
}

operator fun LessonAddress.get(cellIndex: Int) = CellAddress(dayIndex, lessonIndex, cellIndex)

@Serializable
data class Change(
    val from: Cell.Edit,
    val fromLocation: CellAddress,
    val to: Cell.Edit,
    val toLocation: CellAddress,
)

typealias CellForEdit = Cell.ForEditNonHeader<out Address>

@Serializable(AdvancedWeek.Serializer::class)
class AdvancedWeek {

    @PublishedApi
    internal val hold: AdvancedLesson?
    val days: AdvancedWeekData

    @PublishedApi
    internal constructor(
        days: AdvancedWeekData,
        hold: AdvancedLesson?,
    ) {
        this.days = days
        this.hold = hold
    }

    constructor(
        days: AdvancedWeekData,
    ) {
        this.days = days
        this.hold = null
    }

    inline fun editWeek(mutate: (MutableWeek) -> Unit) = AdvancedWeek(days.toMutableWeek().also(mutate), hold)

    fun hideLesson(address: LessonAddress) = AdvancedWeek(days, this[address]).removeLesson(address)
    fun showLesson(address: LessonAddress) = AdvancedWeek(addLesson(address, hold!!).days, null)

    @OptIn(ExperimentalSerializationApi::class)
    class Serializer : KSerializer<AdvancedWeek> {
        private val delegateSerializer = serializer<AdvancedWeekData>()
        override val descriptor = SerialDescriptor("AdvancedWeek", delegateSerializer.descriptor)
        override fun serialize(encoder: Encoder, value: AdvancedWeek) = encoder.encodeSerializableValue(delegateSerializer, value.days)
        override fun deserialize(decoder: Decoder) = AdvancedWeek(decoder.decodeSerializableValue(delegateSerializer))
    }
}

operator fun AdvancedWeek.get(address: LessonAddress) = days[address.dayIndex][address.lessonIndex]
operator fun AdvancedWeek.get(address: CellAddress) = days[address.dayIndex][address.lessonIndex][address.cellIndex]
inline fun AdvancedWeek.editDay(dayIndex: Int, mutate: (MutableDay) -> Unit) = editWeek { it[dayIndex].also(mutate) }
inline fun AdvancedWeek.editLesson(address: LessonAddress, mutate: (MutableLesson) -> Unit) =
    editDay(address.dayIndex) { it[address.lessonIndex].also(mutate) }

inline fun AdvancedWeek.editCell(address: CellAddress, mutate: (Cell.Edit) -> Cell.Edit) = editLesson(address.lessonAddress) {
    it[address.cellIndex] = mutate(it[address.cellIndex])
}

fun AdvancedWeek.moveCell(address1: CellAddress, address2: LessonAddress) =
    addCell(address2, this[address1]).removeCell(address1)

fun AdvancedWeek.moveLesson(address1: LessonAddress, address2: LessonAddress) =
    addLesson(address2, this[address1]).removeLesson(address1)

fun AdvancedWeek.switchCells(address1: CellAddress, address2: CellAddress) =
    moveCell(address1, address2.lessonAddress).moveCell(address2, address1.lessonAddress)

fun AdvancedWeek.switchLessons(address1: LessonAddress, address2: LessonAddress) =
    hideLesson(address1).moveLesson(address2, address1).showLesson(address2)

fun AdvancedWeek.removeCell(address: CellAddress) = editLesson(address.lessonAddress) { lesson ->
    lesson.removeAt(address.cellIndex)
}

fun AdvancedWeek.removeLesson(address: LessonAddress) = editLesson(address) { lesson ->
    lesson.clear()
}

fun AdvancedWeek.addCell(address: LessonAddress, cell: Cell.Edit) = editLesson(address) { cells ->
    cells.add(cell)
}

fun AdvancedWeek.addLesson(address: LessonAddress, lesson: AdvancedLesson) = lesson.fold(this) { acc, cell ->
    acc.addCell(address, cell)
}

inline fun AdvancedWeek.mutateDays(mutate: (MutableDay) -> Unit) = editWeek { it.forEach(mutate) }
inline fun AdvancedWeek.mutateLessons(mutate: (MutableLesson) -> Unit) = mutateDays { it.forEach(mutate) }
inline fun AdvancedWeek.mutateCells(map: (Cell.Edit) -> Cell.Edit) = mutateLessons { lesson ->
    lesson.forEachIndexed { i, cell ->
        lesson[i] = map(cell)
    }
}

inline fun <T> AdvancedWeek.transformWeek(transform: (WeekForEdit) -> T) = transform(days)
inline fun <T> AdvancedWeek.mapDays(transform: (DayForEdit) -> T) = transformWeek { it.map(transform) }
inline fun <T> AdvancedWeek.mapLessons(transform: (LessonForEdit) -> T) = mapDays { it.map(transform) }
inline fun <T> AdvancedWeek.mapCells(transform: (CellForEdit) -> T) = mapLessons { it.map(transform) }
inline fun <T> AdvancedWeek.mapDaysIndexed(transform: (index: Int, DayForEdit) -> T) = transformWeek { it.mapIndexed(transform) }
inline fun <T> AdvancedWeek.mapLessonsIndexed(transform: (index: LessonAddress, LessonForEdit) -> T) = mapDaysIndexed { dayIndex, day ->
    day.mapIndexed { lessonIndex, lesson ->
        transform(LessonAddress(dayIndex, lessonIndex), lesson)
    }
}

inline fun <T> AdvancedWeek.mapCellsIndexed(transform: (index: CellAddress, CellForEdit) -> T) =
    mapLessonsIndexed { lessonAddress, lesson -> lesson.mapIndexed { cellIndex, cell -> transform(lessonAddress[cellIndex], cell) } }

inline fun <T> AdvancedWeek.flatMapLessons(transform: (LessonForEdit) -> T) = mapLessons(transform).flatten()
inline fun <T> AdvancedWeek.flatMapLessonsIndexed(transform: (index: LessonAddress, LessonForEdit) -> T) =
    mapLessonsIndexed(transform).flatten()

inline fun <T> AdvancedWeek.flatMapCells(transform: (CellForEdit) -> T) = mapCells(transform).flatten().flatten()
inline fun <T> AdvancedWeek.flatMapCellsIndexed(transform: (index: CellAddress, CellForEdit) -> T) =
    mapCellsIndexed(transform).flatten().flatten()

inline fun AdvancedWeek.filterDays(predicate: (DayForEdit) -> Boolean) = editWeek { it.filter(predicate) }
inline fun AdvancedWeek.filterLessons(predicate: (LessonForEdit) -> Boolean) = mutateDays { it.filter(predicate) }
inline fun AdvancedWeek.filterCells(predicate: (CellForEdit) -> Boolean) = mutateLessons { it.filter(predicate) }

fun AdvancedWeekData.toMutableWeek(): MutableWeek = map { it.toMutableDay() }.toMutableList()
fun AdvancedDay.toMutableDay(): MutableDay = map { it.toMutableList() }.toMutableList()

val CellForEdit.subject
    get() = when (this) {
        is Cell.EmptyForEdit -> ""
        is Cell.Edit -> subject
    }
val CellForEdit.room
    get() = when (this) {
        is Cell.EmptyForEdit -> ""
        is Cell.Edit -> room
    }

val CellForEdit.group
    get() = when (this) {
        is Cell.EmptyForEdit -> ""
        is Cell.Edit -> group
    }
val CellForEdit.teacher
    get() = when (this) {
        is Cell.EmptyForEdit -> ""
        is Cell.Edit -> teacher
    }

fun TimetableDataForEdit.toTimetableData(): TimetableData = map { day ->
    day.map { lesson ->
        lesson.map {
            when (it) {
                is Cell.EmptyForEdit -> Cell.Empty
                is Cell.Edit -> Cell.Normal(room = it.room, subject = it.subject, group = it.group, teacher = it.teacher)
                is Cell.Header -> it
            }
        }
    }
}

fun WeekForEdit.removeEmptys(): AdvancedWeekData = map { day ->
    day.map { lesson ->
        lesson.mapNotNull {
            when (it) {
                is Cell.EmptyForEdit -> null
                is Cell.Edit -> it
            }
        }
    }
}

fun AdvancedWeek.addEmptys(klass: String): WeekForEdit = mapLessonsIndexed { address, lesson ->
    lesson.ifEmpty {
        listOf(Cell.EmptyForEdit(klass, address))
    }
}

fun AdvancedWeek.toTimetableDataForEdit(lessons: List<ClosedRange<LocalTime>>, klass: String) = addEmptys(klass).addHeaders(lessons)
fun WeekForEdit.addHeaders(lessons: List<ClosedRange<LocalTime>>): TimetableDataForEdit =
    listOf(listOf(listOf(Cell.Header())) + lessons.mapIndexed { i, time ->
        listOf(Cell.Header("$i", "${time.start.hour}:${time.start.minute} - ${time.endInclusive.hour}:${time.endInclusive.minute}"))
    }) + zip(Seznamy.dny) { dayLessons, day ->
        listOf(listOf(Cell.Header(day.zkratka))) + dayLessons
    }