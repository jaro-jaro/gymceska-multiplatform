package cz.jaro.gymceska

import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.js.JsName
import kotlin.time.Duration.Companion.seconds

interface ClassListSource {
    val classes: StateFlow<List<Timetable.Class>>
    val rooms: StateFlow<List<Timetable.Room>>
    val teachers: StateFlow<List<Timetable.Teacher>>
}

sealed interface Timetables {
    val type: TimetableType
    val timetables: Map<Timetable.Class, TimetableData>
}

data class PermanentTimetables(
    override val timetables: Map<Timetable.Class, PermanentTimetableData>,
) : Timetables {
    override val type = TimetableType.Permanent
}

data object EmptyTimetables : Timetables {
    override val type = TimetableType.ThisWeek
    override val timetables = emptyMap<Timetable.Class, TimetableData>()
}

data class CurrentTimetables(
    override val timetables: Map<Timetable.Class, TimetableData>,
    override val type: TimetableType,
) : Timetables


fun interface UserOnlineManager {
    fun isOnline(): Boolean

    @JsName("isOnlineFlow")
    val isOnline
        get() = flow {
            while (currentCoroutineContext().isActive) {
                emit(isOnline())
                delay(5.seconds)
            }
        }
}

interface AdminManager {
    val isAdmin: StateFlow<Boolean>
}

data object NoAdminManager : AdminManager {
    override val isAdmin = MutableStateFlow(false)
}