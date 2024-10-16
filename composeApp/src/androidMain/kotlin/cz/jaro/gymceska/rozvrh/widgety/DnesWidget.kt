package cz.jaro.gymceska.rozvrh.widgety

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.jaro.gymceska.MainActivity
import cz.jaro.gymceska.PrepnoutRozvrhWidget
import cz.jaro.gymceska.R
import cz.jaro.gymceska.Repository
import cz.jaro.gymceska.Uspech
import cz.jaro.gymceska.rozvrh.Bunka
import cz.jaro.gymceska.rozvrh.Stalost
import cz.jaro.gymceska.rozvrh.TypBunky
import cz.jaro.gymceska.rozvrh.filtrovatDen
import cz.jaro.gymceska.rozvrh.isEmpty
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.hours


@Suppress("unused")
class DnesWidget : GlanceAppWidget() {

    @SuppressLint("RestrictedApi")
    @Composable
    fun Content(
        context: Context
    ) = GlanceTheme {
        val prefs = currentState<Preferences>()
        val bunky = Json.decodeFromString<List<Bunka>>(prefs[stringPreferencesKey("hodiny")] ?: "[]")
        val den = prefs[stringPreferencesKey("den")] ?: "??. ??."

        val bg = ColorProvider(R.color.background_color)
        val onbg = ColorProvider(R.color.on_background_color)
        val bg2 = ColorProvider(R.color.background_color_alt)
        val onbg2 = ColorProvider(R.color.on_background_color_alt)
        val bg3 = ColorProvider(R.color.background_color_alt2)
        val onbg3 = ColorProvider(R.color.on_background_color_alt2)

        Column(
            GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {

            bunky
                .ifEmpty {
                    listOf(Bunka("", "Žádné hodiny!", ""))
                }
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it else it.take(10)
                }
                .let { hodiny ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(den, GlanceModifier.defaultWeight(), style = TextStyle(color = onbg))
                        Image(
                            provider = ImageProvider(R.drawable.baseline_refresh_24),
                            colorFilter = ColorFilter.tint(onbg),
                            contentDescription = "Aktualizovat",
                            modifier = GlanceModifier.clickable {
                                updateAll(context)
                            },
                        )
                    }

                    Cara()

                    hodiny
                        .forEachIndexed { i, it ->
                            with(it) {
                                Column(
                                    GlanceModifier
                                        .clickable(actionStartActivity<MainActivity>())
                                        .defaultWeight()
                                        .fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .clickable(actionStartActivity<MainActivity>())
                                            .fillMaxWidth()
                                            .defaultWeight()
                                            .background(
                                                when (typ) {
                                                    TypBunky.Normalni -> bg
                                                    TypBunky.Suplovani -> bg2
                                                    TypBunky.Volno, TypBunky.Trid -> bg3
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.TopStart,
                                            modifier = GlanceModifier
                                                .clickable(actionStartActivity<MainActivity>())
                                                .fillMaxSize()
                                        ) {
                                            Text(
                                                text = ucebna,
                                                modifier = GlanceModifier
                                                    .clickable(actionStartActivity<MainActivity>())
                                                    .padding(all = 8.dp),
                                                style = TextStyle(
                                                    color = when (typ) {
                                                        TypBunky.Normalni -> onbg
                                                        TypBunky.Suplovani -> onbg2
                                                        TypBunky.Volno, TypBunky.Trid -> onbg3
                                                    }
                                                ),
                                            )
                                        }
                                        Box(
                                            contentAlignment = Alignment.TopEnd,
                                            modifier = GlanceModifier
                                                .clickable(actionStartActivity<MainActivity>())
                                                .fillMaxSize()
                                        ) {
                                            Text(
                                                text = tridaSkupina,
                                                modifier = GlanceModifier
                                                    .clickable(actionStartActivity<MainActivity>())
                                                    .padding(all = 8.dp),
                                                style = TextStyle(
                                                    color = when (typ) {
                                                        TypBunky.Normalni -> onbg
                                                        TypBunky.Suplovani -> onbg2
                                                        TypBunky.Volno, TypBunky.Trid -> onbg3
                                                    }
                                                ),
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = GlanceModifier
                                                .clickable(actionStartActivity<MainActivity>())
                                                .fillMaxSize()
                                        ) {
                                            Text(text = "")
                                            Spacer(GlanceModifier.defaultWeight())
                                            Text(
                                                text = predmet,
                                                modifier = GlanceModifier
                                                    .clickable(actionStartActivity<MainActivity>())
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp),
                                                style = TextStyle(
                                                    color = when (typ) {
                                                        TypBunky.Normalni -> onbg
                                                        TypBunky.Suplovani -> onbg2
                                                        TypBunky.Volno, TypBunky.Trid -> onbg3
                                                    },
                                                    textAlign = TextAlign.Center
                                                ),
                                            )
                                            Spacer(GlanceModifier.defaultWeight())
                                            Text(
                                                text = ucitel,
                                                modifier = GlanceModifier
                                                    .clickable(actionStartActivity<MainActivity>())
                                                    .padding(bottom = 8.dp),
                                                style = TextStyle(
                                                    color = when (typ) {
                                                        TypBunky.Normalni -> onbg
                                                        TypBunky.Suplovani -> onbg2
                                                        TypBunky.Volno, TypBunky.Trid -> onbg3
                                                    }
                                                ),
                                            )
                                        }
                                    }
                                    if (i < hodiny.lastIndex)
                                        Cara()
                                }
                            }
                        }
                }
        }
    }

    @Composable
    fun Cara() = Box(modifier = GlanceModifier.height(2.dp).fillMaxWidth().background(Color.Transparent)) {}

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
            override val glanceAppWidget: GlanceAppWidget = DnesWidget()

            private val repo = get<Repository>()

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(EXTRA_KEY_WIDGET_IDS)) {
                    val ids = intent.extras!!.getIntArray(EXTRA_KEY_WIDGET_IDS)
                    onUpdate(context, AppWidgetManager.getInstance(context), ids!!)
                } else super.onReceive(context, intent)
            }

            override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
                super.onUpdate(context, appWidgetManager, appWidgetIds)

                CoroutineScope(Dispatchers.IO).launch {
                    val nastaveni = repo.nastaveni.first()

                    val dnes = rozvrhZobrazitNaDnesek()

                    val den = today().plus(DatePeriod(days = if (dnes) 0 else 1))
                    val cisloDne = den.dayOfWeek.value // 1-5 (2-7)

                    val stalost = if (cisloDne == 1 && !dnes) Stalost.PristiTyden else Stalost.TentoTyden

                    val hodiny = repo.ziskatRozvrh(stalost).let { result ->
                        if (result !is Uspech) return@let listOf(Bunka("", "Žádná data!", ""))

                        val tabulka = result.rozvrh

                        tabulka
                            .getOrNull(cisloDne)
                            ?.asSequence()
                            ?.drop(1)
                            ?.mapIndexed { i, hodina -> i to hodina }
                            ?.filter { (_, hodina) -> hodina.first().predmet.isNotBlank() }
                            ?.map { (i, hodina) -> hodina.map { bunka -> bunka.copy(predmet = "$i. ${bunka.predmet}") } }
                            ?.toList()
                            ?.filtrovatDen(true, nastaveni.mojeSkupiny)
                            ?.mapNotNull { hodina -> hodina.firstOrNull { !it.isEmpty() } }
                            ?.ifEmpty {
                                listOf(
                                    Bunka("", "Žádné hodiny!", ""),
                                )
                            }
                            ?: listOf(Bunka("", "Víkend", ""))
                    }

                    appWidgetIds.forEach {
                        val id = GlanceAppWidgetManager(context).getGlanceIdBy(it)

                        updateAppWidgetState(context, id) { prefs ->
                            prefs[stringPreferencesKey("hodiny")] = Json.encodeToString(hodiny)
                            prefs[stringPreferencesKey("den")] = den.run { "$dayOfMonth. $monthNumber." }
                        }
                        glanceAppWidget.update(context, id)
                    }
                }
            }

            private suspend fun rozvrhZobrazitNaDnesek() =
                when (val nastaveni = repo.nastaveni.first().prepnoutRozvrhWidget) {
                    is PrepnoutRozvrhWidget.OPulnoci -> true
                    is PrepnoutRozvrhWidget.VCas -> {
                        val cas = time()
                        cas < nastaveni.cas
                    }

                    is PrepnoutRozvrhWidget.PoKonciVyucovani -> {
                        val cas = Clock.System.now()
                        val konecVyucovani = zjistitKonecVyucovani()

                        (cas - nastaveni.poHodin.hours).toLocalDateTime(TimeZone.currentSystemDefault()).time < konecVyucovani
                    }
                }

            private suspend fun zjistitKonecVyucovani(): LocalTime {
                val nastaveni = repo.nastaveni.first()

                val result = repo.ziskatRozvrh(Stalost.TentoTyden)

                if (result !is Uspech) return LocalTime(0, 0)

                val tabulka = result.rozvrh

                val denTydne = today().dayOfWeek.value /* 1-5 (6-7) */

                val den = tabulka.getOrNull(denTydne) ?: return LocalTime(12, 0)

                val hodina = den
                    .mapIndexed { i, hodina -> i to hodina }
                    .drop(1)
                    .filter { (_, hodina) -> hodina.first().predmet.isNotBlank() }
                    .lastOrNull { (_, hodina) ->
                        hodina.any { bunka ->
                            bunka.tridaSkupina.isEmpty() || bunka.tridaSkupina in nastaveni.mojeSkupiny
                        }
                    }
                    ?.first
                    ?: return LocalTime(12, 0)

                return tabulka.first()[hodina].first().ucitel.split(" - ")[1].split(":").let { LocalTime(it[0].toInt(), it[1].toInt()) }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }
}
