package cz.jaro.gymceska

import com.fleeksoft.ksoup.nodes.Document
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import cz.jaro.gymceska.rozvrh.Cell
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.TvorbaRozvrhu
import cz.jaro.gymceska.rozvrh.nameNominative
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.remoteconfig.get
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class OnlineTimetableSource(
    private val settings: ObservableSettings,
    userOnlineManager: UserOnlineManager,
    firebase: FirebaseApp,
) : UserOnlineManager by userOnlineManager {

    object Keys {
        fun rozvrh(trida: Timetable.Class, stalost: TimetableType) = "rozvrh2-_${trida.nazev}_${stalost.nameNominative}"
        fun rozvrhPosledni(trida: Timetable.Class, stalost: TimetableType) = "rozvrh2-_${trida.nazev}_${stalost.nameNominative}_posledni"
    }

    suspend fun downloadAll(
        types: List<TimetableType> = TimetableType.entries,
    ) {
        if (!isOnline()) return
        classListSource.classes.value.forEach { trida ->
            _currentlyDownloading.value = trida
            types.forEach { stalost ->

                val doc = getTimetableDocument(trida.odkaz?.replace("###", stalost.code) ?: return)

                val rozvrh = TvorbaRozvrhu.createTimetableForClass(
                    type = stalost,
                    doc = doc,
                    klass = trida.zkratka,
                )

                settings[Keys.rozvrh(trida, stalost)] = Json.encodeToString(rozvrh)
                settings[Keys.rozvrhPosledni(trida, stalost)] = Clock.System.now().epochSeconds / 60L * 60L
            }
        }
        _currentlyDownloading.value = null
    }

    private fun pouzitOfflineRozvrh(trida: Timetable.Class, stalost: TimetableType): Boolean {
        val limit = if (stalost == TimetableType.Permanent) 14.days else 1.hours
        val posledni = settings.getLongOrNull(Keys.rozvrhPosledni(trida, stalost))?.let { Instant.fromEpochSeconds(it) } ?: return false
        val starost = Clock.System.now() - posledni
        return starost < limit
    }

    private val _currentlyDownloading = MutableStateFlow<Timetable.Class?>(null)
    val currentlyDownloading = _currentlyDownloading.asStateFlow()

    suspend fun getTimetable(
        klass: Timetable.Class,
        type: TimetableType,
    ): Result {
        if (isOnline() && !pouzitOfflineRozvrh(klass, type)) try {
            _currentlyDownloading.value = klass
            val doc = getTimetableDocument(klass.odkaz?.replace("###", type.code) ?: return TridaNeexistuje)

            val rozvrh = TvorbaRozvrhu.createTimetableForClass(
                type = type,
                doc = doc,
                klass = klass.zkratka,
            )

            settings[Keys.rozvrh(klass, type)] = Json.encodeToString(rozvrh)
            settings[Keys.rozvrhPosledni(klass, type)] = Clock.System.now().epochSeconds / 60L * 60L

            _currentlyDownloading.value = null

            return Uspech(rozvrh, Online)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val kdy = settings.getLongOrNull(Keys.rozvrhPosledni(klass, type))?.let { Instant.fromEpochSeconds(it) }
            ?: run {
                return ZadnaData
            }

        val rozvrh = settings.getStringOrNull(Keys.rozvrh(klass, type))?.fromJson<Week>()
            ?: run {
                return ZadnaData
            }

        return Uspech(rozvrh, Offline(kdy.toLocalDateTime(TimeZone.currentSystemDefault())))
    }

    val classListSource = FirebaseClassListSource(this, firebase)
}

expect suspend fun getTimetableDocument(link: String): Document
class FirebaseClassListSource(
    userOnlineManager: UserOnlineManager,
    firebase: FirebaseApp,
) : UserOnlineManager by userOnlineManager, ClassListSource {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val remoteConfig = Firebase.remoteConfig(firebase)

    suspend fun resetLists() {
        remoteConfig.reset()
        remoteConfig.settings {
            minimumFetchInterval = 1.hours
        }
        remoteConfig.fetchAndActivate()
    }

    private val configActive = flow {
        remoteConfig.settings {
            minimumFetchInterval = 1.hours
        }
        if (!isOnline())
            emit(remoteConfig.activate())
        else
            emit(remoteConfig.fetchAndActivate())
    }

    override val classes = configActive.map {
        remoteConfig.get<String>("tridy").fromJson<List<Timetable.Class>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf(Timetable.Class("Třídy")))
    override val rooms = configActive.map {
        remoteConfig.get<String>("mistnosti").fromJson<List<Timetable.Room>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf(Timetable.Room("Místnosti")))
    override val teachers = configActive.map {
        remoteConfig.get<String>("vyucujici").fromJson<List<Timetable.Teacher>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf(Timetable.Teacher("Vyučující", "")))
    val vyucujici2 = configActive.map {
        remoteConfig.get<String>("vyucujici2").fromJson<List<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf())
    val odemkleMistnosti = configActive.map {
        remoteConfig.get<String>("odemkleMistnosti").fromJson<List<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf())
    val velkeMistnosti = configActive.map {
        remoteConfig.get<String>("velkeMistnosti").fromJson<List<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf())

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
        }

        inline fun <reified T> String.fromJson(): T = json.decodeFromString(this)
    }
}

suspend fun OnlineTimetableSource.getTimetable(
    type: TimetableType,
    settingsFlow: SettingsFlow,
): Result = getTimetable(settingsFlow.value.mojeTrida, type)

suspend fun OnlineTimetableSource.getGroups(klass: Timetable.Class): Sequence<String> {
    val result = getTimetable(klass, TimetableType.Permanent)

    if (result !is Uspech) return emptySequence()

    return result.rozvrh
        .asSequence()
        .flatten()
        .flatten()
        .filterIsInstance<Cell.Normal>()
        .map { it.group }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
}

suspend fun OnlineTimetableSource.getTeachers(trida: Timetable.Class): Sequence<String> {
    val result = getTimetable(trida, TimetableType.Permanent)

    if (result !is Uspech) return emptySequence()

    return result.rozvrh
        .asSequence()
        .flatten()
        .flatten()
        .map { it.teacherLike }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
}