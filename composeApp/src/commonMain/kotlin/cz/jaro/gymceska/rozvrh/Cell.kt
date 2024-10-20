package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import cz.jaro.gymceska.ResponsiveText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias Week = List<Day>
typealias Day = List<Lesson>
typealias Lesson = List<Cell>

@Serializable
sealed interface Cell {
    val roomLike: String get() = ""
    val subjectLike: String get() = ""
    val teacherLike: String get() = ""
    val classLike: String get() = ""
    fun ColorScheme.backgroundColor(): Color = background
    fun ColorScheme.textColor(): Color = contentColorFor(backgroundColor())
    fun ColorScheme.subjectColor(): Color = textColor()
    val popupData: Map<String, String>? get() = null
    val isWholeDay: Boolean get() = false

    sealed interface Control : Cell
    sealed interface Data : Cell {
        val klass: String get() = ""
    }
    sealed interface Abnormal : Data

    @Serializable
    @SerialName("Normal")
    data class Normal(
        val changeInfo: String? = null,
        val room: String = "",
        val subject: String = "",
        val subjectName: String = "",
        val teacher: String = "",
        val teacherName: String = "",
        override val klass: String = "",
        val group: String = "",
        val theme: String = "",
    ) : Data {
        override val roomLike get() = room
        override val subjectLike get() = subject
        override val teacherLike get() = teacher
        override val classLike get() = klass and group.ifBlank { "" }
        override fun ColorScheme.backgroundColor() = if (changeInfo != null) errorContainer else background
        override fun ColorScheme.subjectColor() = if (theme.isNotBlank() && changeInfo == null) primary else textColor()
        override val popupData
            get() = mapOfNotNull(
                "Předmět:" to subjectName.takeUnless(String::isBlank),
                "Vyučující:" to teacherName.takeUnless(String::isBlank),
                "Učebna:" to room.takeUnless(String::isBlank),
                "Třída:" to klass.takeUnless(String::isBlank),
                "Skupina:" to group.takeUnless(String::isBlank),
                "Téma hodiny:" to theme.takeUnless(String::isBlank),
                "Změna:" to changeInfo?.takeUnless(String::isBlank),
            )
    }

    @Serializable
    @SerialName("Header")
    data class Header(
        val title: String = "",
        val subtitle: String = "",
    ) : Control {
        override val subjectLike get() = title
        override val teacherLike get() = subtitle
    }

    @Serializable
    @SerialName("Removed")
    data class Removed(
        val reasonText: String = "",
        val subject: String = "",
        val teacherName: String = "",
        override val klass: String = "",
    ) : Abnormal {
        override fun ColorScheme.backgroundColor() = errorContainer
        override val classLike get() = klass
        override val popupData
            get() = mapOfNotNull(
                reasonText to "",
                "Předmět:" to subject.takeUnless(String::isBlank),
                "Vyučující:" to teacherName.takeUnless(String::isBlank),
                "Třída:" to klass.takeUnless(String::isBlank),
            )
    }

    @Serializable
    @SerialName("Absent")
    data class Absent(
        val reason: String = "",
        val reasonText: String = "",
        override val klass: String = "",
        val group: String? = null,
    ) : Abnormal {
        override fun ColorScheme.backgroundColor() = tertiaryContainer
        override val subjectLike get() = reason
        override val classLike get() = klass and (group ?: "")
        override val popupData
            get() = mapOfNotNull(
                reasonText to "",
                "Třída:" to klass.takeUnless(String::isBlank),
                "Skupina:" to group?.takeUnless(String::isBlank),
            )
    }

    @Serializable
    @SerialName("DayOff")
    data class DayOff(
        val reasonText: String = "",
        override val klass: String = "",
    ) : Abnormal {
        override fun ColorScheme.backgroundColor() = tertiaryContainer
        override val subjectLike get() = reasonText
        override val classLike get() = klass
        override val isWholeDay get() = true
    }

    @Serializable
    @SerialName("Empty")
    data object Empty : Control
}

@Composable
fun Cell.backgroundColor() = MaterialTheme.colorScheme.backgroundColor()

