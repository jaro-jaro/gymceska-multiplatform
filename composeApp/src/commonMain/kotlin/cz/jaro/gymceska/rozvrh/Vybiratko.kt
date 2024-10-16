package cz.jaro.gymceska.rozvrh

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager

@ExperimentalMaterial3Api
@Composable
fun Vybiratko(
    value: Stalost,
    seznam: List<Stalost>,
    onClick: (Int, Stalost) -> Unit,
    modifier: Modifier = Modifier,
) = Vybiratko(
    value = value.nazev,
    seznam = seznam.map { it.nazev },
    onClick = { i, _ -> onClick(i, seznam[i]) },
    modifier = modifier,
    zaskrtavatko = { false },
)

@Composable
@ExperimentalMaterial3Api
fun Vybiratko(
    index: Int,
    seznam: List<String>,
    onClick: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    zaskrtavatko: ((String) -> Boolean)? = { it == seznam[index] },
) = Vybiratko(
    value = seznam[index],
    seznam = seznam,
    onClick = onClick,
    modifier = modifier,
    label = label,
    zaskrtavatko = zaskrtavatko,
)

@Composable
@ExperimentalMaterial3Api
fun Vybiratko(
    value: String,
    seznam: List<String>,
    onClick: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    zaskrtavatko: ((String) -> Boolean)? = { it == value },
    zavirat: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                focusManager.clearFocus()
            },
        ) {
            val zaskrtavatka = zaskrtavatko?.let { seznam.map(zaskrtavatko) }
            seznam.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onClick(i, option)
                        if (zavirat) {
                            expanded = false
                            focusManager.clearFocus()
                        }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    leadingIcon = zaskrtavatka?.let {
                        ({
                            if (zaskrtavatka[i]) Icon(Icons.Default.Check, null)
                        })
                    }
                )
            }
        }
    }
}