package cz.jaro.gymceska

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
sealed interface PrepnoutRozvrhWidget {
    @Serializable
    data object OPulnoci : PrepnoutRozvrhWidget

    @Serializable
    data class PoKonciVyucovani(
        val poHodin: Int,
    ) : PrepnoutRozvrhWidget

    @Serializable
    data class VCas(
        private val hodin: Int,
        private val minut: Int,
    ) : PrepnoutRozvrhWidget {
        val cas: LocalTime get() = LocalTime(hodin, minut)

        constructor(cas: LocalTime) : this(cas.hour, cas.minute)
    }
}
