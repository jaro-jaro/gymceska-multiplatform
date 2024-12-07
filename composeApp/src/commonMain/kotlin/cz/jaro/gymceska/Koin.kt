package cz.jaro.gymceska

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import com.russhwolf.settings.ObservableSettings
import cz.jaro.gymceska.nastaveni.NastaveniViewModel
import cz.jaro.gymceska.rozvrh.RozvrhViewModel
import cz.jaro.gymceska.rozvrh.editor.EditedTimetableSource
import cz.jaro.gymceska.rozvrh.editor.RozvrhEditorViewModel
import cz.jaro.gymceska.rozvrh.manual.LocalTimetableSource
import cz.jaro.gymceska.rozvrh.manual.RozvrhManualViewModel
import cz.jaro.gymceska.ukoly.UkolyViewModel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.KoinApplicationDslMarker
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

@KoinApplicationDslMarker
fun initKoin(platformSpecificModule: Module): KoinApplication {
    return startKoin {
        modules(platformSpecificModule, commonModule)
    }
}

val commonModule = module {
    single { OnlineTimetableSource(get(), get(), get()) }
    single { UkolyRepository(get(), get(), get()) }
    single { LocalTimetableSource(get(), get()) }
    single { EditedTimetableSource(get(), get()) }
    single { get<OnlineTimetableSource>().classListSource } bind ClassListSource::class
    single { SettingsFlow(get(), get<ObservableSettings>()) }

    factory { RozvrhViewModel(it.get(), get(), get()) }
    factory { UkolyViewModel(get(), get(), get(), get()) }
    factory { NastaveniViewModel(get(), get()) }
    factory { RozvrhManualViewModel(it.get(), get(), get()) }
    factory { RozvrhEditorViewModel(it.get(), get(), get(), get()) }
}

@Composable
inline fun <reified VM : ViewModel> Koin.viewModel(params: Any? = null): VM =
    androidx.lifecycle.viewmodel.compose.viewModel<VM>(initializer = { get<VM> { parametersOf(params) } })