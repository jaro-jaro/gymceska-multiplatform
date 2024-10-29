package cz.jaro.gymceska

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.contains
import com.russhwolf.settings.coroutines.getStringOrNullStateFlow
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import cz.jaro.gymceska.rozvrh.Timetable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun SettingsFlow(classListSource: ClassListSource, settings: ObservableSettings) = SettingsFlow(settings, classListSource.classes)

@OptIn(ExperimentalSettingsApi::class)
class SettingsFlow private constructor(
    private val settings: ObservableSettings,
    classes: StateFlow<List<Timetable.Class>>,
    scope: CoroutineScope,
    defaultSettings: (List<Timetable.Class>) -> Nastaveni,
) : StateFlow<Nastaveni> by (settings
    .getStringOrNullStateFlow(scope, Keys.SETTINGS)
    .combineStates(scope, classes) { it, t ->
        it?.fromJson<Nastaveni>() ?: defaultSettings(t)
    }) {

    constructor(
        settings: ObservableSettings,
        tridy: StateFlow<List<Timetable.Class>>,
    ) : this(
        settings = settings,
        classes = tridy,
        scope = CoroutineScope(Dispatchers.Default),
        { t -> Nastaveni(mojeTrida = t.getOrElse(1) { t.first() }) }
    )

    fun edit(edit: (Nastaveni) -> Nastaveni) {
        settings[Keys.SETTINGS] = Json.encodeToString(edit(value))
    }

    private object Keys {
        const val SETTINGS = "nastaveni"
        const val FIRST = "first"
    }

    init {
        scope.launch {
            if (Keys.FIRST !in settings) {
                settings[Keys.FIRST] = false
            }
        }
    }
}