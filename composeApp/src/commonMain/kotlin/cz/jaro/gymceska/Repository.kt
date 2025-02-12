package cz.jaro.gymceska

import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlin.js.JsName
import kotlin.time.Duration.Companion.seconds

interface ClassListSource {
    val classes: StateFlow<List<Timetable.Class>>
    val rooms: StateFlow<List<Timetable.Room>>
    val teachers: StateFlow<List<Timetable.Teacher>>
}

@Serializable
data class Timetables <T>(
    val type: TimetableType,
    val timetables: Map<String, T>,
)

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