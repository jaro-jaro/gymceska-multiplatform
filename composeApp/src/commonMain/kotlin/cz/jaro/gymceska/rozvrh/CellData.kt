package cz.jaro.gymceska.rozvrh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CellData {
    val subjecttext: String // {Předmět | }dn dd.mm. | H (hh:mm - hh:mm)

    @Serializable
    @SerialName("absent")
    data class Absent(
        override val subjecttext: String,
        val absentinfo: String? = null, // Pr
        @SerialName("InfoAbsentName")
        val infoAbsentName: String? = null, // předmět
        val removedinfo: String? = null, //
    ) : CellData

    @Serializable
    @SerialName("atom")
    data class Normal(
        override val subjecttext: String,
        val teacher: String? = null, // Mgr. Vyučující
        val room: String? = null, // Mis
        val group: String? = null, // Sk
        val theme: String? = null, // Téma hodiny
        val notice: String? = null, //
        val changeinfo: String? = null, // Důvod
        // Přesun z dd.mm., H: Pr, Vyučující{, Mis}
        // Suplování: Pr, Vyučující
        // Spojeno: Vyučující, Pr
        // Změna místnosti: Mis
        val homeworks: Nothing? = null, //
        val absencetext: Nothing? = null, //
        val hasAbsent: Boolean? = null, // Abs pro některé skupiny
        val absentInfoText: String? = null, // Pr (Sk) | předmět
    ) : CellData

    @Serializable
    @SerialName("removed")
    data class Removed(
        override val subjecttext: String,
        val removedinfo: String? = null, // Zrušeno (Pr, Vyučující)"
        val absentinfo: String? = null, //
        @SerialName("InfoAbsentName")
        val infoAbsentName: String? = null, //
    ) : CellData
}
