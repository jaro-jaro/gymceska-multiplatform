package cz.jaro.gymceska

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val routes =
    listOf(Route.Rozvrh::class, Route.Ukoly::class, Route.SpravceUkolu::class, Route.Nastaveni::class, Route.RozvrhManual::class, Route.RozvrhEditor::class)

@Serializable
@SerialName("Route")
sealed interface Route {

    @Serializable
    @SerialName("rozvrh")
    data class Rozvrh(
        val vjec: String,
        val x: Int? = null,
        val y: Int? = null,
    ) : Route

    @Serializable
    @SerialName("rozvrh-manual")
    data class RozvrhManual(
        val vjec: String,
    ) : Route

    @Serializable
    @SerialName("rozvrh-editor")
    data class RozvrhEditor(
        val vjec: String,
    ) : Route

    @Serializable
    @SerialName("ukoly")
    data object Ukoly : Route

    @Serializable
    @SerialName("spravce-ukolu")
    data object SpravceUkolu : Route

    @Serializable
    @SerialName("nastaveni")
    data object Nastaveni : Route
}