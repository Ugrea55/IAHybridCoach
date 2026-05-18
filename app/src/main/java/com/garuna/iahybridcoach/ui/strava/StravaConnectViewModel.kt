package com.garuna.iahybridcoach.ui.strava

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.strava.StravaAuthRepository
import com.garuna.iahybridcoach.data.strava.StravaCallbackBus
import com.garuna.iahybridcoach.data.strava.StravaCallbackEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * ViewModel de la pantalla de conexion Strava.
 *
 * Flujo:
 *   1. Estado Disconnected.
 *   2. Usuario pulsa "Conectar Strava".
 *   3. UI abre el navegador con buildAuthorizeUrl() y llama
 *      `onAuthorizationStarted()` -> estado Authorizing.
 *   4. Strava redirige al callback. Firebase Hosting rebota al deep link.
 *      MainActivity captura el intent y publica en StravaCallbackBus.
 *   5. Este ViewModel observa el bus. Al recibir Success(code), pasa a
 *      Exchanging y llama a StravaAuthRepository.exchangeCode.
 *   6. Si exito -> Connected. Si fallo -> Error.
 *
 * El listener del bus arranca en init() y vive mientras vive el ViewModel.
 */
class StravaConnectViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<StravaConnectUiState>(StravaConnectUiState.Disconnected)
    val uiState: StateFlow<StravaConnectUiState> = _uiState.asStateFlow()

    init {
        listenCallback()
    }

    private fun listenCallback() {
        viewModelScope.launch {
            Log.d(TAG, "Empezando a escuchar bus de Strava")
            StravaCallbackBus.events.collect { event ->
                Log.d(TAG, "Evento recibido del bus: $event")
                when (event) {
                    is StravaCallbackEvent.Success -> exchange(event.code)
                    is StravaCallbackEvent.Failure -> {
                        _uiState.value = StravaConnectUiState.Error(
                            "Strava devolvio error: ${event.error}"
                        )
                    }
                }
                StravaCallbackBus.consumeLast()
            }
        }
    }

    /** URL que la pantalla abrira en el navegador. */
    fun buildAuthorizeUrl(): String = StravaAuthRepository.buildAuthorizeUrl()

    /** La UI lo invoca tras abrir el navegador. */
    fun onAuthorizationStarted() {
        _uiState.value = StravaConnectUiState.Authorizing
    }

    private fun exchange(code: String) {
        Log.d(TAG, "exchange() llamado con code=${code.take(8)}...")
        viewModelScope.launch {
            Log.d(TAG, "Entrando en estado Exchanging")
            _uiState.value = StravaConnectUiState.Exchanging
            Log.d(TAG, "Llamando a StravaAuthRepository.exchangeCode...")
            val result = StravaAuthRepository.exchangeCode(code)
            Log.d(TAG, "exchangeCode resultado: success=${result.isSuccess} error=${result.exceptionOrNull()?.message}")
            result
                .onSuccess {
                    _uiState.value = StravaConnectUiState.Connected(null)
                }
                .onFailure { e ->
                    Log.e(TAG, "exchange fallo", e)
                    _uiState.value = StravaConnectUiState.Error(
                        e.message ?: "Error al canjear el code"
                    )
                }
        }
    }

    fun reset() {
        _uiState.value = StravaConnectUiState.Disconnected
    }

    companion object {
        private const val TAG = "StravaConnectVM"
    }
}
