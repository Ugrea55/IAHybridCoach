package com.garuna.iahybridcoach.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.auth.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * Estados que puede tener la pantalla de login. Sealed interface para que el
 * compilador obligue a contemplar todos los casos al consumirlo en la UI.
 */
sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val user: FirebaseUser) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Recibe el ID token de Google obtenido por Credential Manager y delega
     * en AuthRepository para canjearlo por una sesion de Firebase Auth.
     */
    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            AuthRepository.signInWithGoogleIdToken(idToken)
                .onSuccess { user -> _uiState.value = LoginUiState.Success(user) }
                .onFailure { e ->
                    _uiState.value = LoginUiState.Error(
                        e.message ?: "Error desconocido al iniciar sesion"
                    )
                }
        }
    }

    /**
     * Reporta un error producido FUERA del flujo de Firebase, tipicamente
     * cancelacion del dialog de Credential Manager o falta de cuentas en el
     * dispositivo. Asi la UI puede mostrar el mensaje exacto al usuario.
     */
    fun onCredentialManagerError(message: String) {
        _uiState.value = LoginUiState.Error(message)
    }

    /* CLAUDE CODE:
     * Resetea el estado a Idle. Lo llama la UI despues de propagar un Success
     * al contenedor de navegacion. Sin esto, el ViewModel (que sobrevive a la
     * Activity) seguiria en Success y al volver a montar LoginScreen tras un
     * logout, el LaunchedEffect dispararia onLoginSuccess automaticamente,
     * provocando un bucle logout -> relogin instantaneo.
     */
    fun consumeSuccess() {
        if (_uiState.value is LoginUiState.Success) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
