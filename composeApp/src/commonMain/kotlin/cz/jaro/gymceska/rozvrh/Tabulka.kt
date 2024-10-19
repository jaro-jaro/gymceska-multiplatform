package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import cz.jaro.gymceska.Offline
import cz.jaro.gymceska.OfflineRuzneCasti
import cz.jaro.gymceska.Online
import cz.jaro.gymceska.ZdrojRozvrhu
import cz.jaro.gymceska.ukoly.time
import cz.jaro.gymceska.ukoly.today
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

context(ColumnScope)
@Composable
fun Tabulka(
    vjec: Vjec,
    stalost: Stalost,
    tabulka: Tyden,
    kliklNaNeco: (vjec: Vjec) -> Unit,
    rozvrhOfflineWarning: ZdrojRozvrhu?,
    tridy: List<Vjec.TridaVjec>,
    mistnosti: List<Vjec.MistnostVjec>,
    vyucujici: List<Vjec.VyucujiciVjec>,
    hodiny: List<ClosedRange<LocalTime>>,
    mujRozvrh: Boolean,
    horScrollState: ScrollState,
    verScrollState: ScrollState,
    alwaysTwoRowCells: Boolean,
) {
    if (tabulka.isEmpty()) return

    val currentDay = if (stalost == Stalost.ThisWeek) today().dayOfWeek.isoDayNumber.takeIf { it in 1..5 }?.minus(1) else null
    val currentLesson = if (stalost == Stalost.ThisWeek) hodiny.indexOfFirst { it.contains(time()) }.takeUnless { it == -1 } else null

    val canAllowCellsSmallerThan1 = mujRozvrh || vjec !is Vjec.TridaVjec || alwaysTwoRowCells
    val maxByRow = tabulka.drop(1).map {
        it.drop(1).maxOf { hodina -> hodina.size }
    }
    val rowHeight = maxByRow.map { max ->
        if (max == 1) 1F
        else if (canAllowCellsSmallerThan1) max / 2F
        else (max / 2F).coerceAtLeast(2F)
    }

    BaseTable(
        data = tabulka,
        cornerCellContent = { hodina ->
            BaseCell(
                size = Size(.5F, .5F),
                center = hodina.single().predmet,
            )
        },
        topHeaderCellContent = { _, hodina ->
            val bunka = hodina.single()
            BaseCell(
                size = Size(1F, .5F),
                center = bunka.predmet,
                bottomCenter = bunka.ucitel.takeUnless { it.isBlank() },
                onCenterClick = {
                    if (bunka.predmet.isEmpty()) return@BaseCell
                    kliklNaNeco((if (vjec is Vjec.HodinaVjec) tridy else Seznamy.hodiny).find {
                        bunka.predmet == it.zkratka
                    } ?: return@BaseCell)
                }
            )
        },
        startHeaderCellContent = { row, hodina ->
            val bunka = hodina.single()
            BaseCell(
                size = Size(.5F, rowHeight[row]),
                center = bunka.predmet,
                bottomCenter = bunka.ucitel.takeUnless { it.isBlank() },
                onCenterClick = {
                    if (bunka.predmet.isEmpty()) return@BaseCell
                    kliklNaNeco(
                        (if (vjec is Vjec.DenVjec) tridy else Seznamy.dny).find {
                            bunka.predmet == it.zkratka
                        } ?: return@BaseCell
                    )
                }
            )
        },
        cellContent = { row, column, hodina ->
            val highlight = when (vjec) {
                is Vjec.TridaVjec, is Vjec.MistnostVjec, is Vjec.VyucujiciVjec -> currentDay == row && currentLesson == column
                is Vjec.DenVjec -> vjec.index - 1 == currentDay && currentLesson == column
                is Vjec.HodinaVjec -> vjec.index - 1 == currentLesson && currentDay == row
            }
            Column(
                if (highlight) Modifier.border(4.dp, MaterialTheme.colorScheme.tertiary) else Modifier,
            ) {
                val baseHeight = rowHeight[row] / hodina.size
                hodina.forEach { bunka ->
                    val cellHeight = when {
                        !mujRozvrh && vjec is Vjec.TridaVjec && hodina.size == 1 && bunka.tridaSkupina.isNotBlank() -> baseHeight * 4F / 5F
                        else -> baseHeight
                    }
                    Bunka(
                        height = cellHeight,
                        bunka = bunka,
                        tridy = tridy,
                        mistnosti = mistnosti,
                        vyucujici = vyucujici,
                        kliklNaNeco = kliklNaNeco,
                        forceOneColumnCells = vjec is Vjec.HodinaVjec,
                    )
                    if (cellHeight < baseHeight) BaseCell(
                        size = Size(width = 1F, height = baseHeight - cellHeight)
                    )
                }
            }
        },
        bottomContent = {
            rozvrhOfflineWarning?.let {
                Text(
                    when (it) {
                        Online -> "Prohlížíte si aktuální rozvrh."
                        is Offline -> "Prohlížíte si verzi rozvrhu z ${it.ziskano.dayOfMonth}. ${it.ziskano.monthNumber}. ${it.ziskano.hour}:${it.ziskano.minute.nula()}. "
                        is OfflineRuzneCasti -> "Nejstarší část tohoto rozvrhu pochází z ${it.nejstarsi.dayOfMonth}. ${it.nejstarsi.monthNumber}. ${it.nejstarsi.hour}:${it.nejstarsi.minute.nula()}. "
                    } + if (it != Online) "Pro aktualizaci dat klikněte Stáhnout vše." else "",
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        horScrollState = horScrollState,
        verScrollState = verScrollState
    )
}

@Composable
private fun <T> BaseTable(
    data: List<List<T>>,
    cornerCellContent: @Composable (value: T) -> Unit = {},
    topHeaderCellContent: @Composable (column: Int, value: T) -> Unit = { _, _ -> },
    startHeaderCellContent: @Composable (row: Int, value: T) -> Unit = { _, _ -> },
    cellContent: @Composable (row: Int, column: Int, value: T) -> Unit = { _, _, _ -> },
    bottomContent: @Composable () -> Unit = {},
    horScrollState: ScrollState = rememberScrollState(),
    verScrollState: ScrollState = rememberScrollState(),
) = Column(
    Modifier.doubleScrollable(horScrollState, verScrollState)
) {
    Row(
        modifier = Modifier
            .verticalScroll(rememberScrollState(), enabled = false)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState(), enabled = false)
        ) {
            cornerCellContent(data[0][0])
        }

        Row(
            modifier = Modifier
                .horizontalScroll(horScrollState, enabled = false)
        ) {
            data.first().drop(1).forEachIndexed { i, cell ->
                topHeaderCellContent(i, cell)
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .verticalScroll(verScrollState, enabled = false),
    ) {
        Row {
            Column(
                Modifier.horizontalScroll(rememberScrollState())
            ) {
                data.drop(1).map { it.first() }.forEachIndexed { i, cell ->
                    startHeaderCellContent(i, cell)
                }
            }

            Column(
                Modifier.horizontalScroll(horScrollState, enabled = false)
            ) {
                data.drop(1).forEachIndexed { i, row ->
                    Row {
                        row.drop(1).forEachIndexed { j, cell ->
                            cellContent(i, j, cell)
                        }
                    }
                }
            }
        }

        bottomContent()
    }
}

private fun Modifier.doubleScrollable(
    scrollStateX: ScrollState,
    scrollStateY: ScrollState,
) = composed {
    val coroutineScope = rememberCoroutineScope()

    val flingBehaviorX = ScrollableDefaults.flingBehavior()
    val flingBehaviorY = ScrollableDefaults.flingBehavior()

    val velocityTracker = remember { VelocityTracker() }
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }

    pointerInput(Unit) {
        detectDragGestures(
            onDrag = { pointerInputChange, offset ->
                coroutineScope.launch {
                    velocityTracker.addPointerInputChange(pointerInputChange)
                    scrollStateX.scrollBy(-offset.x)
                    scrollStateY.scrollBy(-offset.y)
                }
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity()
                velocityTracker.resetTracking()
                coroutineScope.launch {
                    scrollStateX.scroll {
                        val scrollScope = object : ScrollScope {
                            override fun scrollBy(pixels: Float): Float {
                                val consumedByPreScroll =
                                    nestedScrollDispatcher.dispatchPreScroll(Offset(pixels, 0F), NestedScrollSource.SideEffect).x
                                val scrollAvailableAfterPreScroll = pixels - consumedByPreScroll
                                val consumedBySelfScroll = this@scroll.scrollBy(scrollAvailableAfterPreScroll)
                                val deltaAvailableAfterScroll = scrollAvailableAfterPreScroll - consumedBySelfScroll
                                val consumedByPostScroll = nestedScrollDispatcher.dispatchPostScroll(
                                    Offset(consumedBySelfScroll, 0F),
                                    Offset(deltaAvailableAfterScroll, 0F),
                                    NestedScrollSource.SideEffect
                                ).x
                                return consumedByPreScroll + consumedBySelfScroll + consumedByPostScroll
                            }
                        }

                        with(flingBehaviorX) {
                            scrollScope.performFling(-velocity.x)
                        }
                    }
                }
                coroutineScope.launch {
                    scrollStateY.scroll {
                        val scrollScope = object : ScrollScope {
                            override fun scrollBy(pixels: Float): Float {
                                val consumedByPreScroll =
                                    nestedScrollDispatcher.dispatchPreScroll(Offset(0F, pixels), NestedScrollSource.SideEffect).y
                                val scrollAvailableAfterPreScroll = pixels - consumedByPreScroll
                                val consumedBySelfScroll = this@scroll.scrollBy(scrollAvailableAfterPreScroll)
                                val deltaAvailableAfterScroll = scrollAvailableAfterPreScroll - consumedBySelfScroll
                                val consumedByPostScroll = nestedScrollDispatcher.dispatchPostScroll(
                                    Offset(0F, consumedBySelfScroll),
                                    Offset(0F, deltaAvailableAfterScroll),
                                    NestedScrollSource.SideEffect
                                ).y
                                return consumedByPreScroll + consumedBySelfScroll + consumedByPostScroll
                            }
                        }

                        with(flingBehaviorY) {
                            scrollScope.performFling(-velocity.y)
                        }
                    }
                }
            },
            onDragStart = {
                velocityTracker.resetTracking()
            },
        )
    }.onPointerScrollEvent { event: PointerEvent ->
        event.changes.forEach { c ->
            val scrollDelta = c.scrollDelta.let {
                if (event.keyboardModifiers.isShiftPressed)
                    Offset(x = it.y, y = it.x)
                else it
            }
            coroutineScope.launch {
                scrollStateX.scrollBy(scrollDelta.x)
            }
            coroutineScope.launch {
                scrollStateY.scrollBy(scrollDelta.y)
            }
        }
    }
}

fun Modifier.onPointerScrollEvent(onScroll: (PointerEvent) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            onScroll(awaitPointerEvent())
        }
    }
}

fun Int.nula(): String = if ("$this".length == 1) "0$this" else "$this"