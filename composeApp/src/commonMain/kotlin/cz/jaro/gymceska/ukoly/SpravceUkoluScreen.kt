package cz.jaro.gymceska.ukoly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.core.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun SpravceUkolu(
    args: Route.SpravceUkolu,
    navigator: Navigator,
    koin: Koin,
) {
    val repo = koin.get<Repository>()
    val viewModel = viewModel<UkolyViewModel> {
        UkolyViewModel(
            repo = repo,
        )
    }

    val ukoly by viewModel.ukoly.collectAsStateWithLifecycle()

    SpravceUkoluContent(
        ukoly = ukoly,
        pridatUkol = viewModel::pridatUkol,
        odebratUkol = viewModel::odebratUkol,
        zmenitUkol = viewModel::upravitUkol,
        navigateBack = navigator::navigateUp,
        navigate = navigator::navigate,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun SpravceUkoluContent(
    ukoly: List<Ukol>?,
    pridatUkol: ((Uuid) -> Unit) -> Unit,
    odebratUkol: (Uuid) -> Unit,
    zmenitUkol: (Ukol) -> Unit,
    navigateBack: () -> Unit,
    navigate: (Route) -> Unit,
) = Surface {
    var upravovat by rememberSaveable { mutableStateOf(null as Uuid?) }
    var datum by rememberSaveable { mutableStateOf("") }
    var predmet by rememberSaveable { mutableStateOf("") }
    var skupina by rememberSaveable { mutableStateOf("") }
    var nazev by rememberSaveable { mutableStateOf("") }
    fun reset() {
        datum = ""
        predmet = ""
        skupina = ""
        nazev = ""
    }

    fun update() {
        val ukol = ukoly?.find { it.id == upravovat } ?: return
        datum = ukol.datum
        predmet = ukol.predmet
        skupina = ukol.skupina
        nazev = ukol.nazev
    }

    var datumDialog by rememberSaveable { mutableStateOf(false) }

    if (upravovat != null) AlertDialog(
        onDismissRequest = {
            upravovat = null
            reset()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    zmenitUkol(Ukol(datum = datum, nazev = nazev, skupina = skupina, predmet = predmet, id = upravovat!!))
                    upravovat = null
                    reset()
                }
            ) {
                Text("Uložit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    upravovat = null
                    reset()
                }
            ) {
                Text("Zrušit")
            }
        },
        title = {
            Text("Upravit úkol")
        },
        icon = {
            Icon(Icons.Default.Edit, null)
        },
        text = {
            val focusManager = LocalFocusManager.current
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                val predvybranyDatum by remember(datum) {
                    derivedStateOf {
                        dateFromString(datum) ?: today()
                    }
                }

                val state = rememberDatePickerState(
                    initialSelectedDateMillis = predvybranyDatum.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                    initialDisplayMode = DisplayMode.Picker,
                )
                if (datumDialog) DatePickerDialog(
                    onDismissRequest = {
                        datumDialog = false
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datumDialog = false
                                val chosenDate = Instant.fromEpochMilliseconds(state.selectedDateMillis!!).toLocalDateTime(TimeZone.UTC).date
                                datum = "${chosenDate.dayOfMonth}. ${chosenDate.monthNumber}."
                                datumDialog = false
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        ) {
                            Text("OK")
                        }
                    },
                ) {
                    DatePicker(
                        state = state,
                        title = {
                            DatePickerDefaults.DatePickerTitle(
                                displayMode = state.displayMode,
                                Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp))
                            )
                        },
                    )
                }

                TextField(
                    value = datum,
                    onValueChange = {
                        datum = it
                    },
                    Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                datumDialog = true
                            }
                        ) {
                            Icon(Icons.Default.CalendarMonth, "Vybrat datum")
                        }
                    },
                    keyboardActions = KeyboardActions {
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    label = {
                        Text("Datum")
                    },
                    singleLine = true,
                )
                TextField(
                    value = predmet,
                    onValueChange = {
                        predmet = it
                    },
                    Modifier.fillMaxWidth(),
                    keyboardActions = KeyboardActions {
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    label = {
                        Text("Předmět")
                    },
                    singleLine = true,
                )
                TextField(
                    value = skupina,
                    onValueChange = {
                        skupina = it
                    },
                    Modifier.fillMaxWidth(),
                    keyboardActions = KeyboardActions {
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    label = {
                        Text("Skupina")
                    },
                    singleLine = true,
                )
                TextField(
                    value = nazev,
                    onValueChange = {
                        nazev = it
                    },
                    Modifier.fillMaxWidth(),
                    label = {
                        Text("Název")
                    },
                    singleLine = true,
                )
            }
        },
    )

    SpravceUkoluNavigation(
        navigateBack = navigateBack,
        pridatUkol = {
            pridatUkol {
                upravovat = it
                datumDialog = true
                update()
            }
        },
        navigate = navigate,
    ) { paddingValues ->
        if (ukoly == null) LinearProgressIndicator(Modifier.padding(paddingValues))
        else LazyColumn(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            items(ukoly, key = { it.id }) { ukol ->
                ListItem(
                    headlineContent = {
                        Text(ukol.asString())
                    },
                    Modifier.animateItem(),
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    odebratUkol(ukol.id)
                                }
                            ) {
                                Icon(Icons.Default.DeleteForever, "Odstranit úkol")
                            }
                            IconButton(
                                onClick = {
                                    upravovat = ukol.id
                                    update()
                                }
                            ) {
                                Icon(Icons.Default.Edit, "Upravit úkol")
                            }
                        }
                    },
                )
            }
        }
    }
}