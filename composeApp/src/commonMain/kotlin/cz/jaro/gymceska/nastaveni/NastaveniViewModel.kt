package cz.jaro.gymceska.nastaveni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.jaro.gymceska.Nastaveni
import cz.jaro.gymceska.OnlineTimetableSource
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.Timetables
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.getGroups
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.ukoly.today
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NastaveniViewModel(
    val settings: SettingsFlow,
    private val onlineTimetableSource: OnlineTimetableSource,
) : ViewModel() {

    val tridyFlow = onlineTimetableSource.classListSource.classes

    val skupiny = settings.map {
        onlineTimetableSource.getGroups(it.mojeTrida)
    }

    fun upravitNastaveni(edit: (Nastaveni) -> Nastaveni) {
        viewModelScope.launch {
            settings.edit(edit)
        }
    }

    fun stahnoutVse(stalost: TimetableType, update: (Float) -> Unit, finish: (Boolean) -> Unit) {
        viewModelScope.launch {
            val tridy = tridyFlow.value
            onlineTimetableSource.downloadAll(listOf(stalost)) { _, klass ->
                update(.5F * tridy.indexOf(klass) / tridy.size)
            }
            val vse = tridy.mapNotNull {
                update(.5F + .5F * tridy.indexOf(it) / tridy.size)
                val res = onlineTimetableSource.getTimetable(it, stalost).value
                if (res !is Uspech) {
                    finish(false)
                    return@mapNotNull null
                }
                it.zkratka to res.timetable
            }.toMap()

            update(.99F)
            val data = Json.encodeToString(
                Timetables(stalost, vse)
            )

            val dnes = today()

            FileKit.saveFile(
                extension = "rozvrh",
                baseName = "ROZVRH-${dnes.year}-${dnes.monthNumber}-${dnes.dayOfMonth}-$stalost",
                bytes = data.encodeToByteArray(),
            )
            finish(true)
        }
    }

    fun resetRemoteConfig() {
        viewModelScope.launch {
            onlineTimetableSource.classListSource.resetLists()
        }
    }
}
