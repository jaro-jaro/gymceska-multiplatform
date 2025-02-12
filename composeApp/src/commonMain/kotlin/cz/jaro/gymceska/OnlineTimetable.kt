package cz.jaro.gymceska

import com.fleeksoft.ksoup.nodes.Document
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getLongOrNullStateFlow
import com.russhwolf.settings.coroutines.getStringOrNullStateFlow
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
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
        onProgress: ((TimetableType, Timetable.Class) -> Unit)? = null,
    ) {
        if (!isOnline()) return
        classListSource.classes.value.forEach { klass ->
            types.forEach { type ->
                onProgress?.invoke(type, klass)
                downloadTimetable(klass, type)
            }
        }
    }

    private fun shouldDownloadTimetable(type: TimetableType, time: Long?): Boolean {
        val limit = if (type == TimetableType.Permanent) 14.days else 1.5.hours
        val lastTime = time?.let { Instant.fromEpochSeconds(it) } ?: return true
        val age = Clock.System.now() - lastTime
        return age >= limit
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private val toDownload = MutableStateFlow<Set<Pair<Timetable.Class, TimetableType>>>(emptySet())
    private val _currentlyDownloading = MutableStateFlow<Set<Pair<Timetable.Class, TimetableType>>>(emptySet())
    val currentlyDownloading = _currentlyDownloading.asStateFlow().mapState(scope) { it.isNotEmpty() }

    @OptIn(ExperimentalSettingsApi::class)
    fun getTimetable(
        klass: Timetable.Class,
        type: TimetableType,
    ) = combineStates(
        scope,
        settings.getLongOrNullStateFlow(scope, Keys.rozvrhPosledni(klass, type)),
        settings.getStringOrNullStateFlow(scope, Keys.rozvrh(klass, type)),
    ) { time, timetable ->
        if (isOnline() && shouldDownloadTimetable(type, time)) toDownload.value += klass to type

        timetable?.fromJson<TimetableData>()?.let(::Success)
            ?: if (isOnline()) Downloading() else Offline()
    }

    init {
        scope.launch {
            _currentlyDownloading.combine(toDownload) { currentlyDownloading, toDownload ->
                toDownload.map { (klass, type) ->
                    async { if (klass to type !in currentlyDownloading) downloadTimetable(klass, type) }
                }.awaitAll()
            }.collect()
        }
    }

    private suspend fun downloadTimetable(
        klass: Timetable.Class,
        type: TimetableType,
    ) {
        toDownload.value -= klass to type
        _currentlyDownloading.value += klass to type
        try {
            val doc = getTimetableDocument(klass.odkaz?.replace("###", type.code) ?: return)

            val rozvrh = TvorbaRozvrhu.createTimetableForClass(
                type = type,
                doc = doc,
                klass = klass.zkratka,
            )

            settings[Keys.rozvrh(klass, type)] = Json.encodeToString(rozvrh)
            settings[Keys.rozvrhPosledni(klass, type)] = Clock.System.now().epochSeconds / 60L * 60L
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            _currentlyDownloading.value -= klass to type
        }
    }

    val classListSource = FirebaseClassListSource(this, firebase)

    fun deleteDownloadedTimetables() {
        settings.keys.filter { it.startsWith("rozvrh2-_") }.forEach { key ->
            settings.remove(key)
        }
    }
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
    }.stateIn(scope, SharingStarted.Eagerly, listOf())
    override val rooms = configActive.map {
        remoteConfig.get<String>("mistnosti").fromJson<List<Timetable.Room>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf())
    override val teachers = configActive.map {
        remoteConfig.get<String>("vyucujici").fromJson<List<Timetable.Teacher>>()
    }.stateIn(scope, SharingStarted.Eagerly, listOf())
    val vyucujici2 = configActive.map {
        remoteConfig.get<String>("vyucujici2").fromJson<Set<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, setOf())
    val odemkleMistnosti = configActive.map {
        remoteConfig.get<String>("odemkleMistnosti").fromJson<Set<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, setOf())
    val velkeMistnosti = configActive.map {
        remoteConfig.get<String>("velkeMistnosti").fromJson<Set<String>>()
    }.stateIn(scope, SharingStarted.Eagerly, setOf())

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
        }

        inline fun <reified T> String.fromJson(serializer: DeserializationStrategy<T>? = null): T =
            if (serializer == null) json.decodeFromString(this) else json.decodeFromString(serializer, this)
        inline fun <reified T> T.toJson(serializer: SerializationStrategy<T>? = null): String =
            if (serializer == null) json.encodeToString(this) else json.encodeToString(serializer, this)
    }
}

fun OnlineTimetableSource.getGroups(klass: Timetable.Class): Sequence<String> {
    val result = getTimetable(klass, TimetableType.Permanent).value

    if (result !is Success) return emptySequence()

    return result.timetable
        .asSequence()
        .flatten()
        .flatten()
        .filterIsInstance<Cell.Normal>()
        .map { it.group }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
}

fun OnlineTimetableSource.getTeachers(trida: Timetable.Class): Set<String> {
    val result = getTimetable(trida, TimetableType.Permanent).value

    if (result !is Success) return emptySet()

    return result.timetable
        .asSequence()
        .flatten()
        .flatten()
        .map { it.teacherLike }
        .filter { it.isNotEmpty() }
        .toSet()
}