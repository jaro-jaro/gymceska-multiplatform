package cz.jaro.gymceska

import cz.jaro.gymceska.rozvrh.Week

sealed interface Result

data object TridaNeexistuje : Result

data object ZadnaData : Result

data object Error : Result

data class Uspech(
    val rozvrh: Week,
    val zdroj: ZdrojRozvrhu,
) : Result