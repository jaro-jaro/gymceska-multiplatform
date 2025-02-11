package cz.jaro.gymceska.widgets

import cz.jaro.gymceska.OnlineTimetableSource
import cz.jaro.gymceska.PrepnoutRozvrhWidget
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.Success
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.copy
import cz.jaro.gymceska.rozvrh.editCells
import cz.jaro.gymceska.rozvrh.filtrovatDen
import cz.jaro.gymceska.rozvrh.toLocalTime
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

private fun OnlineTimetableSource.rozvrhZobrazitNaDnesek(settings: SettingsFlow) =
    when (val nastaveni = settings.value.prepnoutRozvrhWidget) {
        is PrepnoutRozvrhWidget.OPulnoci -> true
        is PrepnoutRozvrhWidget.VCas -> {
            val cas = time()
            cas < nastaveni.cas
        }

        is PrepnoutRozvrhWidget.PoKonciVyucovani -> {
            val cas = Clock.System.now()
            val konecVyucovani = zjistitKonecVyucovani(settings)

            (cas - nastaveni.poHodin.hours).toLocalDateTime(TimeZone.currentSystemDefault()).time < konecVyucovani
        }
    }

private fun OnlineTimetableSource.zjistitKonecVyucovani(settings: SettingsFlow): LocalTime {
    val nastaveni = settings.value

    val result = getTimetable(nastaveni.mojeTrida, TimetableType.ThisWeek).value

    if (result !is Success) return LocalTime(0, 0)

    val tabulka = result.timetable

    val denTydne = today().dayOfWeek.isoDayNumber

    val den = tabulka.getOrNull(denTydne) ?: return LocalTime(12, 0)

    val hodina = den
        .mapIndexed { i, hodina -> i to hodina }
        .drop(1)
        .filter { (_, hodina) -> hodina.first().subjectLike.isNotBlank() }
        .lastOrNull { (_, hodina) ->
            hodina.any { bunka ->
                bunka.classLike.isEmpty() || bunka.classLike in nastaveni.mojeSkupiny
            }
        }
        ?.first
        ?: return LocalTime(12, 0)

    return tabulka.first()[hodina].first().teacherLike.split(" - ")[1].let(::toLocalTime)
}

fun OnlineTimetableSource.rozvrhWidgetData(settings: SettingsFlow): Pair<LocalDate, List<Cell.NonEdit>> {
    val nastaveni = settings.value
    val dnes = rozvrhZobrazitNaDnesek(settings)

    val den = today().plus(DatePeriod(days = if (dnes) 0 else 1))
    val cisloDne = den.dayOfWeek.isoDayNumber

    val stalost = if (cisloDne == 1 && !dnes) TimetableType.NextWeek else TimetableType.ThisWeek

    val hodiny = getTimetable(nastaveni.mojeTrida, stalost).value.let { result ->
        if (result !is Success) return@let listOf(Cell.Header("Žádná data!"))

        result
            .timetable.justTimetable()
            .getOrNull(cisloDne - 1)
            ?.editCells { cell ->
                if (cell is Cell.Data) cell.copy(klass = "") else cell
            }
            ?.filtrovatDen(true, nastaveni.mojeSkupiny)
            ?.mapNotNull { lesson -> lesson.firstOrNull() }
            ?.dropLastWhile { it is Cell.Empty }
            ?.ifEmpty {
                listOf(Cell.Header("Žádné hodiny!"))
            }
            ?: listOf(Cell.Header("Víkend"))
    }
    return Pair(den, hodiny)
}