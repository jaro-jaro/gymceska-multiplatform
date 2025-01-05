package cz.jaro.better_dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Deprecated("Use AlertDialogManager() instead", ReplaceWith("AlertDialogManager()"), DeprecationLevel.ERROR)
fun createAlertDialogManager() = AlertDialogManager()

@Deprecated("Use globalDialogManager instead", ReplaceWith("globalDialogManager"), DeprecationLevel.ERROR)
val dialogManager get() = globalDialogManager

private var managerField: AlertDialogManager? by mutableStateOf(null)
val globalDialogManager: AlertDialogManager
    get() = managerField ?: AlertDialogManager().also { managerField = it }

class AlertDialogManager {
    internal var dialogs: List<AlertDialogState<*, *>> by mutableStateOf(emptyList())
        private set

    private fun <D, S : AlertDialogStyle<D>> remove(state: AlertDialogState<D, S>) {
        dialogs = dialogs - state
    }

    fun <D, S : AlertDialogStyle<D>> show(style: S, state: D): AlertDialogState<D, S> {
        val dialogState = object : AlertDialogState<D, S> {
            override fun hide() = remove(this)
            override val isShown: Boolean get() = this in dialogs
            override var style: S by mutableStateOf(style)
            override var customState: D by mutableStateOf(state)
            override fun toString() = "AlertDialogState(style=${this.style}, customState=$customState, isShown=$isShown)"
        }
        dialogs += dialogState
        return dialogState
    }
}