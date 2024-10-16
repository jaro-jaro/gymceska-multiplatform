package cz.jaro.gymceska.rozvrh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Vjec {
    val nazev: String
    val zkratka: String

    fun toStringArgument() = when (this) {
        TridaVjec.HOME -> ""
        is DenVjec -> "D-$zkratka"
        is HodinaVjec -> "H-$zkratka"
        is MistnostVjec -> "M-$zkratka"
        is TridaVjec -> "T-$zkratka"
        is VyucujiciVjec -> "V-$zkratka"
    }
    companion object {
        fun fromString(string: String, tridy: List<TridaVjec>, mistnosti: List<MistnostVjec>, vyucujici: List<VyucujiciVjec>): Vjec {
            val zkratka = string.substringAfter('-')
            return when (string.first()) {
                'D' -> Seznamy.dny.find { it.zkratka == zkratka }!!
                'H' -> HodinaVjec("$zkratka. hodina", zkratka, zkratka.toInt())
                'M' -> mistnosti.find { it.zkratka == zkratka }!!
                'T' -> tridy.find { it.zkratka == zkratka }!!
                'V' -> vyucujici.find { it.zkratka == zkratka }!!
                else -> error("Invalid type")
            }
        }
    }

    @Serializable
    data class TridaVjec(
        @SerialName("jmeno")
        override val nazev: String,
        val odkaz: String? = null,
    ) : Vjec {
        companion object {
            val HOME = TridaVjec("")
        }
        override val zkratka: String get() = nazev
    }

    @Serializable
    data class MistnostVjec(
        @SerialName("jmeno")
        override val nazev: String,
        val napoveda: String? = null,
    ) : Vjec {
        override val zkratka: String get() = nazev
    }

    @Serializable
    data class VyucujiciVjec(
        val jmeno: String,
        override val zkratka: String,
    ) : Vjec {
        override val nazev: String get() = if (zkratka.isNotBlank()) "$zkratka – $jmeno" else jmeno
    }

    sealed interface Indexed : Vjec {
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
