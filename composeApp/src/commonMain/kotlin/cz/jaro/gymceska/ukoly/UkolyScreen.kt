package cz.jaro.gymceska.ukoly

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.jaro.gymceska.Navigator
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Route
import org.koin.core.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun Ukoly(
    args: Route.Ukoly,
    navigator: Navigator,
    koin: Koin,
) {
    val repo = koin.get<Repository>()
    val viewModel = viewModel<UkolyViewModel> {
        UkolyViewModel(
            repo = repo,
        )
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val jeOnline by viewModel.jeOnline.collectAsStateWithLifecycle()
    val jeInteligentni by viewModel.inteligentni.collectAsStateWithLifecycle()

    UkolyContent(
        state = state,
        skrtnout = viewModel::skrtnout,
        odskrtnout = viewModel::odskrtnout,
        navigator = navigator,
        jeOffline = !jeOnline,
        jeInteligentni = jeInteligentni,
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun UkolyContent(
    state: UkolyState,
    skrtnout: (Uuid) -> Unit,
    odskrtnout: (Uuid) -> Unit,
    navigator: Navigator,
    jeOffline: Boolean,
    jeInteligentni: Boolean,
) = Surface {
    UkolyNavigation(
        navigator = navigator,
        smiSpravovat = !jeOffline && jeInteligentni,
    ) { paddingValues ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            when (state) {
                UkolyState.Nacitani -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is UkolyState.Nacteno -> {
                    val lastUkol = state.ukoly.indexOfLast { it.stav != StavUkolu.Nadpis1 && it.stav != StavUkolu.Nadpis2 }
                    val lastMyUkol = state.ukoly.indexOfLast { it.stav == StavUkolu.Skrtly || it.stav == StavUkolu.Neskrtly }
                    items(state.ukoly.size, key = { state.ukoly[it].id }) { i ->
                        val ukol = state.ukoly[i]
                        if (ukol.stav == StavUkolu.Nadpis1 || ukol.stav == StavUkolu.Nadpis2) {
                            val alpha by animateFloatAsState(
                                if (ukol.stav == StavUkolu.Nadpis1 && i <= lastMyUkol || ukol.stav == StavUkolu.Nadpis2 && i < lastUkol) 1F else 0F, label = "alpha"
                            )
                            Text(
                                if (ukol.stav == StavUkolu.Nadpis1) "Splněné úkoly" else "Úkoly jiných skupin:",
                                Modifier
                                    .animateItem()
                                    .alpha(alpha)
                            )
                        } else
                            ListItem(
                                headlineContent = {
                                    if (ukol.stav == StavUkolu.Skrtly) Text(
                                        text = ukol.text,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38F)
                                    )
                                    else Text(
                                        text = ukol.text,
                                    )
                                },
                                Modifier.animateItem(),
                                leadingContent = if (ukol.stav != StavUkolu.Cizi) ({
                                    Checkbox(
                                        checked = ukol.stav == StavUkolu.Skrtly,
                                        onCheckedChange = {
                                            (if (ukol.stav == StavUkolu.Skrtly) odskrtnout else skrtnout)(ukol.id)
                                        }
                                    )
                                }) else null
                            )
                    }
                    if (state.ukoly.isEmpty()) item {
                        Text("Žádné úkoly nejsou! Jupí!!!")
                    }
                }
            }
        }
    }
}