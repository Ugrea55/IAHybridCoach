package com.garuna.iahybridcoach.ui.login

import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.R
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * LoginScreen: pantalla con estado (stateful). Obtiene el ViewModel, lanza
 * el flujo de Credential Manager (que necesita Context, por eso vive aqui
 * y no en el ViewModel) y delega la parte visual en LoginScreenContent.
 *
 * Separar stateful/stateless permite hacer @Preview sin Firebase ni Context
 * real, y facilita escribir tests de UI puros.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (FirebaseUser) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.web_client_id)

    // CLAUDE CODE: cuando el ViewModel pasa a Success disparamos el callback
    // hacia el contenedor (MainActivity / navegacion). Reseteamos el estado a
    // Idle ANTES de propagar para que un futuro re-montaje de LoginScreen
    // (tras un logout) no auto-dispare otra vez el callback con el Success
    // antiguo. Sin esto se forma un bucle logout -> relogin instantaneo.
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is LoginUiState.Success) {
            viewModel.consumeSuccess()
            onLoginSuccess(state.user)
        }
    }

    LoginScreenContent(
        uiState = uiState,
        onContinueWithGoogleClick = {
            scope.launch {
                requestGoogleIdToken(
                    context = context,
                    webClientId = webClientId,
                    onIdToken = viewModel::signInWithGoogleIdToken,
                    onError = viewModel::onCredentialManagerError
                )
            }
        },
        modifier = modifier
    )
}

/* CLAUDE CODE:
 * Parte puramente visual de la pantalla de login. No conoce ViewModel ni
 * Firebase: solo recibe estado y callbacks. Esto la hace previsualizable
 * y testeable.
 */
@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    onContinueWithGoogleClick: () -> Unit,
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
            text = "IAHybridCoach",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tu entrenador personal con IA",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (uiState is LoginUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onContinueWithGoogleClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Continuar con Google")
            }

            if (uiState is LoginUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* CLAUDE CODE:
 * Orquesta la llamada a Credential Manager para obtener el ID token de Google.
 * Esta funcion no sabe nada de UI: solo notifica por callbacks. Vive aqui en
 * lugar de en el ViewModel porque getCredential() necesita un Context.
 *
 * Posibles errores controlados:
 *   - GetCredentialCancellationException: el usuario cerro el dialog.
 *   - NoCredentialException: el dispositivo no tiene cuentas Google disponibles.
 *   - GoogleIdTokenParsingException: la respuesta no se pudo parsear.
 *   - GetCredentialException: cualquier otro fallo del sistema de credenciales.
 */
private suspend fun requestGoogleIdToken(
    context: Context,
    webClientId: String,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit
) {
    val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    val credentialManager = CredentialManager.create(context)

    try {
        val response = credentialManager.getCredential(context = context, request = request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            onIdToken(googleIdTokenCredential.idToken)
        } else {
            onError("Tipo de credencial inesperado: ${credential.type}")
        }
    } catch (e: GetCredentialCancellationException) {
        onError("Inicio de sesion cancelado")
    } catch (e: NoCredentialException) {
        onError("No hay cuentas de Google disponibles en el dispositivo")
    } catch (e: GoogleIdTokenParsingException) {
        onError("No se pudo leer el token de Google: ${e.message}")
    } catch (e: GetCredentialException) {
        onError("Error al obtener credencial: ${e.message}")
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenIdlePreview() {
    IAHybridCoachTheme {
        LoginScreenContent(
            uiState = LoginUiState.Idle,
            onContinueWithGoogleClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    IAHybridCoachTheme {
        LoginScreenContent(
            uiState = LoginUiState.Error("Inicio de sesion cancelado"),
            onContinueWithGoogleClick = {}
        )
    }
}
