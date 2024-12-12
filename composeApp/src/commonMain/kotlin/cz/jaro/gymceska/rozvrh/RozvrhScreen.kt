package cz.jaro.gymceska.rozvrh

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import cz.jaro.better_dialog.globalDialogManager
import cz.jaro.better_dialog.show
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.TimetableData
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.ZadnaData
import cz.jaro.gymceska.viewModel
import kotlinx.datetime.LocalTime
import org.koin.core.Koin

@Composable
fun Rozvrh(
    args: Route.Rozvrh,
    navigator: Navigator,
    koin: Koin,
) {
    val horScrollState = rememberScrollState(args.x ?: 0)
    val verScrollState = rememberScrollState(args.y ?: 0)

    val viewModel = koin.viewModel<RozvrhViewModel>(RozvrhViewModel.Parameters(
        arg = args.vjec,
        horScrollState = horScrollState,
        verScrollState = verScrollState,
    ))

    LaunchedEffect(Unit) {
        viewModel.navigator = navigator
    }

    val tabulka by viewModel.result.collectAsStateWithLifecycle()
    val realVjec by viewModel.vjec.collectAsStateWithLifecycle()

    val tridy by viewModel.tridy.collectAsStateWithLifecycle()
    val mistnosti by viewModel.mistnosti.collectAsStateWithLifecycle()
    val vyucujici by viewModel.vyucujici.collectAsStateWithLifecycle()
    val hodiny by viewModel.hodiny.collectAsStateWithLifecycle()
    val realMujRozvrh by viewModel.mujRozvrh.collectAsStateWithLifecycle()
    val zobrazitMujRozvrh by viewModel.zobrazitMujRozvrh.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val alwaysTwoRowCells by viewModel.alwaysTwoRowCells.collectAsStateWithLifecycle()
    val currentlyDownloading by viewModel.currentlyDownloading.collectAsStateWithLifecycle()

    RozvrhContent(
        result = tabulka,
        vjec = realVjec,
        stalost = viewModel.stalost,
        vybratRozvrh = viewModel::vybratRozvrh,
        zmenitStalost = viewModel::zmenitStalost,
        stahnoutVse = viewModel.stahnoutVse,
        navigator = navigator,
        najdiMiVolnouTridu = viewModel::najdiMivolnouTridu,
        najdiMiVolnehoUcitele = viewModel::najdiMiVolnehoUcitele,
        tridy = tridy,
        mistnosti = mistnosti,
        vyucujici = vyucujici,
        hodiny = hodiny,
        mujRozvrh = realMujRozvrh,
        zmenitMujRozvrh = viewModel::zmenitMujRozvrh,
        zobrazitMujRozvrh = zobrazitMujRozvrh,
        horScrollState = horScrollState,
        verScrollState = verScrollState,
        zoom = zoom,
        alwaysTwoRowCells = alwaysTwoRowCells,
        currentlyDownloading = currentlyDownloading,
    )
}

