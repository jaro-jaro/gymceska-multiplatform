package cz.jaro.gymceska.ukoly

sealed interface UkolyState {

    data object Nacitani : UkolyState

    data class Nacteno(
        val ukoly: List<JednoduchyUkol>,
    ) : UkolyState
}