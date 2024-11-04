package cz.jaro.gymceska

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import com.russhwolf.settings.set
import cz.jaro.gymceska.ukoly.Ukol
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalSettingsApi::class)
class UkolyRepository(
    private val settings: ObservableSettings,
    userOnlineManager: UserOnlineManager,
    firebase: FirebaseApp,
) : UserOnlineManager by userOnlineManager {

    private val scope = CoroutineScope(Dispatchers.Default)

    object Keys {
        const val SKRTLE_UKOLY = "skrtle_ukoly"
        const val UKOLY = "ukoly"
    }

    private val database = Firebase.database(firebase, "https://gymceska-b9b4c-default-rtdb.europe-west1.firebasedatabase.app/")

    private val ukolyRef = database.reference("ukoly")

    private val onlineUkoly = MutableStateFlow(null as List<Ukol>?)

    init {
        scope.launch {
            ukolyRef.valueEvents.collect { snapshot ->

                val ukoly = snapshot.value<List<Map<String, String>>?>()
                val noveUkoly = ukoly?.mapNotNull {
                    Ukol(
                        datum = it["datum"] ?: return@mapNotNull null,
                        nazev = it["nazev"] ?: return@mapNotNull null,
                        predmet = it["predmet2"] ?: it["predmet"] ?: return@mapNotNull null,
                        skupina = it["skupina"] ?: "",
                        id = it["id"]?.let { id -> Uuid.parse(id) } ?: Uuid.random(),
                    )
                }
                onlineUkoly.value = noveUkoly

                scope.launch {
                    settings[Keys.UKOLY] = Json.encodeToString(noveUkoly)

                    upravitSkrtleUkoly { skrtle ->
                        skrtle.filter { uuid ->
                            uuid in (noveUkoly?.map { it.id } ?: emptyList())
                        }.toSet()
                    }
                }
            }
        }
    }

    private val offlineUkoly = settings.getStringOrNullFlow(Keys.UKOLY).map {
        it?.fromJson<List<Ukol>>()
    }

    @OptIn(ExperimentalUuidApi::class)
    private val fakeUkol = Uuid.parse("00000000-0000-0000-0000-000000000000")

    @OptIn(ExperimentalUuidApi::class)
    val ukoly = combine(isOnline, onlineUkoly, offlineUkoly) { isOnline, onlineUkoly, offlineUkoly ->
        if (isOnline) onlineUkoly else offlineUkoly
    }.map { ukoly ->
        ukoly?.filter {
            it.id != fakeUkol
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
        }

        inline fun <reified T> String.fromJson(): T = json.decodeFromString(this)
    }

    val skrtleUkoly = settings.getStringOrNullFlow(Keys.SKRTLE_UKOLY).map {
        it?.fromJson<Set<String>>()?.map { id -> Uuid.parse(id) }?.toSet() ?: emptySet()
    }

    fun upravitSkrtleUkoly(edit: (Set<Uuid>) -> Set<Uuid>) {
        settings[Keys.SKRTLE_UKOLY] = edit(
            settings.getStringOrNull(Keys.SKRTLE_UKOLY)?.fromJson<List<String>>()?.map { id -> Uuid.parse(id) }?.toSet() ?: emptySet()
        ).map { id -> id.toString() }.toSet().let { Json.encodeToString(it) }
    }

    suspend fun upravitUkoly(ukoly: List<Ukol>) {
        ukolyRef.setValue(ukoly.map {
            mapOf(
                "datum" to it.datum,
                "nazev" to it.nazev,
                "skupina" to it.skupina,
                "predmet2" to it.predmet,
                "predmet" to listOf(it.predmet, it.skupina).filter(String::isNotEmpty).joinToString(" "),
                "id" to it.id.toString()
            )
        })
    }
}