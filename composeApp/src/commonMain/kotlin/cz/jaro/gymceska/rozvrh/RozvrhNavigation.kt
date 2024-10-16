package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cz.jaro.gymceska.ActionScope
import cz.jaro.gymceska.Navigation
import cz.jaro.gymceska.Result
import cz.jaro.gymceska.Route
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.datetime.Clock.System
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun RozvrhNavigation(
    stahnoutVse: () -> Unit,
    navigate: (Route) -> Unit,
    najdiMiVolnouTridu: (Stalost, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Vjec.MistnostVjec>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (Stalost, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Vjec.VyucujiciVjec>?) -> Unit) -> Unit,
    result: Result?,
    vybratRozvrh: (Vjec) -> Unit,
    currentlyDownloading: Vjec.TridaVjec?,
    content: @Composable (PaddingValues) -> Unit,
) = Navigation(
    titleContent = {
        if (currentlyDownloading != null) Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CloudDownload, null, Modifier.padding(start = 16.dp, end = 8.dp))
            Text(text = currentlyDownloading.nazev, style = MaterialTheme.typography.bodyMedium)
        }
    },
    title = "Rozvrh",
    actions = {
        Actions(stahnoutVse, result, vybratRozvrh, najdiMiVolnouTridu, najdiMiVolnehoUcitele)
    },
    currentDestination = Route.Rozvrh(),
    navigateToDestination = navigate,
    content = content,
    minorNavigationItems = {
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
    stahnoutVse: () -> Unit,
    result: Result?,
    vybratRozvrh: (Vjec) -> Unit,
    najdiMiVolnouTridu: (Stalost, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Vjec.MistnostVjec>?) -> Unit) -> Unit,
    najdiMiVolnehoUcitele: (Stalost, Int, List<Int>, List<FiltrNajdiMi>, (String) -> Unit, (List<Vjec.VyucujiciVjec>?) -> Unit) -> Unit,
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

    Action(
        onClick = stahnoutVse,
        icon = Icons.Default.CloudDownload,
        title = "Stáhnout vše",
    )

    var najdiMiNastaveniDialog by remember { mutableStateOf(false) }
    var najdiMiDialog by remember { mutableStateOf(false) }
    var volneTridy by remember { mutableStateOf(emptyList<Vjec.MistnostVjec>()) }
    var volniUcitele by remember { mutableStateOf(emptyList<Vjec.VyucujiciVjec>()) }
    var ucebna by remember { mutableStateOf(true) }
    var stalost by remember {
        mutableStateOf(Stalost.denniEntries(
            today().dayOfWeek.isoDayNumber.let { if (time() > LocalTime(15, 45)) it + 1 else it }
        ).first())
    }
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
                            val cas = it.first().ucitel.split(" - ").first()
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
    var filtry by remember {
        mutableStateOf(listOf<FiltrNajdiMi>())
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
                    Text("Na škole jsou ${stalost.kdy} ${Seznamy.dny4Pad[denIndex]} ${hodinaIndexy.joinToString(" a ") { "$it." }} hodinu volné tyto ${filtry.text()}učebny:")
                }
                if (ucebna) items(volneTridy.toList()) {
                    Text("${it.nazev}, to je${it.napoveda}", Modifier.clickable {
                        najdiMiDialog = false
                        vybratRozvrh(it)
                    })
                }
                if (!ucebna) item {
                    Text("Na škole jsou ${stalost.kdy} ${Seznamy.dny4Pad[denIndex]} ${hodinaIndexy.joinToString(" a ") { "$it." }} hodinu volní tito ${filtry.text()}učitelé:")
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
                        stalost, denIndex, hodinaIndexy, filtry,
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
                        stalost, denIndex, hodinaIndexy, filtry,
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
                    seznam = Stalost.dnesniEntries().map { it.kdy },
                    value = stalost.kdy,
                    onClick = { i, _ ->
                        stalost = Stalost.dnesniEntries()[i]
                    },
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
                if (ucebna) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze odemčené učebny", Modifier.weight(1F))
                    Switch(
                        checked = FiltrNajdiMi.JenOdemcene in filtry,
                        onCheckedChange = {
                            if (FiltrNajdiMi.JenOdemcene in filtry) filtry -= FiltrNajdiMi.JenOdemcene
                            else if (FiltrNajdiMi.JenOdemcene !in filtry) filtry += FiltrNajdiMi.JenOdemcene
                        },
                    )
                }
                if (ucebna) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze celé učebny", Modifier.weight(1F))
                    Switch(
                        checked = FiltrNajdiMi.JenCele in filtry,
                        onCheckedChange = {
                            if (FiltrNajdiMi.JenCele in filtry) filtry -= FiltrNajdiMi.JenCele
                            else if (FiltrNajdiMi.JenCele !in filtry) filtry += FiltrNajdiMi.JenCele
                        },
                    )
                }
                if (!ucebna) Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Pouze moji vyučující", Modifier.weight(1F))
                    Switch(
                        checked = FiltrNajdiMi.JenSvi in filtry,
                        onCheckedChange = {
                            if (FiltrNajdiMi.JenSvi in filtry) filtry -= FiltrNajdiMi.JenSvi
                            else if (FiltrNajdiMi.JenSvi !in filtry) filtry += FiltrNajdiMi.JenSvi
                        },
                    )
                }
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
}