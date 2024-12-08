package cz.jaro.gymceska.widgets

import cz.jaro.gymceska.OnlineTimetableSource
import cz.jaro.gymceska.PrepnoutRozvrhWidget
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.Uspech
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

    if (result !is Uspech) return LocalTime(0, 0)

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

suspend fun OnlineTimetableSource.rozvrhWidgetData(settings: SettingsFlow): Pair<LocalDate, List<Cell>> {
    val nastaveni = settings.value
    val dnes = rozvrhZobrazitNaDnesek(settings)

    val den = today().plus(DatePeriod(days = if (dnes) 0 else 1))
    val cisloDne = den.dayOfWeek.isoDayNumber

    val stalost = if (cisloDne == 1 && !dnes) TimetableType.NextWeek else TimetableType.ThisWeek

    val hodiny = getTimetable(nastaveni.mojeTrida, stalost).value.let { result ->
        if (result !is Uspech) return@let listOf(Cell.Header("Žádná data!"))

        val tabulka = result.timetable

        tabulka
            .getOrNull(cisloDne)
            ?.asSequence()
            ?.drop(1)
            ?.mapIndexed { i, hodina -> i to hodina.filterIsInstance<Cell.NonHeader>() /*cast*/ }.also(::println)
            ?.filter { (_, hodina) -> hodina.first().subjectLike.isNotBlank() }.also(::println)
            ?.map { (i, hodina) ->
                hodina.map { bunka ->
                    when (bunka) {
                        is Cell.Absent -> bunka.copy(reason = "$i. ${bunka.reason}")
                        is Cell.DayOff -> bunka.copy(reasonText = "$i. ${bunka.reasonText}")
                        is Cell.Removed -> bunka.copy(subject = "$i. ${bunka.subject}")
                        is Cell.Normal -> bunka.copy(subject = "$i. ${bunka.subject}")
                        is Cell.ST -> bunka.copy(subject = "$i. ${bunka.subject}")
                        else -> Cell.Header(title = "$i.")
                    }
                }
            }
            ?.toList()
            ?.editCells { cell ->
                if (cell is Cell.Data) cell.copy(klass = "") else cell
            }
            ?.map { it.filterIsInstance<Cell.NonHeader>() /*cast*/ }
            ?.filtrovatDen(true, nastaveni.mojeSkupiny).also(::println)
            ?.mapNotNull { hodina -> hodina.firstOrNull() }.also(::println)
            ?.ifEmpty {
                listOf(
                    Cell.Header("Žádné hodiny!"),
                )
            }
            ?: listOf(Cell.Header("Víkend"))
    }
    return Pair(den, hodiny)
}