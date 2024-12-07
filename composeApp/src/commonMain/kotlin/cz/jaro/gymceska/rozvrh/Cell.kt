package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import cz.jaro.gymceska.ResponsiveText
import cz.jaro.gymceska.rozvrh.editor.Address
import cz.jaro.gymceska.rozvrh.editor.CellAddress
import cz.jaro.gymceska.rozvrh.editor.LessonAddress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Cell {
    val roomLike: String get() = ""
    val subjectLike: String get() = ""
    val teacherLike: String get() = ""
    val classLike: String get() = ""
    fun ColorScheme.backgroundColor(): Color = background
    fun ColorScheme.textColor(): Color = contentColorFor(backgroundColor())
    fun ColorScheme.subjectColor(): Color = textColor()
    val popupData: List<Pair<String, String>>? get() = null
    val isWholeDay: Boolean get() = false

    @Serializable
    sealed interface ForEdit : Cell

    @Serializable
    sealed interface ForEditNonHeader<T : Address> : ForEdit, NonHeader, HasClass {
        val address: T
        val isEdited: Boolean
    }

    @Serializable
    sealed interface NonHeader : Cell

    @Serializable
    sealed interface Data : HasClass

    @Serializable
    sealed interface HasClass : Cell {
        val klass: String get() = ""
    }
    @Serializable
    sealed interface HasRoom : Cell {
        val room: String get() = ""
    }
    @Serializable
    sealed interface HasTeacher : Cell {
        val teacher: String get() = ""
    }

    @Serializable
    sealed interface Abnormal : Data, NonHeader

    @Serializable
    @SerialName("Normal")
    data class Normal(
        val changeInfo: String? = null,
        override val room: String = "",
        val subject: String = "",
        val subjectName: String = "",
        override val teacher: String = "",
        val teacherName: String = "",
        override val klass: String = "",
        val group: String = "",
        val theme: String = "",
    ) : Data, NonHeader, HasRoom, HasTeacher {
        override val roomLike get() = room
        override val subjectLike get() = subject
        override val teacherLike get() = teacher
        override val classLike get() = klass and group.ifBlank { "" }
        override fun ColorScheme.backgroundColor() = if (changeInfo != null) errorContainer else background
        override fun ColorScheme.subjectColor() = if (theme.isNotBlank() && changeInfo == null) primary else textColor()
        override val popupData
            get() = pairsOfNotNull(
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
    @SerialName("Edit")
    data class Edit(
        override val room: String = "",
        val subject: String = "",
        override val teacher: String = "",
        override val klass: String = "",
        val group: String = "",
        override val address: CellAddress,
        override val isEdited: Boolean = false,
    ) : ForEditNonHeader<CellAddress>, HasTeacher, HasRoom {
        override val roomLike get() = room
        override val subjectLike get() = subject
        override val teacherLike get() = teacher
        override val classLike get() = klass and group.ifBlank { "" }
        override fun ColorScheme.backgroundColor() = if (isEdited) primaryContainer else background
    }

    @Serializable
    @SerialName("ST")
    data class ST(
        val subject: String = "ST",
        val subjectName: String = "Sportovní trénink",
        override val klass: String = "",
        val groups: List<STGroup>,
    ) : Data, NonHeader {
        @Serializable
        data class STGroup(
            val changeInfo: String? = null,
            val group: String = "",
            val theme: String = "",
            val teacher: String = "",
            val teacherName: String = "",
        )

        override val subjectLike get() = subject
        override val classLike get() = klass
        override fun ColorScheme.backgroundColor() = if (groups.any { it.changeInfo != null }) errorContainer else background
        override fun ColorScheme.subjectColor() =
            if (groups.any { it.theme.isNotBlank() } && groups.none { it.changeInfo != null }) primary else textColor()

        override val popupData
            get() = pairsOfNotNull(
                "Předmět:" to subjectName.takeUnless(String::isBlank),
                "Třída:" to klass.takeUnless(String::isBlank),
            ) + groups.flatMap {
                pairsOfNotNull(
                    "" to it.group,
                    "    Vyučující:" to it.teacherName + " (${it.teacher})",
                    "    Téma hodiny:" to it.theme.takeUnless(String::isBlank),
                    "    Změna:" to it.changeInfo?.takeUnless(String::isBlank),
                )
            }
    }

    @Serializable
    @SerialName("Header")
    data class Header(
        val title: String = "",
        val subtitle: String = "",
    ) : Cell, ForEdit {
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
            get() = pairsOfNotNull(
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
            get() = pairsOfNotNull(
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
    data object Empty : NonHeader

    @Serializable
    @SerialName("EmptyForEdit")
    data class EmptyForEdit(
        override val klass: String,
        override val address: LessonAddress,
        override val isEdited: Boolean = false,
    ) : ForEditNonHeader<LessonAddress>, ForEdit
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
    onSubjectClick: (() -> Unit)?,
    onRoomLongClick: (() -> Unit)?,
    icon: ImageVector?,
) {
    val onRoomClick = if (cell is Cell.HasRoom && cell.room.isNotBlank()) (onClick@{
        openTimetable(rooms.find<Timetable.Room> { cell.room == it.zkratka } ?: return@onClick)
    }) else null

    val onClassClick = if (cell is Cell.HasClass && cell.klass.isNotBlank()) (onClick@{
        openTimetable(classes.find<Timetable.Class> { cell.klass == it.zkratka } ?: return@onClick)
    }) else null

    val onTeacherClick = if (cell is Cell.HasTeacher && cell.teacher.isNotBlank()) (onClick@{
        openTimetable(teachers.find<Timetable.Teacher> { cell.teacher == it.zkratka } ?: return@onClick)
    }) else null

    val twoRowCell = height * LocalCellZoom.current < .7F
    val wholeRowCell = cell.isWholeDay && !forceOneColumnCells

    Surface(
        color = cell.backgroundColor(),
        contentColor = cell.textColor(),
    ) {
        when {
            wholeRowCell -> BaseCell(
                size = Size(10F, height),
                center = cell.subjectLike,
                centerStyle = TextStyle(
                    color = cell.subjectColor(),
                    fontWeight = FontWeight.Bold,
                ),
            )

            !twoRowCell -> BaseCell(
                size = Size(1F, height),
                center = cell.subjectLike,
                centerIcon = icon,
                bottomCenter = cell.teacherLike,
                onBottomCenterClick = onTeacherClick,
                topStart = cell.roomLike,
                onTopStartClick = onRoomClick,
                onTopStartLongClick = onRoomLongClick,
                topEnd = cell.classLike,
                onTopEndClick = onClassClick,
                centerStyle = TextStyle(
                    color = cell.subjectColor(),
                    fontWeight = FontWeight.Bold,
                ),
                onCenterClick = onSubjectClick,
            )

            twoRowCell -> BaseCell(
                size = Size(1F, height),
                bottomStart = cell.subjectLike,
                bottomStartIcon = icon,
                bottomEnd = cell.teacherLike,
                onBottomEndClick = onTeacherClick,
                topStart = cell.roomLike,
                onTopStartClick = onRoomClick,
                onTopStartLongClick = onRoomLongClick,
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

fun pairsOfNotNull(vararg pairs: Pair<String?, String?>?) = pairs.mapNotNull {
    if (it?.first == null || it.second == null) null
    else it.first!! to it.second!!
}

infix fun String.and(other: String) = if (this.isEmpty() || other.isEmpty()) "$this$other" else "$this $other"

val baseCellSize = 128.dp

val LocalCellZoom = compositionLocalOf { 1F }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseCell(
    size: Size,
    modifier: Modifier = Modifier,
    topStart: String? = null,
    topStartIcon: ImageVector? = null,
    topStartStyle: TextStyle = LocalTextStyle.current,
    onTopStartClick: (() -> Unit)? = null,
    onTopStartLongClick: (() -> Unit)? = null,
    topEnd: String? = null,
    topEndIcon: ImageVector? = null,
    topEndStyle: TextStyle = LocalTextStyle.current,
    onTopEndClick: (() -> Unit)? = null,
    onTopEndLongClick: (() -> Unit)? = null,
    bottomStart: String? = null,
    bottomStartIcon: ImageVector? = null,
    bottomStartStyle: TextStyle = LocalTextStyle.current,
    onBottomStartClick: (() -> Unit)? = null,
    onBottomStartLongClick: (() -> Unit)? = null,
    bottomEnd: String? = null,
    bottomEndIcon: ImageVector? = null,
    bottomEndStyle: TextStyle = LocalTextStyle.current,
    onBottomEndClick: (() -> Unit)? = null,
    onBottomEndLongClick: (() -> Unit)? = null,
    bottomCenter: String? = null,
    bottomCenterIcon: ImageVector? = null,
    bottomCenterStyle: TextStyle = LocalTextStyle.current,
    onBottomCenterClick: (() -> Unit)? = null,
    onBottomCenterLongClick: (() -> Unit)? = null,
    center: String? = null,
    centerIcon: ImageVector? = null,
    centerStyle: TextStyle = LocalTextStyle.current,
    onCenterClick: (() -> Unit)? = null,
    onCenterLongClick: (() -> Unit)? = null,
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
                Modifier
                    .size(cellWidth / columns, cellHeight / rows)
                    .padding(1.dp)
                    .combinedClickable(
                        enabled = onTopStartClick != null || onTopStartLongClick != null,
                        onLongClick = onTopStartLongClick ?: {},
                        onClick = onTopStartClick ?: {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (topStartIcon != null) Icon(topStartIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                    ResponsiveText(text = topStart, style = topStartStyle)
                }
            }
            if (topEnd != null) Box(
                Modifier
                    .size(cellWidth / columns, cellHeight / rows)
                    .padding(1.dp)
                    .combinedClickable(
                        enabled = onTopEndClick != null || onTopEndLongClick != null,
                        onLongClick = onTopEndLongClick ?: {},
                        onClick = onTopEndClick ?: {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (topEndIcon != null) Icon(topEndIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                    ResponsiveText(text = topEnd, style = topEndStyle)
                }
            }
        }
        if (center != null) Box(
            Modifier
                .size(cellWidth, cellHeight / rows)
                .padding(1.dp)
                .combinedClickable(
                    enabled = onCenterClick != null || onCenterLongClick != null,
                    onLongClick = onCenterLongClick ?: {},
                    onClick = onCenterClick ?: {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (centerIcon != null) Icon(centerIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                ResponsiveText(text = center, style = centerStyle)
            }
        }
        if (isBottom) Row(
            Modifier.size(cellWidth, cellHeight / rows),
        ) {
            val columns = listOf(bottomStart, bottomCenter, bottomEnd).count { it != null }
            if (bottomStart != null) Box(
                Modifier
                    .size(cellWidth / columns, cellHeight / rows)
                    .padding(1.dp)
                    .combinedClickable(
                        enabled = onBottomStartClick != null || onBottomStartLongClick != null,
                        onLongClick = onBottomStartLongClick ?: {},
                        onClick = onBottomStartClick ?: {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (bottomStartIcon != null) Icon(bottomStartIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                    ResponsiveText(text = bottomStart, style = bottomStartStyle)
                }
            }
            if (bottomCenter != null) Box(
                Modifier
                    .size(cellWidth / columns, cellHeight / rows)
                    .padding(1.dp)
                    .combinedClickable(
                        enabled = onBottomCenterClick != null || onBottomCenterLongClick != null,
                        onLongClick = onBottomCenterLongClick ?: {},
                        onClick = onBottomCenterClick ?: {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (bottomCenterIcon != null) Icon(bottomCenterIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                    ResponsiveText(text = bottomCenter, style = bottomCenterStyle)
                }
            }
            if (bottomEnd != null) Box(
                Modifier
                    .size(cellWidth / columns, cellHeight / rows)
                    .padding(1.dp)
                    .combinedClickable(
                        enabled = onBottomEndClick != null || onBottomEndLongClick != null,
                        onLongClick = onBottomEndLongClick ?: {},
                        onClick = onBottomEndClick ?: {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (bottomEndIcon != null) Icon(bottomEndIcon, null, Modifier.size(24.dp).padding(horizontal = 4.dp))
                    ResponsiveText(text = bottomEnd, style = bottomEndStyle)
                }
            }
        }
    }
}

fun Cell.Data.copy(klass: String = this.klass) = when (this) {
    is Cell.Normal -> copy(klass = klass)
    is Cell.Absent -> copy(klass = klass)
    is Cell.Removed -> copy(klass = klass)
    is Cell.DayOff -> copy(klass = klass)
    is Cell.ST -> copy(klass = klass)
}

fun <T : Address> Cell.ForEditNonHeader<T>.copy(
    klass: String = this.klass,
    address: T = this.address,
    isEdited: Boolean = this.isEdited,
) = when (this) {
    is Cell.Edit -> copy(klass = klass, address = address as CellAddress, isEdited = isEdited)
    is Cell.EmptyForEdit -> copy(klass = klass, address = address as LessonAddress, isEdited = isEdited)
}