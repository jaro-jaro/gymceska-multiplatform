package cz.jaro.gymceska.rozvrh.editor

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullStateFlow
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import cz.jaro.gymceska.FirebaseClassListSource.Companion.toJson
import cz.jaro.gymceska.Timetables
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.manual.LocalFileManager
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted

@OptIn(ExperimentalSettingsApi::class)
class EditedTimetableSource(
    private val settings: ObservableSettings,
    localFileManager: LocalFileManager,
) : LocalFileManager by localFileManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    object Keys {
        const val EDITED = "edited_timetable"
    }

    private val edited = settings.getStringOrNullStateFlow(scope, Keys.EDITED)
    val timetables = try {
        edited.mapState(scope, SharingStarted.Eagerly) {
            edited.value?.let(::loadSavedTimetable)?.fromJson<Timetables<AdvancedWeek>>(Timetables.serializer(AdvancedWeek.Serializer()))
        }
    } catch (e: Exception) {
        try {
            cleanup(edited.value ?: throw e)
        } finally {
            settings[Keys.EDITED] = null
        }
        throw e
    }

    suspend fun loadFile() {
        settings[Keys.EDITED] = FileKit.pickFile(
            type = PickerType.File(listOf("rozvrh")),
            mode = PickerMode.Single,
            title = "Vyberte soubor s upraveným rozvrhem",
        )?.readBytes()?.decodeToString()?.getSaveData()
    }

    suspend fun loadData(data: Timetables<AdvancedWeek>?) {
        settings[Keys.EDITED] = data?.toJson(Timetables.serializer(AdvancedWeek.Serializer()))?.getSaveData()
        println(settings[Keys.EDITED])
    }

    fun removedEditedTimetable() {
        cleanup(edited.value ?: return)
        settings[Keys.EDITED] = null
    }

    suspend fun editTimetable(klass: Timetable.Class, modify: AdvancedWeek.() -> AdvancedWeek) {
        settings[Keys.EDITED] = timetables.value?.let { data ->
            data.copy(
                timetables = data.timetables.toMutableMap().also {
                    it[klass.zkratka] = it[klass.zkratka]!!.modify()
                }.toMap()
            ).toJson(Timetables.serializer(AdvancedWeek.Serializer())).getSaveData()
        }
    }

    fun getTimetable(klass: Timetable.Class) =
        timetables.mapState(scope) {
            it?.timetables?.let { timetables ->
                timetables[klass.zkratka]
            }
        }
}