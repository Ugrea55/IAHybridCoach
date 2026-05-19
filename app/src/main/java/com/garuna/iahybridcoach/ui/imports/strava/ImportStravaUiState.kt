package com.garuna.iahybridcoach.ui.imports.strava

import com.garuna.iahybridcoach.data.strava.StravaActivitySummary

/* CLAUDE CODE:
 * Estados de la pantalla "Importar de Strava".
 *
 * Loading: pidiendo la lista a la Cloud Function.
 * Ready: tenemos las actividades; muestra checkboxes y boton importar.
 * Done: import terminado, cuantos se importaron.
 * Error: fallo (sin conexion, token expirado, etc.).
 */
sealed interface ImportStravaUiState {
    object Loading : ImportStravaUiState
    data class Ready(
        val candidates: List<StravaImportCandidate>,
        val isImporting: Boolean = false
    ) : ImportStravaUiState
    data class Done(val imported: Int, val replacedHc: Int) : ImportStravaUiState
    data class Error(val message: String) : ImportStravaUiState
}

/* CLAUDE CODE:
 * `alreadyImported` = ya tenemos un Workout con externalId = "strava-{id}".
 * `replacesHealthConnect` = hay un Workout HC con fecha proxima (+-5 min)
 * que sera borrado al importar este de Strava (priorizamos Strava por
 * tener mas datos).
 */
data class StravaImportCandidate(
    val activity: StravaActivitySummary,
    val alreadyImported: Boolean,
    val replacesHealthConnect: Boolean,
    val replacedHcWorkoutId: String?,
    val selected: Boolean
)
