package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.combineStates
import cz.jaro.gymceska.mapState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class RozvrhViewModel(
    private val params: Parameters,
    private val repo: Repository,
) : ViewModel() {
    data class Parameters(
        val vjec: String?,
        val stalost: Stalost?,
        val mujRozvrh: Boolean?,
        val horScrollState: ScrollState,
        val verScrollState: ScrollState,
    )

    lateinit var navigovat: (Route) -> Unit

    val tridy = repo.tridy
    val mistnosti = repo.mistnosti
    val vyucujici = repo.vyucujici
    private val vyucujici2 = repo.vyucujici2
    private val odemkleMistnosti = repo.odemkleMistnosti
    private val velkeMistnosti = repo.velkeMistnosti

    val vjec = combineStates(
        viewModelScope,
        repo.nastaveni, tridy, vyucujici, mistnosti,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { nastaveni, tridy, vyucujici, mistnosti ->
        params.vjec?.let { Vjec.fromString(it, tridy, mistnosti, vyucujici) } ?: nastaveni.mojeTrida
    }

    val stalost = params.stalost ?: Stalost.dnesniEntries().first()

    private val _mujRozvrh = repo.nastaveni.mapState(
        viewModelScope, SharingStarted.WhileSubscribed(5.seconds)
    ) { nastaveni ->
        params.mujRozvrh ?: nastaveni.defaultMujRozvrh
    }

    val mujRozvrh = combineStates(
        viewModelScope,
        _mujRozvrh, repo.nastaveni, vjec,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { mujRozvrh, nastaveni, vjec ->
        mujRozvrh && vjec == nastaveni.mojeTrida
    }

    fun vybratRozvrh(vjec: Vjec) {
        viewModelScope.launch {
            navigovat(
                Route.Rozvrh(
                    vjec = (if (vjec.nazev == "HOME") repo.nastaveni.value.mojeTrida else vjec).toStringArgument(),
                    mujRozvrh = _mujRozvrh.value,
                    stalost = stalost.name,
                )
            )
        }
    }

    fun zmenitStalost(stalost: Stalost) {
        viewModelScope.launch {
            navigovat(
                Route.Rozvrh(
                    vjec = vjec.value.toStringArgument(),
                    mujRozvrh = _mujRozvrh.value,
                    stalost = stalost.name,
                    horScroll = params.horScrollState.value,
                    verScroll = params.verScrollState.value,
                )
            )
        }
    }

    fun zmenitMujRozvrh() {
        viewModelScope.launch {
            navigovat(
                Route.Rozvrh(
                    vjec = vjec.value.toStringArgument(),
                    mujRozvrh = !_mujRozvrh.value,
                    stalost = stalost.name,
                    horScroll = params.horScrollState.value,
                    verScroll = params.verScrollState.value,
                )
            )
        }
    }

    val zobrazitMujRozvrh = combineStates(
        viewModelScope,
        vjec, repo.nastaveni,
        SharingStarted.WhileSubscribed(5.seconds),
    ) { vjec, nastaveni ->
        vjec == nastaveni.mojeTrida
    }

    val zoom = repo.nastaveni.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::zoom)

    val currentlyDownloading = repo.currentlyDownloading

    val alwaysTwoRowCells = repo.nastaveni.mapState(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), Nastaveni::alwaysTwoRowCells)

    val result = combine(vjec, mujRozvrh, repo.nastaveni, zobrazitMujRozvrh) { vjec, mujRozvrh, nastaveni, zobrazitMujRozvrh ->
        when (vjec) {
            is Vjec.TridaVjec -> repo.ziskatRozvrh(
                trida = vjec,
                stalost = stalost,
            ).upravitTabulku {
                it.filtrovatTabulku(
                    mujRozvrh = mujRozvrh && zobrazitMujRozvrh,
                    mojeSkupiny = nastaveni.mojeSkupiny,
                )
            }

            is Vjec.VyucujiciVjec,
            is Vjec.MistnostVjec -> TvorbaRozvrhu.vytvoritRozvrhPodleJinych(
                vjec = vjec,
                stalost = stalost,
                repo = repo
            )

            is Vjec.DenVjec,
            is Vjec.HodinaVjec -> TvorbaRozvrhu.vytvoritSpecialniRozvrh(
                vjec = vjec,
                stalost = stalost,
                repo = repo
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val stahnoutVse: () -> Unit = {
        viewModelScope.launch {
            repo.stahnoutVse()
        }
    }

    fun najdiMivolnouTridu(
        stalost: Stalost,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        progress: (String) -> Unit,
        onComplete: (List<Vjec.MistnostVjec>?) -> Unit
    ) {
        viewModelScope.launch {
            val plneTridy = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                repo.ziskatRozvrh(trida, stalost).let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.rozvrh
                }.drop(1)[den].drop(1).slice(hodiny).flatMap { hodina ->
                    hodina.map { bunka ->
                        bunka.ucebna
                    }
                }
            }
            progress("Už to skoro je")

            val vysledek = mistnosti.value.drop(1).filter { it.zkratka !in plneTridy }.toMutableList()

            if (FiltrNajdiMi.JenOdemcene in filtry) vysledek.retainAll {
                it.zkratka in odemkleMistnosti.value
            }
            if (FiltrNajdiMi.JenCele in filtry) vysledek.retainAll {
                it.zkratka in velkeMistnosti.value
            }

            onComplete(vysledek)
        }
    }

    fun najdiMiVolnehoUcitele(
        stalost: Stalost,
        den: Int,
        hodiny: List<Int>,
        filtry: List<FiltrNajdiMi>,
        progress: (String) -> Unit,
        onComplete: (List<Vjec.VyucujiciVjec>?) -> Unit
    ) {
        viewModelScope.launch {
            val zaneprazdneniUcitele = tridy.value.drop(1).flatMap { trida ->
                progress("Prohledávám třídu\n${trida.zkratka}")
                repo.ziskatRozvrh(trida, stalost).let { result ->
                    if (result !is Uspech) {
                        onComplete(null)
                        return@launch
                    }
                    result.rozvrh
                }.drop(1)[den].drop(1).slice(hodiny).flatMap { hodina ->
                    hodina.map { bunka ->
                        bunka.ucitel
                    }
                }
            }
            progress("Už to skoro je")

            val vysledek = vyucujici.value.drop(1).filter { it.zkratka !in zaneprazdneniUcitele && it.zkratka in vyucujici2.value }.toMutableList()

            val ucitele = repo.ziskaUcitele(repo.nastaveni.first().mojeTrida)
            if (FiltrNajdiMi.JenSvi in filtry) vysledek.retainAll {
                it.zkratka in ucitele
            }

            onComplete(vysledek)
        }
    }
}