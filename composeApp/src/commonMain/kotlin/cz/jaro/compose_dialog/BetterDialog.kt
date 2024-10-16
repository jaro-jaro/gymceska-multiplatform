package cz.jaro.compose_dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import kotlin.js.JsName

sealed interface AlertDialogStyle {
    data class Material(
        val confirmButton: @Composable context(AlertDialogScope) () -> Unit,
        val modifier: Modifier = Modifier,
        val dismissButton: @Composable (context(AlertDialogScope) () -> Unit)? = null,
        val onDismissed: (() -> Unit)? = null,
        val icon: @Composable (context(AlertDialogScope) () -> Unit)? = null,
        val title: @Composable (context(AlertDialogScope) () -> Unit)? = null,
        val content: @Composable (context(ColumnScope, AlertDialogScope) () -> Unit)? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle

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
    ) : AlertDialogStyle

    data class Basic(
        val modifier: Modifier = Modifier,
        val onDismissed: (() -> Unit)? = null,
        val content: @Composable (AlertDialogScope.() -> Unit)? = null,
        val properties: DialogProperties = DialogProperties(),
    ) : AlertDialogStyle
}

interface AlertDialogScope {
    fun hide()
}

interface AlertDialogState {
    fun show(style: AlertDialogStyle)
}
@JsName("createAlertDialogState")
fun AlertDialogState(): AlertDialogState = AlertDialogStateImpl()

private var stateField: AlertDialogState? = null
val dialogState: AlertDialogState
    get() = stateField ?: AlertDialogState().also { stateField = it }

private class AlertDialogStateImpl : AlertDialogState {

    var dialogStyles: List<AlertDialogStyle> by mutableStateOf(emptyList())
        private set

    override fun show(style: AlertDialogStyle) {
        this.dialogStyles += style
    }

    fun hideTopMost() {
        this.dialogStyles = this.dialogStyles.dropLast(1)
    }
}

fun AlertDialogState.show(
    confirmButton: @Composable context(AlertDialogScope) () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (context(AlertDialogScope) () -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
    icon: @Composable (context(AlertDialogScope) () -> Unit)? = null,
    title: @Composable (context(AlertDialogScope) () -> Unit)? = null,
    content: @Composable (context(ColumnScope, AlertDialogScope) () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Material(
        confirmButton, modifier, dismissButton, onDismissed, icon, title, content, properties
    )
)

fun AlertDialogState.show(
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
    )
)

fun AlertDialogState.show(
    modifier: Modifier = Modifier,
    onDismissed: (() -> Unit)? = null,
    content: @Composable (AlertDialogScope.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = show(
    AlertDialogStyle.Basic(
        modifier, onDismissed, content, properties
    )
)

/**
 * Verze: 2.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    state: AlertDialogState,
) {
    require(state is AlertDialogStateImpl)

    val scope: AlertDialogScope = remember {
        object : AlertDialogScope {
            override fun hide() = state.hideTopMost()
        }
    }
    state.dialogStyles.forEach { info ->
        when(info) {
            is AlertDialogStyle.Material -> androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    scope.hide()
                    info.onDismissed?.invoke()
                },
                confirmButton = {
                    info.confirmButton(scope)
                },
                modifier = info.modifier,
                dismissButton = info.dismissButton?.let {
                    { it(scope) }
                },
                icon = info.icon?.let {
                    { it(scope) }
                },
                title = info.title?.let {
                    { it(scope) }
                },
                text = info.content?.let {
                    {
                        Column(
                            Modifier.fillMaxWidth()
                        ) {
                            it(this, scope)
                        }
                    }
                },
                properties = info.properties
            )

            is AlertDialogStyle.Simple -> androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    scope.hide()
                    info.onDismissed?.invoke()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.hide()
                            info.onConfirmed?.invoke()
                        }
                    ) {
                        Text(
                            text = info.confirmButtonText
                        )
                    }
                },
                modifier = info.modifier,
                dismissButton = info.dismissButtonText?.let {
                    {
                        TextButton(
                            onClick = {
                                scope.hide()
                                info.onDismissed?.invoke()
                            }
                        ) {
                            Text(
                                text = it
                            )
                        }
                    }
                },
                icon = info.icon?.let {
                    {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                        )
                    }
                },
                title = info.titleText?.let {
                    {
                        Text(
                            text = it,
                        )
                    }
                },
                text = info.contentText?.let {
                    {
                        Text(
                            text = it,
                        )
                    }
                },
                properties = info.properties
            )

            is AlertDialogStyle.Basic -> BasicAlertDialog(
                onDismissRequest = {
                    scope.hide()
                    info.onDismissed?.invoke()
                },
                modifier = info.modifier,
                content = {
                    info.content?.invoke(scope)
                },
                properties = info.properties
            )
        }
    }
}