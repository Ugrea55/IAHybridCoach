package com.garuna.iahybridcoach.ui.strava

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/* CLAUDE CODE:
 * Pantalla "Conectar Strava".
 *
 * Es la primera pantalla que abre el flujo OAuth. La UI:
 *   - Disconnected: titulo + boton "Conectar Strava".
 *   - Authorizing: "Esperando autorizacion en Strava..." + boton para
 *     reintentar (por si el usuario cancelo y volvio).
 *   - Exchanging: spinner "Conectando..."
 *   - Connected: mensaje verde "Conectado como X" + boton "Volver".
 *   - Error: mensaje rojo + boton reintentar.
 *
 * NO maneja el deep link directamente: MainActivity captura el intent y
 * publica en StravaCallbackBus, que es donde escucha el ViewModel.
 */
@Composable
fun StravaConnectScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StravaConnectViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Conectar con Strava",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Para leer tus entrenos con FC por segmento, laps y ruta GPS desde tu reloj.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (val state = uiState) {
            StravaConnectUiState.Disconnected -> {
                Button(
                    onClick = {
                        val url = viewModel.buildAuthorizeUrl()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        viewModel.onAuthorizationStarted()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Conectar Strava")
                }
            }

            StravaConnectUiState.Authorizing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Esperando autorizacion en Strava...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = { viewModel.reset() }) {
                    Text("Cancelar")
                }
            }

            StravaConnectUiState.Exchanging -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Conectando con el servidor...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            is StravaConnectUiState.Connected -> {
                Text(
                    text = if (state.athleteName != null) {
                        "Conectado como ${state.athleteName}"
                    } else {
                        "Strava conectada correctamente"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver")
                }
            }

            is StravaConnectUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar")
                }
            }
        }
    }
}
