package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import cz.jaro.gymceska.ResponsiveText
import kotlinx.serialization.Serializable

typealias Tyden = List<Den>
typealias Den = List<Hodina>
typealias Hodina = List<Bunka>

@Serializable
data class Bunka(
    val ucebna: String,
    val predmet: String,
    val ucitel: String,
    val tridaSkupina: String = "",
    val typ: TypBunky = TypBunky.Normalni,
) {

    companion object
}

val Bunka.Companion.empty
    get() = Bunka(
        ucebna = "",
        predmet = "",
        ucitel = "",
        tridaSkupina = "",
        typ = TypBunky.Normalni
    )

fun Bunka.isEmpty() = ucebna.isEmpty() && predmet.isEmpty() && ucitel.isEmpty() && tridaSkupina.isEmpty()

@Composable
fun Bunka(
    height: Float,
    bunka: Bunka,
    tridy: List<Vjec.TridaVjec>,
    mistnosti: List<Vjec.MistnostVjec>,
    vyucujici: List<Vjec.VyucujiciVjec>,
    kliklNaNeco: (vjec: Vjec) -> Unit,
    forceOneColumnCells: Boolean = false,
) {
    val onUcebna = ucebna@{
        if (bunka.ucebna.isEmpty()) return@ucebna
        val vjec = mistnosti
            .find { bunka.ucebna == it.zkratka } ?: return@ucebna
        kliklNaNeco(vjec)
    }

    val onTrida = trida@{
        if (bunka.tridaSkupina.isEmpty()) return@trida
        val vjec = tridy.find {
            bunka.tridaSkupina
                .split(" ")
                .first() == it.zkratka
        } ?: return@trida
        kliklNaNeco(vjec)
    }

    val onUcitel = ucitel@{
        if (bunka.ucitel.isEmpty()) return@ucitel
        val vjec = vyucujici.find {
            bunka.ucitel
                .split(",")
                .first() == it.zkratka
        } ?: return@ucitel
        kliklNaNeco(vjec)
    }

    val twoRowCell = height * LocalBunkaZoom.current < .7F
    val wholeRowCell = bunka.typ == TypBunky.Volno && !forceOneColumnCells

    val size = if (wholeRowCell) Size(10F, height) else Size(1F, height)

    Surface(
        color = when (bunka.typ) {
            TypBunky.Suplovani -> MaterialTheme.colorScheme.errorContainer
            TypBunky.Volno, TypBunky.Trid -> MaterialTheme.colorScheme.tertiaryContainer
            TypBunky.Normalni -> MaterialTheme.colorScheme.background
        },
    ) {
        if (!twoRowCell && !wholeRowCell) BaseCell(
            size = size,
            center = bunka.predmet,
            bottomCenter = bunka.ucitel,
            onBottomCenterClick = onUcitel,
            topStart = bunka.ucebna,
            onTopStartClick = onUcebna,
            topEnd = bunka.tridaSkupina,
            onTopEndClick = onTrida,
            centerStyle = TextStyle(
                color = if (bunka.typ == TypBunky.Normalni) MaterialTheme.colorScheme.primary else Color.Unspecified,
            ),
        )
        else if (twoRowCell && !wholeRowCell) BaseCell(
            size = size,
            bottomStart = bunka.predmet,
            bottomEnd = bunka.ucitel,
            onBottomEndClick = onUcitel,
            topStart = bunka.ucebna,
            onTopStartClick = onUcebna,
            topEnd = bunka.tridaSkupina,
            onTopEndClick = onTrida,
            bottomStartStyle = TextStyle(
                color = if (bunka.typ == TypBunky.Normalni) MaterialTheme.colorScheme.primary else Color.Unspecified,
            ),
        )
        else BaseCell(
            size = size,
            center = bunka.predmet,
        )
    }
}

val zakladniVelikostBunky = 128.dp

val LocalBunkaZoom = compositionLocalOf { 1F }

//fun Boolean.toInt() = if (this) 1 else 0

