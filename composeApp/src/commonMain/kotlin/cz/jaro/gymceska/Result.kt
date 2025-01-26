package cz.jaro.gymceska

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmName

sealed interface Result<T>
sealed interface Fail<T> : Result<T>
class Offline<T> : Fail<T>
class Downloading<T> : Fail<T>

data class Success<T>(
    val timetable: T,
) : Result<T>


val <T> Result<T>.timetable get() = if (this is Success) timetable else null

fun <T> Fail<*>.changeType() = when (this) {
    is Downloading -> Downloading<T>()
    is Offline -> Offline<T>()
}

fun <T, U> Result<T>.editTimetable(edit: (T) -> U) = when (this) {
    is Success -> Success(timetable = edit(this.timetable))
    is Downloading -> Downloading()
    is Offline -> Offline()
}

fun <T, U> StateFlow<Result<T>>.editTimetable(coroutineScope: CoroutineScope, edit: (T) -> U) = mapState(coroutineScope) { result ->
    result.editTimetable(edit)
}

@JvmName("successOrElseOut")
inline fun <T> Sequence<Result<out T>>.successOrElse(orElse: (Fail<T>) -> Nothing) = when {
    any { it is Offline } -> orElse(Offline())
    any { it is Downloading } -> orElse(Downloading())
    else -> filterIsInstance<Success<T>>().map { it.timetable }
}
@JvmName("successOrElseOut")
inline fun <T> Iterable<Result<out T>>.successOrElse(orElse: (Fail<T>) -> Nothing) =
    asSequence().successOrElse(orElse).toList()
@JvmName("successOrElseOut")
inline fun <T> Array<Result<out T>>.successOrElse(orElse: (Fail<T>) -> Nothing) =
    asSequence().successOrElse(orElse).toList()

inline fun <T> Sequence<Result<T>>.successOrElse(orElse: (Fail<T>) -> Nothing) = when {
    any { it is Offline } -> orElse(Offline())
    any { it is Downloading } -> orElse(Downloading())
    else -> filterIsInstance<Success<T>>().map { it.timetable }
}
inline fun <T> Iterable<Result<T>>.successOrElse(orElse: (Fail<T>) -> Nothing) =
    asSequence().successOrElse(orElse).toList()
inline fun <T> Array<Result<T>>.successOrElse(orElse: (Fail<T>) -> Nothing) =
    asSequence().successOrElse(orElse).toList()

fun <T> Iterable<Result<out T>>.worst() = when {
    any { it is Offline } -> Offline()
    any { it is Downloading } -> Downloading()
    else -> Success(filterIsInstance<Success<T>>().map { it.timetable })
}
fun <T> Sequence<Result<out T>>.worst() = when {
    any { it is Offline } -> Offline()
    any { it is Downloading } -> Downloading()
    else -> Success(filterIsInstance<Success<T>>().map { it.timetable })
}
fun <T> Array<Result<out T>>.worst() = when {
    any { it is Offline } -> Offline()
    any { it is Downloading } -> Downloading()
    else -> Success(filterIsInstance<Success<T>>().map { it.timetable })
}
