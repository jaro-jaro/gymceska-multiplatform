package cz.jaro.gymceska.rozvrh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Timetable {
    val nazev: String
    val zkratka: String

    @Serializable
    data class Class(
        @SerialName("jmeno")
        override val nazev: String,
        val odkaz: String? = null,
    ) : Timetable {
        companion object {
            val HOME = Class("")
        }
        override val zkratka: String get() = nazev
    }

    @Serializable
    data class Room(
        @SerialName("jmeno")
        override val nazev: String,
        val napoveda: String? = null,
    ) : Timetable {
        override val zkratka: String get() = nazev
    }

    @Serializable
    data class Teacher(
        val jmeno: String,
        override val zkratka: String,
    ) : Timetable {
        override val nazev: String get() = if (zkratka.isNotBlank()) "$zkratka – $jmeno" else jmeno
    }

    sealed interface Indexed : Timetable {
        val index: Int
    }

    @Serializable
    data class DenVjec(
        override val nazev: String,
        override val zkratka: String,
        override val index: Int,
    ) : Indexed

    @Serializable
    data class HodinaVjec(
        override val nazev: String,
        override val zkratka: String,
        override val index: Int,
    ) : Indexed
}
