package cz.jaro.gymceska.rozvrh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Vjec {
    val nazev: String
    val zkratka: String

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
