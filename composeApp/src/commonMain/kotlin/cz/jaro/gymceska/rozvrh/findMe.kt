package cz.jaro.gymceska.rozvrh

import cz.jaro.gymceska.Result
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.mapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

fun findMe(
    settings: FindMeSettings, coroutineScope: CoroutineScope,
    classes: List<Timetable.Class>, rooms: List<Timetable.Room>, teachers: List<Timetable.Teacher>,
    unlockedRooms: Set<String> = emptySet(), wholeRooms: Set<String> = emptySet(),
    myTeachers: Set<String> = emptySet(), nonTrainers: Set<String>,
    getTimetable: (Timetable.Class) -> StateFlow<Result<out TimetableData>>
): StateFlow<FindMeResult?> =
    if (settings.findRoom)
        findMeFreeRoom(
            settings = settings, getTimetable = getTimetable,
            coroutineScope = coroutineScope, classes = classes, rooms = rooms,
            unlockedRooms = unlockedRooms, wholeRooms = wholeRooms,
        ).mapState(coroutineScope) {
            it?.let {
                FindMeResult(rooms = it)
            }
        }
    else
        findMeFreeTeacher(
            settings = settings, getTimetable = getTimetable,
            coroutineScope = coroutineScope, classes = classes, teachers = teachers,
            myTeachers = myTeachers, nonTrainers = nonTrainers,
        ).mapState(coroutineScope) {
            it?.let {
                FindMeResult(teachers = it)
            }
        }

data class FindMeSettings(
    val findRoom: Boolean,
    val dayIndex: Int,
    val lessonIndices: List<Int>,
    val filters: List<FindMeFilter> = emptyList(),
    val type: TimetableType = TimetableType.Permanent,
)

data class FindMeResult(
    val teachers: List<Timetable.Teacher> = emptyList(),
    val rooms: List<Timetable.Room> = emptyList(),
)

private fun findMeFreeTeacher(
    coroutineScope: CoroutineScope,
    settings: FindMeSettings,
    classes: List<Timetable.Class>,
    teachers: List<Timetable.Teacher>,
    myTeachers: Set<String>,
    nonTrainers: Set<String>,
    getTimetable: (Timetable.Class) -> StateFlow<Result<out TimetableData>>,
) =
    combineStates(coroutineScope, classes.map { trida ->
        getTimetable(trida).mapState(coroutineScope) { result ->
            if (result !is Uspech) {
                return@mapState null
            }
            result.timetable.justTimetable()[settings.dayIndex]
                .slice(settings.lessonIndices)
                .flatMap { lesson ->
                    lesson.map { cell ->
                        cell.teacherLike
                    }
                }
        }
    }) {
        if (it.any { it == null }) null
        else it.filterNotNull().flatten()
    }.mapState(coroutineScope) { occupiedTeachers ->
        if (occupiedTeachers == null) return@mapState null

        val result = teachers
            .filter { it.zkratka !in occupiedTeachers && it.zkratka in nonTrainers }
            .toMutableList()

        if (FindMeFilter.JustMy in settings.filters) result.retainAll {
            it.zkratka in myTeachers
        }

        result.toList()
    }

private fun findMeFreeRoom(
    coroutineScope: CoroutineScope,
    settings: FindMeSettings,
    classes: List<Timetable.Class>,
    rooms: List<Timetable.Room>,
    unlockedRooms: Set<String>,
    wholeRooms: Set<String>,
    getTimetable: (Timetable.Class) -> StateFlow<Result<out TimetableData>>,
) =
    combineStates(coroutineScope, classes.map { klass ->
        getTimetable(klass).mapState(coroutineScope) { result ->
            if (result !is Uspech) {
                return@mapState null
            }
            result.timetable.justTimetable()[settings.dayIndex]
                .slice(settings.lessonIndices)
                .flatMap { lesson ->
                    lesson.map { cell ->
                        cell.roomLike
                    }
                }
        }
    }) {
        if (it.any { it == null }) null
        else it.filterNotNull().flatten()
    }.mapState(coroutineScope) { occupiedRooms ->
        if (occupiedRooms == null) return@mapState null

        val result = rooms.filter { it.zkratka !in occupiedRooms }.toMutableList()

        if (FindMeFilter.JustUnlocked in settings.filters) result.retainAll {
            it.zkratka in unlockedRooms
        }
        if (FindMeFilter.JustWhole in settings.filters) result.retainAll {
            it.zkratka in wholeRooms
        }

        result.toList()
    }