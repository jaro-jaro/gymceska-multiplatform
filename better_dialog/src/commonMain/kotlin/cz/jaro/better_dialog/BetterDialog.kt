package cz.jaro.better_dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface AlertDialogStateInColumnScope<D, S : AlertDialogStyle<D>> : AlertDialogState<D, S>, ColumnScope

private fun <D, S : AlertDialogStyle<D>> AlertDialogStateInColumnScope(columnScope: ColumnScope, state: AlertDialogState<D, S>) =
    object : AlertDialogStateInColumnScope<D, S>, AlertDialogState<D, S> by state, ColumnScope by columnScope {}

/**
 * Version: 1.2.6
 */
@Composable
fun AlertDialog(
    manager: AlertDialogManager,
) {
    manager.dialogs.forEach { state ->
        ShowDialog(state)
    }
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <D, S : AlertDialogStyle<D>> ShowDialog(state: AlertDialogState<D, S>) {
    when (val style: AlertDialogStyle<D> = state.style) {
        is AlertDialogStyle.Material<*> -> ShowMaterialDialog(
            state = state as AlertDialogState<D, AlertDialogStyle.Material<D>>,
            style = style as AlertDialogStyle.Material<D>
        )

        is AlertDialogStyle.Simple -> ShowSimpleDialog(
            state = state as AlertDialogState<Nothing?, AlertDialogStyle.Simple>,
            style = style
        )

        is AlertDialogStyle.Basic<*> -> ShowBasicDialog(
            state = state as AlertDialogState<D, AlertDialogStyle.Basic<D>>,
            style = style as AlertDialogStyle.Basic<D>
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <D> ShowBasicDialog(
    state: AlertDialogState<D, AlertDialogStyle.Basic<D>>,
    style: AlertDialogStyle.Basic<D>,
) = androidx.compose.material3.BasicAlertDialog(
    onDismissRequest = {
        state.hide()
        style.onDismissed?.invoke()
    },
    modifier = style.modifier,
    content = {
        style.content?.invoke(state)
    },
    properties = style.properties
)

@Composable
private fun ShowSimpleDialog(
    state: AlertDialogState<Nothing?, AlertDialogStyle.Simple>,
    style: AlertDialogStyle.Simple,
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = {
        state.hide()
        style.onDismissed?.invoke()
    },
    confirmButton = {
        TextButton(
            onClick = {
                state.hide()
                style.onConfirmed?.invoke()
            }
        ) {
            Text(text = style.confirmButtonText)
        }
    },
    modifier = style.modifier,
    dismissButton = style.dismissButtonText?.let {
        {
            TextButton(
                onClick = {
                    state.hide()
                    style.onDismissed?.invoke()
                }
            ) {
                Text(text = it)
            }
        }
    },
    icon = style.icon?.let {
        { Icon(imageVector = it, contentDescription = null) }
    },
    title = style.titleText?.let {
        { Text(text = it) }
    },
    text = style.contentText?.let {
        { Text(text = it) }
    },
    properties = style.properties
)

@Composable
private fun <D> ShowMaterialDialog(
    state: AlertDialogState<D, AlertDialogStyle.Material<D>>,
    style: AlertDialogStyle.Material<D>,
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = {
        state.hide()
        style.onDismissed?.invoke()
    },
    confirmButton = {
        style.confirmButton(state)
    },
    modifier = style.modifier,
    dismissButton = style.dismissButton?.let {
        { it(state) }
    },
    icon = style.icon?.let {
        { it(state) }
    },
    title = style.title?.let {
        { it(state) }
    },
    text = style.content?.let {
        {
            Column(
                Modifier.fillMaxWidth()
            ) {
                it(AlertDialogStateInColumnScope(this, state))
            }
        }
    },
    properties = style.properties
)