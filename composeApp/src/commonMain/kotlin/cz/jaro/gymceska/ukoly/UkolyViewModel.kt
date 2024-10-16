package cz.jaro.gymceska.ukoly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.combine as kombajn

@OptIn(ExperimentalUuidApi::class)
class UkolyViewModel(
    private val repo: Repository
) : ViewModel() {

    private val idNadpis1 = Uuid.random()
    private val idNadpis2 = Uuid.random()

    val jeOnline = repo.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), false)

    val inteligentni = repo.jeZarizeniPovoleno

    val ukoly = repo.ukoly.map { ukoly ->
        ukoly?.sortedBy(::dateFromUkol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state = kombajn(ukoly, repo.skrtleUkoly, repo.nastaveni) { ukoly, skrtle, nastaveni ->
        if (ukoly == null) UkolyState.Nacitani
        else UkolyState.Nacteno(
            ukoly = ukoly.map {
                val ukolJeMuj = it.skupina.isEmpty() || nastaveni.mojeSkupiny.isEmpty() || it.skupina in nastaveni.mojeSkupiny
                it.zjednusit(
                    stav = when {
                        !ukolJeMuj -> StavUkolu.Cizi
                        it.id in skrtle -> StavUkolu.Skrtly
                        else -> StavUkolu.Neskrtly
                    }
                )
            }
                .plus(JednoduchyUkol(id = idNadpis1, "", stav = StavUkolu.Nadpis1))
                .plus(JednoduchyUkol(id = idNadpis2, "", stav = StavUkolu.Nadpis2))
                .sortedBy {
                    when (it.stav) {
                        StavUkolu.Neskrtly -> 0
                        StavUkolu.Nadpis1 -> 1
                        StavUkolu.Skrtly -> 2
                        StavUkolu.Nadpis2 -> 3
                        StavUkolu.Cizi -> 4
                    }
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), UkolyState.Nacitani)

    fun skrtnout(id: Uuid) {
        viewModelScope.launch {
            repo.upravitSkrtleUkoly {
                it + id
            }
        }
    }

    fun odskrtnout(id: Uuid) {
        viewModelScope.launch {
            repo.upravitSkrtleUkoly {
                it - id
            }
        }
    }

    fun pridatUkol(callback: (Uuid) -> Unit) {
        viewModelScope.launch {
            val novyUkol = Ukol()
            repo.upravitUkoly(ukoly.value!! + novyUkol)
            callback(novyUkol.id)
        }
    }

    fun odebratUkol(id: Uuid) {
        viewModelScope.launch {
            repo.upravitUkoly(ukoly.value!!.filter { it.id != id })
        }
    }

    fun upravitUkol(ukol: Ukol) {
        viewModelScope.launch {
            val ukoly = ukoly.value!!.toMutableList()
            val index = ukoly.indexOfFirst { it.id == ukol.id }
            if (index == -1) return@launch
            ukoly[index] = ukol
            repo.upravitUkoly(ukoly)
        }
    }
}