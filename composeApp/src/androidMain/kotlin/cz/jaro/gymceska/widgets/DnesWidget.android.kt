package cz.jaro.gymceska.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.jaro.gymceska.MainActivity
import cz.jaro.gymceska.OnlineTimetableSource
import cz.jaro.gymceska.R
import cz.jaro.gymceska.SettingsFlow
import cz.jaro.gymceska.rozvrh.Cell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


@Suppress("unused", "CONTEXT_RECEIVERS_DEPRECATED")
@SuppressLint("RestrictedApi")
class DnesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private val bg = ColorProvider(R.color.background_color)
    private val onBg = ColorProvider(R.color.on_background_color)
    private val bgChange = ColorProvider(R.color.background_color_alt)
    private val onBgChange = ColorProvider(R.color.on_background_color_alt)
    private val bgAbsent = ColorProvider(R.color.background_color_alt2)
    private val onBgAbsent = ColorProvider(R.color.on_background_color_alt2)

    private val basePadding = 4.dp
    private val innerPadding = basePadding
    private val outerVerticalPadding = basePadding
    private val outerHorizontalPadding = basePadding * 2
    private val image = 24.dp
    private val separatorHeight = basePadding
    private val mainSeparatorHeight = basePadding * 3

    @Composable
    fun Content(
        context: Context,
    ) = GlanceTheme {
        val prefs = currentState<Preferences>()
        val lessons =
            Json.decodeFromString<List<Cell.NonEdit>>(prefs[hodinyKey] ?: "[]")
                .ifEmpty {
                    listOf(Cell.Header("Žádné hodiny!"))
                }
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it else it.take(9)
                }
        val den = prefs[denKey] ?: "??. ??."

        Column(
            GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {

            val (width, height) = determineCellLayout(
                lessonCount = lessons.count { it !is Cell.Empty && it !is Cell.Removed },
                breakCount = lessons.count { it is Cell.Empty || it is Cell.Removed },
            )

            val context = LocalContext.current
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (width > 1) Text(
                    den,
                    GlanceModifier.defaultWeight(),
                    style = TextStyle(color = onBg)
                )
                Image(
                    provider = ImageProvider(R.drawable.baseline_refresh_24),
                    colorFilter = ColorFilter.tint(onBg),
                    contentDescription = "Aktualizovat",
                    modifier = GlanceModifier.clickable {
                        updateAll(context)
                    },
                )
            }

            Column(
                GlanceModifier.fillMaxSize(),
            ) {
                val firstLesson = lessons.indexOfFirst { it !is Cell.WithoutText }
                lessons.take(MAIN_BREAK_INDEX).forEachIndexed { i, cell ->
                    DrawCell(cell, i, firstLesson, width, height)
                }
                Separator(isMainBreakCompliment = true)
                lessons.drop(MAIN_BREAK_INDEX).forEachIndexed { i, cell ->
                    DrawCell(cell, i + MAIN_BREAK_INDEX, firstLesson, width, height)
                }
            }
        }
    }

    context(ColumnScope)
    @Composable
    private fun DrawCell(
        cell: Cell.NonEdit,
        i: Int,
        firstLesson: Int,
        width: Int,
        height: Int
    ) {
        when (cell) {
            is Cell.Empty, is Cell.Removed -> cell.DrawBreak(
                isSleeping = i < firstLesson,
            )

            else -> cell.DrawCell(
                width = width,
                height = height,
            )
        }
    }

    context(ColumnScope)
    @Composable
    private fun Cell.DataOrEmpty.DrawBreak(
        isSleeping: Boolean,
    ) = Column(
        GlanceModifier
            .clickable(actionStartActivity<MainActivity>())
            .fillMaxWidth()
    ) {
        Separator()
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(
                    when (this@DrawBreak) {
                        is Cell.Empty -> bg
                        is Cell.Data -> bgChange
                    }
                )
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(
                    if (isSleeping) R.drawable.outline_hotel_24 else R.drawable.outline_emoji_food_beverage_24
                ),
                colorFilter = ColorFilter.tint(
                    when (this@DrawBreak) {
                        is Cell.Empty -> onBg
                        is Cell.Data -> onBgChange
                    }
                ),
                contentDescription = "Přestávka",
            )
        }
    }

    @Composable
    private fun determineCellLayout(
        lessonCount: Int,
        breakCount: Int,
    ): Pair<Int, Int> {
        val scale = currentState<Preferences>()[scaleKey] ?: 1F
        val context = LocalContext.current
        val fontFamilyResolver = createFontFamilyResolver(context)
        val density = Density(context)
        val layoutDirection = LayoutDirection.Ltr

        val textMeasurer = remember(
            fontFamilyResolver,
            density,
            layoutDirection
        ) {
            TextMeasurer(
                defaultFontFamilyResolver = fontFamilyResolver,
                defaultDensity = density,
                defaultLayoutDirection = layoutDirection,
            )
        }

        val letter = with(density) {
            textMeasurer.measure(
                text = "H",
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp * scale)
            ).size.toSize().toDpSize()
        }

        fun sizes(textSize: Dp, outerPadding: Dp) = List(3) { i ->
            textSize * (1 + i) + innerPadding * i + outerPadding * 2 + separatorHeight
        }

        val heights = sizes(letter.height, outerVerticalPadding)
        val widths = sizes(letter.width * 4, outerHorizontalPadding)

        val size = LocalSize.current
        val breakOrHeader = outerVerticalPadding * 2 + image + separatorHeight
        val breakAndHeaderCount = breakCount + 1
        val breaksAndHeaders = breakOrHeader * breakAndHeaderCount
        val mainBreak = if (lessonCount + breakCount > MAIN_BREAK_INDEX)
            mainSeparatorHeight - separatorHeight
        else 0.dp
        val height = heights.indexOfLast {
            it * lessonCount + breaksAndHeaders + mainBreak - separatorHeight <= size.height
        }.takeUnless { it == -1 }?.plus(1) ?: 1
        val width = widths.indexOfLast { it <= size.width }
            .takeUnless { it == -1 }?.plus(1) ?: 1
        return width to height
    }

    context(ColumnScope)
    @Composable
    private fun Cell.NonEdit.DrawCell(
        width: Int,
        height: Int,
    ) = Column(
        GlanceModifier
            .clickable(actionStartActivity<MainActivity>())
            .defaultWeight()
            .fillMaxWidth()
    ) {
        Separator()
        Column(
            GlanceModifier
                .padding(
                    horizontal = outerHorizontalPadding - innerPadding / 2,
                    vertical = outerVerticalPadding - innerPadding / 2,
                )
                .clickable(actionStartActivity<MainActivity>())
                .defaultWeight()
                .fillMaxWidth()
                .background(
                    when (this@DrawCell) {
                        is Cell.Normal if changeInfo != null -> bgChange
                        is Cell.ST if groups.any { it.changeInfo != null } -> bgChange
                        is Cell.Removed -> bgChange
                        is Cell.Normal, is Cell.ST, is Cell.Header, is Cell.Empty -> bg
                        is Cell.Absent, is Cell.DayOff -> bgAbsent
                    }
                )
        ) {
            when (width) {
                1 -> when (height) {
                    1 -> Cell11()
                    2 -> Cell12()
                    3 -> Cell13()
                }

                2 -> when (height) {
                    1 -> Cell21()
                    2 -> Cell22()
                    3 -> Cell33and23()
                }

                3 -> when (height) {
                    1 -> Cell31()
                    2 -> Cell32()
                    3 -> Cell33and23()
                }
            }
        }
    }

    @Composable
    fun Separator(isMainBreakCompliment: Boolean = false) = Box(
        GlanceModifier
            .height(if (isMainBreakCompliment) mainSeparatorHeight - separatorHeight else separatorHeight)
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {}

    companion object {

        private const val MAIN_BREAK_INDEX = 4 // v pořadí 4. vyučovací hodina dne

        const val EXTRA_KEY_WIDGET_IDS = "providerwidgetids"

        val hodinyKey = stringPreferencesKey("hodiny")
        val denKey = stringPreferencesKey("den")
        val scaleKey = floatPreferencesKey("scale")

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

            private val timetableSource = get<OnlineTimetableSource>()
            private val settings = get<SettingsFlow>()

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(EXTRA_KEY_WIDGET_IDS)) {
                    val ids = intent.extras!!.getIntArray(EXTRA_KEY_WIDGET_IDS)
                    onUpdate(context, AppWidgetManager.getInstance(context), ids!!)
                } else super.onReceive(context, intent)
            }

            override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray
            ) {
                super.onUpdate(context, appWidgetManager, appWidgetIds)

                CoroutineScope(Dispatchers.IO).launch {
                    val (den, hodiny) = timetableSource.rozvrhWidgetData(settings)

                    appWidgetIds.forEach {
                        val id = GlanceAppWidgetManager(context).getGlanceIdBy(it)

                        updateAppWidgetState(context, id) { prefs ->
                            prefs[hodinyKey] = Json.encodeToString(hodiny)
                            prefs[denKey] = den.run { "$dayOfMonth. $monthNumber." }
                            prefs[scaleKey] = settings.value.widgetTextScale
                        }
                        glanceAppWidget.update(context, id)
                    }
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideContent { Content(context) }

    context(RowScope)
    @Composable
    fun Cell.NonEdit.DrawText(
        text: String,
        alignment: Alignment,
        bold: Boolean = false,
    ) = Box(
        GlanceModifier
            .defaultWeight()
            .padding(innerPadding / 2),
        contentAlignment = alignment,
    ) {
        val scale = currentState<Preferences>()[scaleKey] ?: 1F
        Text(
            text = text,
            modifier = GlanceModifier
                .clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = when (this) {
                    is Cell.Normal if changeInfo != null -> onBgChange
                    is Cell.ST if groups.any { it.changeInfo != null } -> onBgChange
                    is Cell.Removed -> onBgChange
                    is Cell.Normal, is Cell.ST, is Cell.Header, is Cell.Empty -> onBg
                    is Cell.Absent, is Cell.DayOff -> onBgAbsent
                },
                textAlign = TextAlign.Center,
                fontSize = 14.sp * scale
            ),
        )
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell33and23() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            DrawText(roomLike, Alignment.TopStart)
            DrawText(classLike, Alignment.TopEnd)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawText(subjectLike, Alignment.Center, bold = true)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            DrawText(teacherLike, Alignment.BottomCenter)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell13() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            DrawText(roomLike, Alignment.TopCenter)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawText(subjectLike, Alignment.Center, bold = true)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            DrawText(teacherLike, Alignment.BottomCenter)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell32() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            DrawText(roomLike, Alignment.TopStart)
            DrawText(classLike, Alignment.TopEnd)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            DrawText("", Alignment.BottomStart)
            DrawText(subjectLike, Alignment.BottomCenter, bold = true)
            DrawText(teacherLike, Alignment.BottomEnd)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell22() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            DrawText(roomLike, Alignment.TopStart)
            DrawText(classLike, Alignment.TopEnd)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            DrawText(subjectLike, Alignment.BottomStart, bold = true)
            DrawText(teacherLike, Alignment.BottomEnd)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell12() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            DrawText(roomLike, Alignment.TopCenter)
        }
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            DrawText(subjectLike, Alignment.BottomCenter, bold = true)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell31() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawText(roomLike, Alignment.BottomStart)
            DrawText(subjectLike, Alignment.BottomCenter, bold = true)
            DrawText(teacherLike, Alignment.BottomEnd)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell21() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawText(roomLike, Alignment.BottomStart)
            DrawText(subjectLike, Alignment.BottomEnd, bold = true)
        }
    }

    context(ColumnScope)
    @Composable
    fun Cell.NonEdit.Cell11() {
        Row(
            GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawText(subjectLike, Alignment.Center, bold = true)
        }
    }
}