package cz.jaro.gymceska.rozvrh

import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

enum class TimetableType(val code: String) {
    ThisWeek("Actual"),
    NextWeek("Next"),
    Permanent("Permanent");

    companion object
}

val TimetableType.nameWhen: String
    get() = when (this) {
        TimetableType.ThisWeek -> if (today().dayOfWeek.isWorkDay()) "tento týden" else "minulý týden"
        TimetableType.NextWeek -> "příští týden"
        TimetableType.Permanent -> "vždy"
    }

val TimetableType.nameNominative: String
    get() = when (this) {
        TimetableType.ThisWeek -> if (today().dayOfWeek.isWorkDay()) "Tento týden" else "Minulý týden"
        TimetableType.NextWeek -> "Příští týden"
        TimetableType.Permanent -> "Stálý"
    }

private fun DayOfWeek.isWorkDay() = isoDayNumber in 1..5

fun TimetableType.Companion.defaultToday() = defaultByDay(today().dayOfWeek)

fun TimetableType.Companion.defaultByDay(day: DayOfWeek) =
    if (day.isWorkDay()) TimetableType.ThisWeek
    else TimetableType.NextWeek