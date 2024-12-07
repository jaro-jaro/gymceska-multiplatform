package cz.jaro.compose_dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import kotlin.js.JsName

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

    data class Basic<D>(
        val modifier: Modifier = Modifier,
        val onDismissed: (() -> Unit)? = null,
        val content: @Composable (AlertDialogState<D, Basic<D>>.() -> Unit)? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle<D>
}

interface AlertDialogState<D, S : AlertDialogStyle<D>> {
    fun hide()
    val isShown: Boolean
    var style: S
    var customState: D
}

interface AlertDialogStateInColumnScope<D, S : AlertDialogStyle<D>> : AlertDialogState<D, S>, ColumnScope

private fun <D, S : AlertDialogStyle<D>> AlertDialogStateInColumnScope(columnScope: ColumnScope, state: AlertDialogState<D, S>) =
    object : AlertDialogStateInColumnScope<D, S>, AlertDialogState<D, S> by state, ColumnScope by columnScope {}

fun <D, S : AlertDialogStyle<D>> AlertDialogState<D, S>.modifyStyle(update: S.() -> Unit) {
    style = style.apply(update)
}

fun <D, S : AlertDialogStyle<D>> AlertDialogState<D, S>.modifyState(update: D.() -> D) {
    customState = customState.update()
}

interface AlertDialogManager {
    fun <D, S : AlertDialogStyle<D>> show(style: S, state: D): AlertDialogState<D, S>
}

fun AlertDialogManager.show(style: AlertDialogStyle<Nothing?>) = show(style, null)

@JsName("createAlertDialogManager")
fun AlertDialogManager(): AlertDialogManager = AlertDialogManagerImpl()

private var managerField: AlertDialogManager? by mutableStateOf(null)
val dialogManager: AlertDialogManager
    get() = managerField ?: AlertDialogManager().also { managerField = it }

private class AlertDialogManagerImpl : AlertDialogManager {

    var dialogs: List<AlertDialogState<*, *>> by mutableStateOf(emptyList())
        private set

    private fun <D, S : AlertDialogStyle<D>> remove(state: AlertDialogState<D, S>) {
        dialogs = dialogs - state
    }

    override fun <D, S : AlertDialogStyle<D>> show(style: S, state: D): AlertDialogState<D, S> {
        val dialogState = object : AlertDialogState<D, S> {
            override fun hide() = remove(this)
            override val isShown: Boolean get() = this in dialogs
            override var style: S by mutableStateOf(style)
            override var customState: D by mutableStateOf(state)
            override fun toString() = "AlertDialogState(style=${this.style}, customState=$customState, isShown=$isShown)"
        }
        dialogs += dialogState
        println(dialogs)
        return dialogState
    }
}

fun <D> AlertDialogManager.show(
    state: D,
    confirmButton: @Composable AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
    icon: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    title: @Composable (AlertDialogState<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    content: @Composable (AlertDialogStateInColumnScope<D, AlertDialogStyle.Material<D>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Material(
        confirmButton, modifier, dismissButton, onDismissed, icon, title, content, properties
    ), state
)

fun AlertDialogManager.show(
    confirmButton: @Composable AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
    icon: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    title: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    content: @Composable (AlertDialogStateInColumnScope<Nothing?, AlertDialogStyle.Material<Nothing?>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(null, confirmButton, modifier, dismissButton, onDismissed, icon, title, content, properties)

fun AlertDialogManager.show(
    confirmButtonText: String,
    modifier: Modifier = Modifier,
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
    ), null
)

fun <D> AlertDialogManager.show(
    state: D,
    modifier: Modifier = Modifier,
    onDismissed: (() -> Unit)? = null,
    content: @Composable (AlertDialogState<D, AlertDialogStyle.Basic<D>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Basic(
        modifier, onDismissed, content, properties
    ), state
)

fun AlertDialogManager.show(
    modifier: Modifier = Modifier,
    onDismissed: (() -> Unit)? = null,
    content: @Composable (AlertDialogState<Nothing?, AlertDialogStyle.Basic<Nothing?>>.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(null, modifier, onDismissed, content, properties)

/**
 * Verze: 3.0
 */
@Composable
fun AlertDialog(
    manager: AlertDialogManager,
) {
    require(manager is AlertDialogManagerImpl)

    println(manager.dialogs)
    manager.dialogs.forEach { state ->
        ShowDialog(state)
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <D, S : AlertDialogStyle<D>> ShowDialog(state: AlertDialogState<D, S>) {
    when (val style: AlertDialogStyle<D> = state.style) {
        is AlertDialogStyle.Material<*> -> {
            style as AlertDialogStyle.Material<D>
            state as AlertDialogState<D, AlertDialogStyle.Material<D>>
            AlertDialog(
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
        }

        is AlertDialogStyle.Simple -> AlertDialog(
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
                    Text(
                        text = style.confirmButtonText
                    )
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
                        Text(
                            text = it
                        )
                    }
                }
            },
            icon = style.icon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                    )
                }
            },
            title = style.titleText?.let {
                {
                    Text(
                        text = it,
                    )
                }
            },
            text = style.contentText?.let {
                {
                    Text(
                        text = it,
                    )
                }
            },
            properties = style.properties
        )

        is AlertDialogStyle.Basic<*> -> BasicAlertDialog(
            onDismissRequest = {
                state.hide()
                style.onDismissed?.invoke()
            },
            modifier = style.modifier,
            content = {
                (style as AlertDialogStyle.Basic<D>).content?.invoke(state as AlertDialogState<D, AlertDialogStyle.Basic<D>>)
            },
            properties = style.properties
        )
    }
}