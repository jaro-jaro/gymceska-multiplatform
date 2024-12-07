package cz.jaro.gymceska

sealed interface Result<T>

class TridaNeexistuje<T> : Result<T>
class ZadnaData<T> : Result<T>
class Error<T> : Result<T>

data class Uspech<T>(
    val rozvrh: T,
    val zdroj: ZdrojRozvrhu,
) : Result<T>