@Composable
fun RozvrhContent(
    result: Result<out TimetableData>?,
    vjec: Timetable?,
    stalost: TimetableType,
    vybratRozvrh: (Timetable) -> Unit,
    zmenitStalost: (TimetableType) -> Unit,
    stahnoutVse: () -> Unit,
    navigator: Navigator,
    najdiMiVolnouTridu: (TimetableType, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Timetable.Room>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (TimetableType, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Timetable.Teacher>?) -> Unit) -> Unit,
    tridy: List<Timetable.Class>,
    mistnosti: List<Timetable.Room>,
    vyucujici: List<Timetable.Teacher>,
    hodiny: List<ClosedRange<LocalTime>>,
    mujRozvrh: Boolean?,
    zmenitMujRozvrh: () -> Unit,
    zobrazitMujRozvrh: Boolean,
    horScrollState: ScrollState,
    verScrollState: ScrollState,
    zoom: Float,
    alwaysTwoRowCells: Boolean,
    currentlyDownloading: Boolean,
) = RozvrhNavigation(
    stahnoutVse = stahnoutVse,
    navigator = navigator,
    najdiMiVolnouTridu = najdiMiVolnouTridu,
    najdiMiVolnehoUcitele = najdiMiVolnehoUcitele,
    result = result,
    vybratRozvrh = vybratRozvrh,
    currentlyDownloading = currentlyDownloading,
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val isInTabletMode = windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
                || windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

        if (isInTabletMode) Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Vybiratko(vjec, zobrazitMujRozvrh, zmenitMujRozvrh, mujRozvrh, vybratRozvrh, tridy, mistnosti, vyucujici, Modifier.weight(1F))
            PrepinatkoStalosti(stalost, zmenitStalost, Modifier.weight(1F))
        } else {
            Vybiratko(vjec, zobrazitMujRozvrh, zmenitMujRozvrh, mujRozvrh, vybratRozvrh, tridy, mistnosti, vyucujici)
            PrepinatkoStalosti(stalost, zmenitStalost, Modifier.fillMaxWidth())
        }

        if (result == null || vjec == null || mujRozvrh == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else when (result) {
            is Uspech -> CompositionLocalProvider(LocalCellZoom provides zoom) {
                Tabulka(
                    vjec = vjec,
                    tabulka = result.timetable,
                    kliklNaNeco = { vjec ->
                        vybratRozvrh(vjec)
                    },
                    tridy = tridy,
                    mistnosti = mistnosti,
                    vyucujici = vyucujici,
                    mujRozvrh = mujRozvrh,
                    hodiny = hodiny,
                    horScrollState = horScrollState,
                    verScrollState = verScrollState,
                    alwaysTwoRowCells = alwaysTwoRowCells,
                    stalost = stalost,
                )
            }

            is Error -> Text("Omlouváme se, ale došlo k chybě při stahování rozvrhu. Zkuste to znovu.")
            is ZadnaData -> Text("Jste offline a nemáte stažená žádná data z dřívějška.")
        }
    }
}

