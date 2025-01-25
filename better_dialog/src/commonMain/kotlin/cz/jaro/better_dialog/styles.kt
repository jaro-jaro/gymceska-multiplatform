package cz.jaro.better_dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties

fun AlertDialogManager.show(style: AlertDialogStyle<Nothing?>) = show(style, null)

fun <D> AlertDialogManager.showMaterial(
    state: D,
    confirmButton: @Composable AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit,
    modifier: Modifier = Modifier.Companion,
    dismissButton: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
    icon: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    title: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    content: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Material(
        confirmButton, modifier, dismissButton, onDismissed, icon, title, content, properties
    ), state
)

fun AlertDialogManager.showMaterial(
    confirmButton: @Composable AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit,
    modifier: Modifier = Modifier.Companion,
    dismissButton: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
    icon: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    title: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    content: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = showMaterial(null, confirmButton, modifier, dismissButton, onDismissed, icon, title, content, properties)

fun AlertDialogManager.showSimple(
    confirmButtonText: String,
    modifier: Modifier = Modifier.Companion,
    onConfirmed: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissed: (() -> Unit)? = null,
    icon: ImageVector? = null,
    titleText: String? = null,
    contentText: String? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Simple(
        confirmButtonText, modifier, onConfirmed, dismissButtonText, onDismissed, icon, titleText, contentText, properties
    )
)

@ExperimentalMaterial3Api
fun <D> AlertDialogManager.showBasic(
    state: D,
    modifier: Modifier = Modifier.Companion,
    onDismissed: (() -> Unit)? = null,
    content: @Composable (AlertDialogState<D, AlertDialogStyle.Basic<D>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Basic(
        modifier, onDismissed, content, properties
    ), state
)

@ExperimentalMaterial3Api
fun AlertDialogManager.showBasic(
    modifier: Modifier = Modifier.Companion,
    onDismissed: (() -> Unit)? = null,
    content: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Basic<Nothing?>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = showBasic(null, modifier, onDismissed, content, properties)