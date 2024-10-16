package cz.jaro.gymceska.rozvrh

import cz.jaro.gymceska.rozvrh.FiltrNajdiMi.JenCele
import cz.jaro.gymceska.rozvrh.FiltrNajdiMi.JenOdemcene
import cz.jaro.gymceska.rozvrh.FiltrNajdiMi.JenSvi

enum class FiltrNajdiMi {
    JenOdemcene,
    JenCele,
    JenSvi,
}

fun List<FiltrNajdiMi>.text() = when {
    JenSvi in this -> "moji "
    JenCele in this && JenOdemcene in this -> "odemčené celé "
    JenOdemcene in this -> "odemčené "
    JenCele in this -> "celé "
    else -> ""
}