@Composable
fun Cell.subjectColor() = MaterialTheme.colorScheme.subjectColor()

@Composable
fun Cell.textColor() = MaterialTheme.colorScheme.textColor()

@Composable
fun Cell(
    height: Float,
    cell: Cell,
    classes: List<Timetable.Class>,
    rooms: List<Timetable.Room>,
    teachers: List<Timetable.Teacher>,
    openTimetable: (timetable: Timetable) -> Unit,
    forceOneColumnCells: Boolean = false,
    onSubjectClick: (() -> Unit)?
) {
    val onRoomClick = if (cell is Cell.Normal && cell.room.isNotBlank()) (onClick@{
        openTimetable(rooms.find<Timetable.Room> { cell.room == it.zkratka } ?: return@onClick)
    }) else null

    val onClassClick = if (cell is Cell.Data && cell.klass.isNotBlank()) (onClick@{
        openTimetable(classes.find<Timetable.Class> { cell.klass == it.zkratka } ?: return@onClick)
    }) else null

    val onTeacherClick = if (cell is Cell.Normal && cell.teacher.isNotBlank()) (onClick@{
        openTimetable(teachers.find<Timetable.Teacher> { cell.teacher == it.zkratka } ?: return@onClick)
    }) else null

    val twoRowCell = height * LocalCellZoom.current < .7F
    val wholeRowCell = cell.isWholeDay && !forceOneColumnCells

    Surface(
        color = cell.backgroundColor(),
        contentColor = cell.textColor(),
        onClick = {
            onSubjectClick?.invoke()
        },
        enabled = onSubjectClick != null && cell.subjectLike.isBlank(),
    ) {
        when {
            wholeRowCell -> BaseCell(
                size = Size(10F, height),
                center = cell.subjectLike,
            )
            !twoRowCell -> BaseCell(
                size = Size(1F, height),
                center = cell.subjectLike,
                bottomCenter = cell.teacherLike,
                onBottomCenterClick = onTeacherClick,
                topStart = cell.roomLike,
                onTopStartClick = onRoomClick,
                topEnd = cell.classLike,
                onTopEndClick = onClassClick,
                centerStyle = TextStyle(
                    color = cell.subjectColor(),
                ),
                onCenterClick = onSubjectClick,
            )
            twoRowCell -> BaseCell(
                size = Size(1F, height),
                bottomStart = cell.subjectLike,
                bottomEnd = cell.teacherLike,
                onBottomEndClick = onTeacherClick,
                topStart = cell.roomLike,
                onTopStartClick = onRoomClick,
                topEnd = cell.classLike,
                onTopEndClick = onClassClick,
                bottomStartStyle = TextStyle(
                    color = cell.subjectColor(),
                    fontWeight = FontWeight.Bold,
                ),
                onBottomStartClick = onSubjectClick,
            )
        }
    }
}

fun mapOfNotNull(vararg pairs: Pair<String?, String?>?) = pairs.mapNotNull {
    if (it?.first == null || it.second == null) null
    else it.first!! to it.second!!
}.toMap()

infix fun String.and(other: String) = if (this.isEmpty() || other.isEmpty()) "$this$other" else "$this $other"

val baseCellSize = 128.dp

val LocalCellZoom = compositionLocalOf { 1F }

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
    val cellWidth = size.width * baseCellSize * LocalCellZoom.current
    val cellHeight = size.height * baseCellSize * LocalCellZoom.current

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

fun Week.justTimetable(): Week = drop(1).map { it.drop(1) }
fun Week.topHeaders() = first().drop(1).map { it.single() as Cell.Header }
fun Week.startHeaders() = drop(1).map { it.first().single() as Cell.Header }
fun Week.cornerHeader() = first().first().single() as Cell.Header

fun Cell.Data.copy(klass: String = this.klass) = when (this) {
    is Cell.Normal -> copy(klass = klass)
    is Cell.Absent -> copy(klass = klass)
    is Cell.Removed -> copy(klass = klass)
    is Cell.DayOff -> copy(klass = klass)
}