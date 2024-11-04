package cz.jaro.gymceska.rozvrh.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import cz.jaro.gymceska.ActionScope
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.rozvrh.Seznamy
import cz.jaro.gymceska.rozvrh.Timetable
import cz.jaro.gymceska.rozvrh.Vybiratko
import cz.jaro.gymceska.rozvrh.tabulka
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.Clock.System
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun RozvrhManualNavigation(
    navigator: Navigator,
    najdiMiVolnouTridu: (Int, List<Int>, (String) -> Unit, (List<Timetable.Room>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (Int, List<Int>, (String) -> Unit, (List<Timetable.Teacher>?) -> Unit) -> Unit,
    result: Result?,
    vybratRozvrh: (Timetable) -> Unit,
    remove: () -> Unit,
    loaded: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    title = "Manuál",
    actions = {
        Actions(result, vybratRozvrh, najdiMiVolnouTridu, najdiMiVolnehoUcitele, remove, loaded)
    },
    currentDestination = Route.RozvrhManual(""),
    navigator = navigator,
    content = content,
    minorNavigationItems = {
        MinorNavigationItem(
            destination = Route.RozvrhManual(""),
            title = "Manuální",
            icon = Icons.Default.TableChart,
        )
        MinorNavigationItem(
            destination = Route.Nastaveni,
            title = "Nastavení",
            icon = Icons.Default.Settings,
        )
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionScope.Actions(
    result: Result?,
    vybratRozvrh: (Timetable) -> Unit,
    najdiMiVolnouTridu: (Int, List<Int>, (String) -> Unit, (List<Timetable.Room>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (Int, List<Int>, (String) -> Unit, (List<Timetable.Teacher>?) -> Unit) -> Unit,
    remove: () -> Unit,
    loaded: Boolean,
) {
    var nacitame by remember { mutableStateOf(false) }
    var podrobnostiNacitani by remember { mutableStateOf("Načítání...") }

    if (nacitame) AlertDialog(
        onDismissRequest = {
            nacitame = false
        },
        confirmButton = {},
        title = {
            Text(text = podrobnostiNacitani)
        },
        text = {
            CircularProgressIndicator()
        },
    )

    var najdiMiNastaveniDialog by remember { mutableStateOf(false) }
    var najdiMiDialog by remember { mutableStateOf(false) }
    var volneTridy by remember { mutableStateOf(emptyList<Timetable.Room>()) }
    var volniUcitele by remember { mutableStateOf(emptyList<Timetable.Teacher>()) }
    var ucebna by remember { mutableStateOf(true) }
    var denIndex by remember {
        mutableIntStateOf(
            today().dayOfWeek.isoDayNumber
                .let { if (time() > LocalTime(15, 45)) it + 1 else it }
                .let { if (it > 5) 1 else it } - 1
        )
    }
    var hodinaIndexy by remember(result) {
        mutableStateOf(
            listOf(
                result
                    ?.tabulka
                    ?.get(0)
                    ?.drop(1)
                    ?.indexOfFirst {
                        try {
                            val cas = it.first().teacherLike.split(" - ").first()
                            val hm = cas.split(":")
                            (System.now() - 10.minutes).toLocalDateTime(TimeZone.currentSystemDefault()).time < LocalTime(hm[0].toInt(), hm[1].toInt())
                        } catch (e: Exception) {
                            false
                        }
                    }
                    ?.coerceAtLeast(0)
                    ?: 0
            )
        )
    }

    if (najdiMiDialog) AlertDialog(
        onDismissRequest = {
            najdiMiDialog = false
        },
        confirmButton = {
            TextButton(
                onClick = {
                    najdiMiDialog = false
                }
            ) {
                Text(text = "OK")
            }
        },
        dismissButton = {},
        title = {
            Text(text = "Najdi mi ${if (ucebna) "volnou učebnu" else "volného učitele"}")
        },
        text = {
            LazyColumn {
                if (ucebna) item {
                    Text("Na škole jsou ${Seznamy.dny4Pad[denIndex]} ${hodinaIndexy.joinToString(" a ") { "$it." }} hodinu volné tyto učebny:")
                }
                if (ucebna) items(volneTridy.toList()) {
                    Text("${it.nazev}, to je${it.napoveda}", Modifier.clickable {
                        najdiMiDialog = false
                        vybratRozvrh(it)
                    })
                }
                if (!ucebna) item {
                    Text("Na škole jsou ${Seznamy.dny4Pad[denIndex]} ${hodinaIndexy.joinToString(" a ") { "$it." }} hodinu volní tito učitelé:")
                }
                if (!ucebna) items(volniUcitele.toList()) {
                    Text(it.nazev, Modifier.clickable {
                        najdiMiDialog = false
                        vybratRozvrh(it)
                    })
                }
            }
        }
    )

    if (najdiMiNastaveniDialog) AlertDialog(
        onDismissRequest = {
            najdiMiNastaveniDialog = false
        },
        confirmButton = {
            TextButton(
                onClick = {
                    nacitame = true
                    najdiMiNastaveniDialog = false
                    podrobnostiNacitani = "Hledám..."

                    if (ucebna) najdiMiVolnouTridu(
                        denIndex, hodinaIndexy,
                        {
                            podrobnostiNacitani = it
                        },
                        {
                            if (it == null) {
                                podrobnostiNacitani = "Nejste připojeni k internetu a nemáte staženou offline verzi všech rozvrhů tříd"
                                return@najdiMiVolnouTridu
                            }
                            volneTridy = it
                            najdiMiDialog = true
                            nacitame = false
                        }
                    )
                    else najdiMiVolnehoUcitele(
                        denIndex, hodinaIndexy,
                        {
                            podrobnostiNacitani = it
                        },
                        {
                            if (it == null) {
                                podrobnostiNacitani = "Nejste připojeni k internetu a nemáte staženou offline verzi všech rozvrhů tříd"
                                return@najdiMiVolnehoUcitele
                            }
                            volniUcitele = it
                            najdiMiDialog = true
                            nacitame = false
                        }
                    )
                }
            ) {
                Text(text = "Vyhledat")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    najdiMiNastaveniDialog = false
                }
            ) {
                Text(text = "Zrušit")
            }
        },
        title = {
            Text(text = "Najdi mi")
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                Vybiratko(
                    seznam = listOf("volnou učebnu", "volného učitele"),
                    index = if (ucebna) 0 else 1,
                    onClick = { i, _ ->
                        ucebna = i == 0
                    },
                    label = "Najdi mi",
                    zaskrtavatko = { false },
                )
                Vybiratko(
                    seznam = Seznamy.dny4Pad,
                    index = denIndex,
                    onClick = { i, _ ->
                        denIndex = i
                    },
                    zaskrtavatko = { false },
                )
                Vybiratko(
                    value = "${hodinaIndexy.joinToString(" a ") { "$it." }} hodinu",
                    seznam = Seznamy.hodiny4Pad,
                    onClick = { i, _ ->
                        if (i in hodinaIndexy) hodinaIndexy -= i
                        else if (i !in hodinaIndexy) hodinaIndexy += i
                    },
                    zaskrtavatko = {
                        Seznamy.hodiny4Pad.indexOf(it) in hodinaIndexy
                    },
                    zavirat = false
                )
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        )
    )
    Action(
        onClick = {
            najdiMiNastaveniDialog = true
        },
        icon = Icons.Default.Search,
        title = "Najdi mi",
    )
    if (loaded) Action(
        onClick = remove,
        icon = Icons.Default.Delete,
        title = "Zrušit rozvrhy",
    )
}

private operator fun DayOfWeek.plus(i: Int): DayOfWeek {
    require(i in -6..6) { "i must be in -6..6, got $i" }
    return DayOfWeek(isoDayNumber = (isoDayNumber + i + 7 - 1) % 7 + 1)
}
