package com.garuna.iahybridcoach.ui.entrenamientos

import com.garuna.iahybridcoach.data.workouts.Workout

/* CLAUDE CODE:
 * Estados posibles de la pantalla de Entrenamientos. Sealed interface para
 * que el `when` que los consume sea exhaustivo y el compilador nos avise
 * si en el futuro anyadimos un caso.
 */
sealed interface EntrenamientosUiState {
    object Loading : EntrenamientosUiState
    object Empty : EntrenamientosUiState
    data class Success(val workouts: List<Workout>) : EntrenamientosUiState
    data class Error(val message: String) : EntrenamientosUiState
}
