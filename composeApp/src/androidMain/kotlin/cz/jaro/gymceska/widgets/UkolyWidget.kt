package cz.jaro.gymceska.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.jaro.gymceska.MainActivity
import cz.jaro.gymceska.R
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.ukoly.JednoduchyUkol
import cz.jaro.gymceska.ukoly.StavUkolu
import cz.jaro.gymceska.ukoly.dateFromUkol
import cz.jaro.gymceska.ukoly.zjednusit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("unused")
class UkolyWidget : GlanceAppWidget() {

    @SuppressLint("RestrictedApi")
    @Composable
    fun Content(
        context: Context
    ) = GlanceTheme {
        val prefs = currentState<Preferences>()
        val ukoly = Json.decodeFromString<List<JednoduchyUkol>>(prefs[stringPreferencesKey("ukoly")] ?: "[]")
            .filter {
                it.stav in listOf(StavUkolu.Neskrtly, StavUkolu.Skrtly, StavUkolu.Nadpis1)
            }

        val bg = ColorProvider(R.color.background_color)
        val onbg = ColorProvider(R.color.on_background_color)

        val action = actionStartActivity(Intent(context, MainActivity::class.java).apply {
            putExtra("ukoly", true)
        })

        LazyColumn(
            GlanceModifier.background(bg).fillMaxSize().padding(all = 8.dp).clickable(action),
        ) {
            item {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text("Domácí úkoly", GlanceModifier.defaultWeight(), style = TextStyle(color = onbg))
                    Image(
                        provider = ImageProvider(R.drawable.baseline_refresh_24),
                        colorFilter = ColorFilter.tint(onbg),
                        contentDescription = "Aktualizovat",
                        modifier = GlanceModifier.clickable {
                            updateAll(context)
                        },
                    )
                }
            }
            items(ukoly) { ukol ->
                if (ukol.stav == StavUkolu.Nadpis1)
                    Text(
                        text = context.resources.getString(R.string.splnene_ukoly),
                        GlanceModifier.padding(vertical = 4.dp).clickable(action),
                        style = TextStyle(
                            color = onbg
                        )
                    )
                else Row(
                    GlanceModifier.padding(vertical = 4.dp).clickable(action),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
//                    Checkbox(
//                        checked = ukol.stav == StavUkolu.Skrtly,
//                        onCheckedChange = {
//                            (if (ukol.stav == StavUkolu.Skrtly) odskrtnout else skrtnout)(ukol.id)
//                        }
//                    )
                    if (ukol.stav == StavUkolu.Skrtly) Text(
                        text = ukol.text,
                        GlanceModifier.clickable(action),
                        style = TextStyle(
                            color = ColorProvider(onbg.getColor(context).copy(alpha = .38F)),
                            textDecoration = TextDecoration.LineThrough,
                        ),
                    )
                    else Text(
                        text = ukol.text,
                        GlanceModifier.clickable(action),
                        style = TextStyle(
                            color = onbg
                        ),
                    )
                }
            }
            if (ukoly.isEmpty()) item {
                Text(
                    text = "Žádné úkoly nejsou! Jupí!!!",
                    GlanceModifier.clickable(action),
                    style = TextStyle(
                        color = onbg
                    )
                )
            }
        }
    }

    companion object {

        const val EXTRA_KEY_WIDGET_IDS = "providerwidgetids"

        fun updateAll(context: Context) {
            context.sendBroadcast(Intent().apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

                val appWidgetManager = AppWidgetManager.getInstance(context)

                putExtra(
                    EXTRA_KEY_WIDGET_IDS, appWidgetManager.getAppWidgetIds(
                        ComponentName(context, Reciever::class.java)
                    )
                )
            })
        }

        class Reciever : GlanceAppWidgetReceiver(), KoinComponent {
            override val glanceAppWidget: GlanceAppWidget = UkolyWidget()

            private val repo = get<Repository>()

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(EXTRA_KEY_WIDGET_IDS)) {
                    val ids = intent.extras!!.getIntArray(EXTRA_KEY_WIDGET_IDS)
                    onUpdate(context, AppWidgetManager.getInstance(context), ids!!)
                } else super.onReceive(context, intent)
            }

            @OptIn(ExperimentalUuidApi::class)
            override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
                super.onUpdate(context, appWidgetManager, appWidgetIds)

                CoroutineScope(Dispatchers.IO).launch {
                    val rawUkoly = repo.ukoly.first()
                    val skrtle = repo.skrtleUkoly.first()
                    val nastaveni = repo.nastaveni.first()
                    val ukoly = rawUkoly
                        ?.sortedBy(::dateFromUkol)
                        ?.map {
                            val ukolJeMuj = it.skupina.isEmpty() || nastaveni.mojeSkupiny.isEmpty() || it.skupina in nastaveni.mojeSkupiny
                            it.zjednusit(
                                stav = when {
                                    !ukolJeMuj -> StavUkolu.Cizi
                                    it.id in skrtle -> StavUkolu.Skrtly
                                    else -> StavUkolu.Neskrtly
                                }
                            )
                        }
                        ?.let { ukoly ->
                            if (ukoly.any { it.stav == StavUkolu.Skrtly })
                                ukoly.plus(JednoduchyUkol(id = Uuid.random(), "", stav = StavUkolu.Nadpis1))
                            else ukoly
                        }
                        ?.sortedBy {
                            when (it.stav) {
                                StavUkolu.Neskrtly -> 0
                                StavUkolu.Nadpis1 -> 1
                                StavUkolu.Skrtly -> 2
                                StavUkolu.Nadpis2 -> 3
                                StavUkolu.Cizi -> 4
                            }
                        } ?: return@launch

                    appWidgetIds.forEach {
                        val id = try {
                            GlanceAppWidgetManager(context).getGlanceIdBy(it)
                        } catch (_: IllegalArgumentException) {
                            return@forEach
                        }

                        updateAppWidgetState(context, id) { prefs ->
                            prefs[stringPreferencesKey("ukoly")] = Json.encodeToString(ukoly)
                        }
                        glanceAppWidget.update(context, id)
                    }
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }
}