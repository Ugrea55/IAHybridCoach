package com.garuna.iahybridcoach.ui.calendario

import com.garuna.iahybridcoach.data.workouts.Workout
import java.time.LocalDate

/* CLAUDE CODE:
 * Estado de la pantalla de Calendario.
 *
 * En Ready exponemos `workoutsByDate`, ya indexado por fecha local del
 * dispositivo, para que la UI pueda preguntar "que entrenos hay el dia X"
 * sin recorrer toda la lista en cada recomposicion.
 */
sealed interface CalendarioUiState {
    object Loading : CalendarioUiState
    data class Ready(
        val workoutsByDate: Map<LocalDate, List<Workout>>
    ) : CalendarioUiState
    data class Error(val message: String) : CalendarioUiState
}
