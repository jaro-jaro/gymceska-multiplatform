package cz.jaro.gymceska.rozvrh

import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

enum class Stalost(val code: String) {
    ThisWeek("Actual"),
    NextWeek("Next"),
    Permanent("Permanent");

    companion object
}

val Stalost.nameWhen: String
    get() = when (this) {
        Stalost.ThisWeek -> if (today().dayOfWeek.isWorkDay()) "tento týden" else "minulý týden"
        Stalost.NextWeek -> "příští týden"
        Stalost.Permanent -> "vždy"
    }

val Stalost.nameNominative: String
    get() = when (this) {
        Stalost.ThisWeek -> if (today().dayOfWeek.isWorkDay()) "Tento týden" else "Minulý týden"
        Stalost.NextWeek -> "Příští týden"
        Stalost.Permanent -> "Stálý"
    }

private fun DayOfWeek.isWorkDay() = isoDayNumber in 1..5

fun Stalost.Companion.defaultToday() = defaultByDay(today().dayOfWeek)

fun Stalost.Companion.defaultByDay(day: DayOfWeek) =
    if (day.isWorkDay()) Stalost.ThisWeek
    else Stalost.NextWeek