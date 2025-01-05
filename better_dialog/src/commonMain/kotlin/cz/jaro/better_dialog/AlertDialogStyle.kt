package cz.jaro.better_dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties

sealed interface AlertDialogStyle<D> {
    data class Material<D>(
        val confirmButton: @Composable AlertDialogState<D, Material<D>>.() -> Unit,
        val modifier: Modifier = Modifier,
        val dismissButton: @Composable (AlertDialogState<D, Material<D>>.() -> Unit)? = null,
        val onDismissed: (() -> Unit)? = null,
        val icon: @Composable (AlertDialogState<D, Material<D>>.() -> Unit)? = null,
        val title: @Composable (AlertDialogState<D, Material<D>>.() -> Unit)? = null,
        val content: @Composable (AlertDialogStateInColumnScope<D, Material<D>>.() -> Unit)? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle<D>

    data class Simple(
        val confirmButtonText: String,
        val modifier: Modifier = Modifier,
        val onConfirmed: (() -> Unit)? = null,
        val dismissButtonText: String? = null,
        val onDismissed: (() -> Unit)? = null,
        val icon: ImageVector? = null,
        val titleText: String? = null,
        val contentText: String? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle<Nothing?>

    @ExperimentalMaterial3Api
    data class Basic<D>(
        val modifier: Modifier = Modifier,
        val onDismissed: (() -> Unit)? = null,
        val content: @Composable (AlertDialogState<D, Basic<D>>.() -> Unit)? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle<D>
}