@Composable
private fun PrepinatkoStalosti(
    stalost: TimetableType,
    zmenitStalost: (TimetableType) -> Unit,
    modifier: Modifier = Modifier,
) = SingleChoiceSegmentedButtonRow(
    modifier = modifier
        .padding(horizontal = 8.dp)
        .padding(top = 4.dp)
        .height(IntrinsicSize.Max),
) {
    TimetableType.entries.forEachIndexed { i, it ->
        SegmentedButton(
            selected = stalost == it,
            onClick = { zmenitStalost(it) },
            shape = SegmentedButtonDefaults.itemShape(i, TimetableType.entries.count()),
            Modifier.fillMaxHeight(),
        ) {
            Text(it.nameNominative)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Vybiratko(
    vjec: Timetable?,
    zobrazitMujRozvrh: Boolean,
    zmenitMujRozvrh: () -> Unit,
    mujRozvrh: Boolean?,
    vybratRozvrh: (Timetable) -> Unit,
    tridy: List<Timetable.Class>,
    mistnosti: List<Timetable.Room>,
    vyucujici: List<Timetable.Teacher>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = vjec?.nazev ?: "",
            onValueChange = {},
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .padding(horizontal = 8.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            placeholder = {
                CircularProgressIndicator()
            },
            trailingIcon = {
                if (mujRozvrh != null) Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (zobrazitMujRozvrh) IconButton(
                        onClick = {
                            zmenitMujRozvrh()
                            expanded = false
                            focusManager.clearFocus()
                        }
                    ) { Icon(if (mujRozvrh) Icons.Default.PeopleAlt else Icons.Default.Person, null) }
                    else IconButton(
                        onClick = {
                            vybratRozvrh(Timetable.Class.HOME)
                            expanded = false
                            focusManager.clearFocus()
                        }
                    ) { Icon(Icons.Default.Home, null) }

                    Box(Modifier.minimumInteractiveComponentSize()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
                else {
                    Box(Modifier.minimumInteractiveComponentSize()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
            },
        )
        MyExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                focusManager.clearFocus()
            }
        ) {
            MenuVybiratka(vjec, tridy, mistnosti, vyucujici, vybratRozvrh) {
                expanded = false
                focusManager.clearFocus()
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun ExposedDropdownMenuBoxScope.MyExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    if (expandedState.currentState || expandedState.targetState) {
        Popup(
            onDismissRequest = {
                onDismissRequest()
            },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ) = anchorBounds.bottomLeft
            }
        ) {
            val transition = rememberTransition(expandedState, "DropDownMenu")

            val scale by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) {
                        tween(durationMillis = 120, easing = LinearOutSlowInEasing)
                    } else {
                        tween(durationMillis = 1, delayMillis = 74)
                    }
                }
            ) { expanded ->
                if (expanded) 1F else 0.8F
            }

            val alpha by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) {
                        tween(durationMillis = 30)
                    } else {
                        tween(durationMillis = 75)
                    }
                }
            ) { expanded ->
                if (expanded) 1F else 0F
            }

            Surface(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin.Center
                }.padding(start = 10.dp),
                shape = RoundedCornerShape(4.0.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Column(
                    Modifier
                        .exposedDropdownSize(false)
                        .padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max),
                    content = content,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MenuVybiratka(
    vjec: Timetable?,
    tridy: List<Timetable.Class>,
    mistnosti: List<Timetable.Room>,
    vyucujici: List<Timetable.Teacher>,
    vybratRozvrh: (Timetable) -> Unit,
    hide: () -> Unit,
) {
    val dny by lazy { Seznamy.dny }
    val hodiny by lazy { Seznamy.hodiny }
    val seznamy = listOf(if (vjec is Timetable.DenVjec) dny else if (vjec is Timetable.HodinaVjec) hodiny else tridy, mistnosti, vyucujici)
    val nadpisy = listOf(if (vjec is Timetable.DenVjec) "Dny:" else if (vjec is Timetable.HodinaVjec) "Hodiny:" else "Třídy:", "Mistnosti:", "Učitelé:")
    Row(
        Modifier.height(IntrinsicSize.Max)
    ) {
        nadpisy.forEachIndexed { j, nadpis ->
            if (j != 0) Spacer(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nadpis)
                        if (j == 1) NapovedaKMistostem(mistnosti)
                    }
                },
                onClick = {},
                Modifier.weight(listOf(5F, 7F, 12F)[j]),
                colors = MenuDefaults.itemColors(
                    disabledTextColor = MaterialTheme.colorScheme.primary
                ),
                enabled = false,
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        repeat(seznamy.maxOf { it.size - 1 }) { i ->
            val vjeci = seznamy.map { it.getOrNull(i + 1) }
            Row(
                Modifier.height(IntrinsicSize.Max)
            ) {
                vjeci.forEachIndexed { j, vjec ->
                    if (j != 0) Spacer(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                    DropdownMenuItem(
                        text = {
                            if (vjec != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vjec.nazev)
                            }
                        },
                        onClick = {
                            vybratRozvrh(vjec!!)
                            hide()
                        },
                        Modifier
                            .weight(listOf(5F, 7F, 12F)[j]),
                        enabled = vjec != null,
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun NapovedaKMistostem(mistnosti: List<Timetable.Room>) = if (mistnosti.any { it.napoveda != null }) IconButton(
    onClick = {
        globalDialogManager.show(
            confirmButton = { TextButton(::hide) { Text("OK") } },
            title = { Text("Nápověda k místnostem") },
            content = {
                LazyColumn {
                    items(mistnosti.filter { it.napoveda != null }.drop(1)) {
                        Text("${it.nazev} - to je${it.napoveda}")
                    }
                }
            },
        )
    }
) {
    Icon(Icons.AutoMirrored.Filled.Help, null)
} else Unit