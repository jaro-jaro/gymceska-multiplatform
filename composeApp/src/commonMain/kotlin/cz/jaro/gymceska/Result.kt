package cz.jaro.gymceska

sealed interface Result

data object TridaNeexistuje : Result

data object ZadnaData : Result

data object Error : Result

data class Uspech(
    val rozvrh: TimetableData,
    val zdroj: ZdrojRozvrhu,
) : Result