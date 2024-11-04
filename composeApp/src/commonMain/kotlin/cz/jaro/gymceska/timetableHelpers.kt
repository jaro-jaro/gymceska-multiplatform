package cz.jaro.gymceska

import cz.jaro.gymceska.rozvrh.Cell
import kotlin.jvm.JvmName

typealias TimetableData = List<List<List<Cell>>>
typealias PermanentTimetableData = List<List<List<Cell.Permanent>>>
typealias Week = List<Day>
typealias Day = List<Lesson>
typealias Lesson = List<Cell.NonHeader>
typealias PermanentWeek = List<PermanentDay>
typealias PermanentDay = List<PermanentLesson>
typealias PermanentLesson = List<Cell.PermanentNonHeader>

@JvmName("justPermanentTimetable")
fun PermanentTimetableData.justTimetable(): PermanentWeek =
    drop(1).map { it.drop(1).map { l -> l.map { c -> c as Cell.PermanentNonHeader } } }

fun TimetableData.justTimetable(): Week = drop(1).map { it.drop(1).map { l -> l.map { c -> c as Cell.NonHeader } } }
fun TimetableData.topHeaders() = first().drop(1).map { it.single() as Cell.Header }
fun TimetableData.startHeaders() = drop(1).map { it.first().single() as Cell.Header }
fun TimetableData.cornerHeader() = first().first().single() as Cell.Header