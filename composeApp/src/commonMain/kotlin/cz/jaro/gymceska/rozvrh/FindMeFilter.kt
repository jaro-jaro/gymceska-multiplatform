package cz.jaro.gymceska.rozvrh

import cz.jaro.gymceska.rozvrh.FindMeFilter.JustWhole
import cz.jaro.gymceska.rozvrh.FindMeFilter.JustUnlocked
import cz.jaro.gymceska.rozvrh.FindMeFilter.JustMy

enum class FindMeFilter {
    JustUnlocked,
    JustWhole,
    JustMy,
}

fun List<FindMeFilter>.text() = when {
    JustMy in this -> "moji "
    JustWhole in this && JustUnlocked in this -> "odemčené celé "
    JustUnlocked in this -> "odemčené "
    JustWhole in this -> "celé "
    else -> ""
}