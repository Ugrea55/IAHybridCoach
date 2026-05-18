package com.garuna.iahybridcoach.ui.detail

import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.detail.WorkoutDetail

/* CLAUDE CODE:
 * Estados de la pantalla de detalle de un entrenamiento.
 *
 * Loading: cargando el documento del Workout principal.
 * Ready: ya tenemos el workout. `detail` puede ser:
 *   - null + workout.source == "MANUAL"   -> entreno manual, sin detalle.
 *   - null + workout.source == "HEALTH_CONNECT" + loadingFromHc=true ->
 *       estamos pidiendo el detalle a Health Connect en background.
 *   - WorkoutDetail no null -> ya tenemos detalle cacheado.
 *   - hcNoData=true -> se intento cargar de HC y no habia datos.
 * Error: fallo al cargar el workout.
 */
sealed interface WorkoutDetailUiState {
    object Loading : WorkoutDetailUiState
    data class Ready(
        val workout: Workout,
        val detail: WorkoutDetail?,
        val loadingFromHc: Boolean = false,
        val hcNoData: Boolean = false
    ) : WorkoutDetailUiState
    data class Error(val message: String) : WorkoutDetailUiState
}
