package cz.jaro.better_dialog

interface AlertDialogState<D, S : AlertDialogStyle<D>> {
    fun hide()
    val isShown: Boolean
    var style: S
    var customState: D
}

fun <D, S : AlertDialogStyle<D>> AlertDialogState<D, S>.modifyStyle(update: S.() -> Unit) {
    style = style.apply(update)
}

fun <D, S : AlertDialogStyle<D>> AlertDialogState<D, S>.modifyState(update: D.() -> D) {
    customState = customState.update()
}