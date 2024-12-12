package cz.jaro.gymceska

import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.editor.CellForEdit
import kotlin.jvm.JvmName

typealias TimetableData = List<List<List<Cell>>>
typealias TimetableDataForEdit = List<List<List<Cell.ForEdit>>>
typealias TimetableDataNotForEdit = List<List<List<Cell.NonEdit>>>
typealias Week = List<Day>
typealias Day = List<Lesson>
typealias Lesson = List<Cell.DataOrEmpty>
typealias AdvancedWeekData = List<List<List<Cell.Edit>>>
typealias AdvancedDay = List<List<Cell.Edit>>
typealias AdvancedLesson = List<Cell.Edit>
typealias WeekForEdit = List<DayForEdit>
typealias DayForEdit = List<LessonForEdit>
typealias LessonForEdit = List<CellForEdit>
typealias MutableWeek = MutableList<MutableDay>
typealias MutableDay = MutableList<MutableLesson>
typealias MutableLesson = MutableList<Cell.Edit>


@JvmName("justTimetableForEdit")
fun TimetableDataForEdit.justTimetable(): WeekForEdit =
    drop(1).map { it.drop(1).map { l -> l.map { c -> c as CellForEdit } } }

fun TimetableData.justTimetable(): Week = drop(1).map { it.drop(1).map { l -> l.map { c -> c as Cell.DataOrEmpty } } }
fun TimetableData.topHeaders() = first().drop(1).map { it.single() as Cell.Header }
fun TimetableData.startHeaders() = drop(1).map { it.first().single() as Cell.Header }
fun TimetableData.cornerHeader() = first().first().single() as Cell.Header