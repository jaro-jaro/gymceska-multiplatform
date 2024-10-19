package cz.jaro.gymceska.ukoly

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Ukol(
    val datum: String,
    val nazev: String,
    val predmet: String,
    val skupina: String,
    val id: @Serializable(with = MyUuidSerializer::class) Uuid,
)

const val PRVNI_MESIC_VE_SKOLNIM_ROCE = 8

fun dateFromUkol(ukol: Ukol) = dateFromString(ukol.datum)

fun dateFromString(dateString: String): LocalDate? {
    if (dateString == "0. 0.") return null

    val datum = dateString.replace(" ", "").split(".")
    val den = datum.getOrNull(0)?.toIntOrNull() ?: return null
    val mesic = datum.getOrNull(1)?.toIntOrNull() ?: return null

    val today = today()
    val tentoMesic = today.monthNumber
    val tentoRok = today.year

    val jePredVanoci = tentoMesic >= PRVNI_MESIC_VE_SKOLNIM_ROCE
    val ukolJePredVanoci = mesic >= PRVNI_MESIC_VE_SKOLNIM_ROCE

    val rok = when {
        jePredVanoci == ukolJePredVanoci -> tentoRok
        ukolJePredVanoci -> tentoRok - 1
        else -> tentoRok + 1
    }

    return LocalDate(rok, mesic, den)
}

@OptIn(ExperimentalUuidApi::class)
fun Ukol.zjednusit(stav: StavUkolu) = JednoduchyUkol(id = id, text = asString(), stav = stav)

@OptIn(ExperimentalUuidApi::class)
operator fun Ukol.Companion.invoke() = Ukol("0. 0.", "", "", "", Uuid.random())

fun Ukol.asString() = buildString {
    if (den != null) {
        +den!!
        +" "
    }
    +datum
    if (predmet.isNotBlank() || skupina.isNotBlank()) +" – "
    if (predmet.isNotBlank()) +predmet
    if (predmet.isNotBlank() && skupina.isNotBlank()) +" "
    if (skupina.isNotBlank()) +skupina
    +" – "
    +nazev
}

context(StringBuilder)
operator fun String.unaryPlus()  = also { append(this) }
context(StringBuilder)
operator fun Char.unaryPlus()  = also { append(this) }

private val Ukol.den get() = dateFromUkol(this)?.dayOfWeek?.zkratka

private val DayOfWeek.zkratka
    get() = when (this) {
        DayOfWeek.MONDAY -> "po"
        DayOfWeek.TUESDAY -> "út"
        DayOfWeek.WEDNESDAY -> "st"
        DayOfWeek.THURSDAY -> "čt"
        DayOfWeek.FRIDAY -> "pá"
        DayOfWeek.SATURDAY -> "so"
        DayOfWeek.SUNDAY -> "ne"
        else -> error("Unknown day of week: $this")
    }

fun today() = now().date
fun time() = now().time
fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