@Composable
fun BaseCell(
    size: Size,
    modifier: Modifier = Modifier,
    topStart: String? = null,
    topStartStyle: TextStyle = LocalTextStyle.current,
    onTopStartClick: (() -> Unit)? = null,
    topEnd: String? = null,
    topEndStyle: TextStyle = LocalTextStyle.current,
    onTopEndClick: (() -> Unit)? = null,
    bottomStart: String? = null,
    bottomStartStyle: TextStyle = LocalTextStyle.current,
    onBottomStartClick: (() -> Unit)? = null,
    bottomEnd: String? = null,
    bottomEndStyle: TextStyle = LocalTextStyle.current,
    onBottomEndClick: (() -> Unit)? = null,
    bottomCenter: String? = null,
    bottomCenterStyle: TextStyle = LocalTextStyle.current,
    onBottomCenterClick: (() -> Unit)? = null,
    center: String? = null,
    centerStyle: TextStyle = LocalTextStyle.current,
    onCenterClick: (() -> Unit)? = null,
) = Box {
    val cellWidth = size.width * zakladniVelikostBunky * LocalBunkaZoom.current
    val cellHeight = size.height * zakladniVelikostBunky * LocalBunkaZoom.current

    Column(
        modifier = modifier
            .size(cellWidth, cellHeight - 0.5F.dp)
            .border(.5.dp, MaterialTheme.colorScheme.secondary)
            .padding(1.dp),
    ) {
        val isTop = topStart != null || topEnd != null
        val isCenter = center != null
        val isBottom = bottomStart != null || bottomCenter != null || bottomEnd != null
        val rows = listOf(isTop, isCenter, isBottom).count { it }

        if (isTop) Row(
            Modifier.size(cellWidth, cellHeight / rows),
        ) {
            val columns = listOf(topStart, topEnd).count { it != null }
            if (topStart != null) Box(
                Modifier.size(cellWidth / columns, cellHeight / rows).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                ResponsiveText(
                    text = topStart,
                    style = topStartStyle,
                    modifier = Modifier.clickable(enabled = onTopStartClick != null) {
                        onTopStartClick?.invoke()
                    },
                )
            }
            if (topEnd != null) Box(
                Modifier.size(cellWidth / columns, cellHeight / rows).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                ResponsiveText(
                    text = topEnd,
                    style = topEndStyle,
                    modifier = Modifier.clickable(enabled = onTopEndClick != null) {
                        onTopEndClick?.invoke()
                    },
                )
            }
        }
        if (center != null) Box(
            Modifier.size(cellWidth, cellHeight / rows).padding(1.dp),
            contentAlignment = Alignment.Center,
        ) {
            ResponsiveText(
                text = center,
                style = centerStyle,
                modifier = Modifier.clickable(enabled = onCenterClick != null) {
                    onCenterClick?.invoke()
                },
            )
        }
        if (isBottom) Row(
            Modifier.size(cellWidth, cellHeight / rows),
        ) {
            val columns = listOf(bottomStart, bottomCenter, bottomEnd).count { it != null }
            if (bottomStart != null) Box(
                Modifier.size(cellWidth / columns, cellHeight / rows).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                ResponsiveText(
                    text = bottomStart,
                    style = bottomStartStyle,
                    modifier = Modifier.clickable(enabled = onBottomStartClick != null) {
                        onBottomStartClick?.invoke()
                    },
                )
            }
            if (bottomCenter != null) Box(
                Modifier.size(cellWidth / columns, cellHeight / rows).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                ResponsiveText(
                    text = bottomCenter,
                    style = bottomCenterStyle,
                    modifier = Modifier.clickable(enabled = onBottomCenterClick != null) {
                        onBottomCenterClick?.invoke()
                    }
                )
            }
            if (bottomEnd != null) Box(
                Modifier.size(cellWidth / columns, cellHeight / rows).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                ResponsiveText(
                    text = bottomEnd,
                    style = bottomEndStyle,
                    modifier = Modifier.clickable(enabled = onBottomEndClick != null) {
                        onBottomEndClick?.invoke()
                    },
                )
            }
        }
    }
}

private val Float.px @Composable get() = with(LocalDensity.current) { toDp() }

fun Shape(createOutline: (size: Size, layoutDirection: LayoutDirection, density: Density) -> Outline) =
    object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            createOutline(size, layoutDirection, density)
    }

fun buildShape(buildPath: Path.(size: Size, layoutDirection: LayoutDirection, density: Density) -> Unit) =
    Shape { size, layoutDirection, density ->
        Outline.Generic(Path().apply { buildPath(size, layoutDirection, density) })
    }