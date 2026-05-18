package com.garuna.iahybridcoach.ui.strava

/* CLAUDE CODE:
 * Estados de la pantalla de conexion con Strava.
 *
 * Disconnected: aun no se ha vinculado. Boton "Conectar".
 * Authorizing: se ha lanzado el navegador con la URL de OAuth de Strava.
 *   Esperamos al callback (deep link iahybridcoach://strava-callback).
 * Exchanging: tenemos el code, llamando a Cloud Function stravaExchangeCode.
 * Connected: ya tenemos tokens guardados. Mostramos athleteName si vino.
 * Error: fallo en la cadena. Mensaje + reintentar.
 */
sealed interface StravaConnectUiState {
    object Disconnected : StravaConnectUiState
    object Authorizing : StravaConnectUiState
    object Exchanging : StravaConnectUiState
    data class Connected(val athleteName: String?) : StravaConnectUiState
    data class Error(val message: String) : StravaConnectUiState
}
