package com.garuna.iahybridcoach.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme

/* CLAUDE CODE:
 * Pantalla principal provisional. Hoy solo muestra el usuario logueado y un
 * boton para cerrar sesion, asi tenemos forma de probar el flujo completo
 * (login -> home -> logout -> login). En pasos posteriores se sustituira
 * por la navegacion real entre Entrenos / Salud / Calendario / Chat.
 *
 * Recibe nombre y email como strings (no FirebaseUser) para mantener este
 * archivo desacoplado de Firebase y previsualizable.
 */
@Composable
fun HomeScreen(
    userDisplayName: String?,
    userEmail: String?,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Hola, ${userDisplayName ?: "deportista"}",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        if (!userEmail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onSignOutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Cerrar sesion")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    IAHybridCoachTheme {
        HomeScreen(
            userDisplayName = "Unai",
            userEmail = "unai@ejemplo.com",
            onSignOutClick = {}
        )
    }
}
