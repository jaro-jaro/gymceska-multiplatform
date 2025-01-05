package cz.jaro.gymceska

sealed interface Result<T>

class ZadnaData<T> : Result<T>

data class Uspech<T>(
    val timetable: T,
) : Result<T>