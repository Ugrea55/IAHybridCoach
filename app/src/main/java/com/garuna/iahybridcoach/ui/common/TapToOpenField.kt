package com.garuna.iahybridcoach.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/* CLAUDE CODE:
 * Campo de texto que se comporta como un boton: muestra el valor (o vacio)
 * y al pulsarlo invoca onClick. Sirve tipicamente para abrir un DatePicker.
 *
 * Resuelve el bug de usar OutlinedTextField con readOnly + clickable encima
 * (el campo capturaba el foco y se comia el click). Escuchamos las
 * interactions del InteractionSource del propio TextField para detectar el
 * tap desde dentro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapToOpenField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                onClick()
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth()
    )
}
