package cz.jaro.gymceska.rozvrh.manual

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullStateFlow
import com.russhwolf.settings.set
import cz.jaro.gymceska.ClassListSource
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import cz.jaro.gymceska.Offline
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Timetables
import cz.jaro.gymceska.TridaNeexistuje
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.ZadnaData
import cz.jaro.gymceska.filterNotNullState
import cz.jaro.gymceska.justTimetable
import cz.jaro.gymceska.mapState
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.ukoly.now
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalSettingsApi::class)
class LocalTimetableSource(
    private val settings: ObservableSettings,
    localFileManager: LocalFileManager,
) : LocalFileManager by localFileManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    object Keys {
        const val SAVED = "saved_timetable"
    }

    private val saved = settings.getStringOrNullStateFlow(scope, Keys.SAVED)
    @PublishedApi
    internal val timetables = try {
        saved.mapState(scope, SharingStarted.Eagerly) {
            saved.value?.let(::loadSavedTimetable)?.fromJson<Timetables<TimetableData>>()
        }
    } catch (e: IllegalArgumentException) {
        try {
            cleanup(saved.value ?: throw e)
        } finally {
            settings[Keys.SAVED] = null
        }
        throw e
    }

    inline fun copyTimetables(callback: (Timetables<TimetableData>?) -> Unit) = callback(timetables.value)

    suspend fun loadFile() {
        settings[Keys.SAVED] = FileKit.pickFile(
            type = PickerType.File(listOf("rozvrh")),
            mode = PickerMode.Single,
            title = "Vyberte soubor s rozvrhem",
        )?.getSaveData()
    }

    fun removedSavedTimetable() {
        cleanup(saved.value ?: return)
        settings[Keys.SAVED] = null
    }

    fun getTimetable(klass: Timetable.Class) =
        timetables.value?.timetables?.let { timetables ->
            timetables[klass.zkratka]?.let {
                Uspech(it, Offline(now()))
            } ?: TridaNeexistuje()
        } ?: ZadnaData()

    val type = timetables.mapState(scope, SharingStarted.Eagerly) { it?.type }

    val classListSource = LocalClassListSource(timetables.filterNotNullState(
        scope, Timetables(
            type = TimetableType.ThisWeek,
            timetables = emptyMap(),
        )
    ))
}

interface LocalFileManager {
    suspend fun String.getSaveData(): String
    fun loadSavedTimetable(savedData: String): String
    fun cleanup(savedData: String)

    companion object Default : LocalFileManager {
        override suspend fun String.getSaveData() = this
        override fun loadSavedTimetable(savedData: String) = savedData
        override fun cleanup(savedData: String) = Unit
    }
}

context(LocalFileManager)
suspend fun PlatformFile.getSaveData(): String = readBytes().decodeToString().getSaveData()

class LocalClassListSource(
    timetables: StateFlow<Timetables<TimetableData>>,
) : ClassListSource {
    private fun Timetables<TimetableData>.cells() = timetables.flatMap { (_, week) ->
        week.justTimetable().flatMap { day ->
            day.flatten()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override val classes = timetables.mapState(scope, SharingStarted.Eagerly) { timetables ->
        timetables.cells().filterIsInstance<Cell.Data>().map { Timetable.Class(it.klass) }.distinct().sortedBy { it.zkratka }
    }
    override val rooms = timetables.mapState(scope, SharingStarted.Eagerly) { timetables ->
        timetables.cells().filterIsInstance<Cell.Normal>().map { Timetable.Room(it.room) }.distinct().sortedBy { it.zkratka }
    }
    override val teachers = timetables.mapState(scope, SharingStarted.Eagerly) { timetables ->
        timetables.cells().filterIsInstance<Cell.Normal>().map { Timetable.Teacher(it.teacherName, it.teacher) }.distinct()
            .sortedBy { it.zkratka }
    }

    val vyucujici2 = timetables.mapState(scope, SharingStarted.Eagerly) { timetables ->
        timetables.cells().filterIsInstance<Cell.Normal>()
            .groupBy({ it.teacher }, { it.subject })
            .filterValues { "ST" !in it }
            .keys
    }
}