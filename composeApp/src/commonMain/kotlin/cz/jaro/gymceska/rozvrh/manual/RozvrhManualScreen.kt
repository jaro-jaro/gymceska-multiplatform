package cz.jaro.gymceska.rozvrh.manual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jaro.gymceska.Error
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.TridaNeexistuje
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.ZadnaData
import cz.jaro.gymceska.rozvrh.LocalCellZoom
import cz.jaro.gymceska.rozvrh.Tabulka
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.TimetableType
import cz.jaro.gymceska.rozvrh.Vybiratko
import cz.jaro.gymceska.viewModel
import kotlinx.datetime.LocalTime
import org.koin.core.Koin

@Composable
fun RozvrhManual(
    args: Route.RozvrhManual,
    navigator: Navigator,
    koin: Koin,
) {
    val viewModel = koin.viewModel<RozvrhManualViewModel>(
        RozvrhManualViewModel.Parameters(
            arg = args.vjec,
        )
    )

    LaunchedEffect(Unit) {
        viewModel.navigator = navigator
    }

    val tabulka by viewModel.result.collectAsStateWithLifecycle()
    val realVjec by viewModel.vjec.collectAsStateWithLifecycle()

    val tridy by viewModel.tridy.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val mistnosti by viewModel.mistnosti.collectAsStateWithLifecycle()
    val vyucujici by viewModel.vyucujici.collectAsStateWithLifecycle()
    val hodiny by viewModel.hodiny.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val alwaysTwoRowCells by viewModel.alwaysTwoRowCells.collectAsStateWithLifecycle()

    RozvrhManualContent(
        result = tabulka,
        vjec = realVjec,
        vybratRozvrh = viewModel::vybratRozvrh,
        navigator = navigator,
        najdiMiVolnouTridu = viewModel::najdiMivolnouTridu,
        najdiMiVolnehoUcitele = viewModel::najdiMiVolnehoUcitele,
        tridy = tridy,
        mistnosti = mistnosti,
        vyucujici = vyucujici,
        hodiny = hodiny,
        zoom = zoom,
        alwaysTwoRowCells = alwaysTwoRowCells,
        remove = viewModel::removeTimetable,
        load = viewModel::loadFile,
        loaded = loaded,
    )
}

@Composable
fun RozvrhManualContent(
    result: Result?,
    vjec: Timetable?,
    vybratRozvrh: (Timetable) -> Unit,
    navigator: Navigator,
    najdiMiVolnouTridu: (Int, List<Int>, (String) -> Unit, (List<Timetable.Room>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (Int, List<Int>, (String) -> Unit, (List<Timetable.Teacher>?) -> Unit) -> Unit,
    tridy: List<Timetable.Class>,
    mistnosti: List<Timetable.Room>,
    vyucujici: List<Timetable.Teacher>,
    hodiny: List<ClosedRange<LocalTime>>,
    zoom: Float,
    alwaysTwoRowCells: Boolean,
    remove: () -> Unit,
    load: () -> Unit,
    loaded: Boolean,
) = RozvrhManualNavigation(
    navigator = navigator,
    najdiMiVolnouTridu = najdiMiVolnouTridu,
    najdiMiVolnehoUcitele = najdiMiVolnehoUcitele,
    result = result,
    vybratRozvrh = vybratRozvrh,
    remove = remove,
    loaded = loaded,
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (loaded) Vybiratko(vjec, false, {}, null, vybratRozvrh, tridy, mistnosti, vyucujici)
        else {
            TextButton(
                onClick = load,
                Modifier.padding(all = 8.dp),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
            ) {
                Icon(Icons.Default.Upload, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Nahrát rozvrh")
            }
        }
        if (loaded && result != null && vjec != null) when (result) {
            is Uspech -> CompositionLocalProvider(LocalCellZoom provides zoom) {
                Tabulka(
                    vjec = vjec,
                    tabulka = result.rozvrh,
                    kliklNaNeco = { vjec ->
                        vybratRozvrh(vjec)
                    },
                    rozvrhOfflineWarning = result.zdroj,
                    tridy = tridy,
                    mistnosti = mistnosti,
                    vyucujici = vyucujici,
                    mujRozvrh = false,
                    hodiny = hodiny,
                    horScrollState = rememberScrollState(),
                    verScrollState = rememberScrollState(),
                    alwaysTwoRowCells = alwaysTwoRowCells,
                    stalost = TimetableType.Permanent,
                )
            }

            Error -> Text("Omlouváme se, ale došlo k chybě při načítání rozvrhu. Zkuste to znovu.")
            TridaNeexistuje -> Text("Omlouváme se, rozvrhy jsou poškozeny, prosím, odstraňte je a opakujte akci")
            ZadnaData -> Text("Nemáte nahrané žádné rozvrhy")
        }
    }
}
