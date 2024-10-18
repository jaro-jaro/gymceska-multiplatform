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
import kotlinx.coroutines.launch

context(ColumnScope)
@Composable
fun Tabulka(
    vjec: Vjec,
    tabulka: Tyden,
    kliklNaNeco: (vjec: Vjec) -> Unit,
    rozvrhOfflineWarning: ZdrojRozvrhu?,
    tridy: List<Vjec.TridaVjec>,
    mistnosti: List<Vjec.MistnostVjec>,
    vyucujici: List<Vjec.VyucujiciVjec>,
    mujRozvrh: Boolean,
    horScrollState: ScrollState,
    verScrollState: ScrollState,
    alwaysTwoRowCells: Boolean,
) {
    if (tabulka.isEmpty()) return

    val canAllowCellsSmallerThan1 = mujRozvrh || vjec !is Vjec.TridaVjec || alwaysTwoRowCells
    val maxByRow = tabulka.map {
        it.maxOf { hodina -> hodina.size }
    }
    val rowHeight = maxByRow.map { max ->
        if (max == 1) 1F
        else if (canAllowCellsSmallerThan1) max / 2F
        else (max / 2F).coerceAtLeast(2F)
    }

    Column(
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
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                BaseCell(
                    size = Size(.5F, .5F),
                    center = tabulka[0][0][0].predmet,
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(horScrollState, enabled = false, reverseScrolling = true)
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                tabulka.first().drop(1).map { it.first() }.forEachIndexed { i, bunka ->
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
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(verScrollState, enabled = false, reverseScrolling = true),
        ) {
            Row {
                Column(
                    Modifier.horizontalScroll(rememberScrollState())
                ) {
                    tabulka.drop(1).map { it.first().first() }.forEachIndexed { i, bunka ->
                        BaseCell(
                            size = Size(.5F, rowHeight[i + 1]),
                            center = bunka.predmet,
                            onCenterClick = {
                                if (bunka.predmet.isEmpty()) return@BaseCell
                                kliklNaNeco(
                                    (if (vjec is Vjec.DenVjec) tridy else Seznamy.dny).find {
                                        bunka.predmet == it.zkratka
                                    } ?: return@BaseCell
                                )
                            }
                        )
                    }
                }

                Column(
                    Modifier.horizontalScroll(horScrollState, enabled = false, reverseScrolling = true)
                ) {
                    tabulka.drop(1).forEachIndexed { i, radek ->
                        Row {
                            radek.drop(1).forEachIndexed { j, hodina ->
                                Column(
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                                ) {
                                    val baseHeight = rowHeight[i + 1] / hodina.size
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
                            }
                        }
                    }
                }
            }

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
        }
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
                    scrollStateX.scrollBy(offset.x)
                    scrollStateY.scrollBy(offset.y)
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
                            scrollScope.performFling(velocity.x)
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
                            scrollScope.performFling(velocity.y)
                        }
                    }
                }
            },
            onDragStart = {
                velocityTracker.resetTracking()
            },
        )
    }.onPointerScrollEvent { event: PointerEvent ->
        val scrollDelta = event.changes.fold(Offset.Zero) { acc, c -> acc + c.scrollDelta }.let {
            if (event.keyboardModifiers.isShiftPressed)
                Offset(x = it.y, y = it.x)
            else it
        }
        coroutineScope.launch {
            scrollStateX.scrollBy(-scrollDelta.x)
        }
        coroutineScope.launch {
            scrollStateY.scrollBy(-scrollDelta.y)
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