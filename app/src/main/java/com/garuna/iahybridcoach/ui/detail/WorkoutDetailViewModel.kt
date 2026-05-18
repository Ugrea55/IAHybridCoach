package com.garuna.iahybridcoach.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.healthconnect.HealthConnectManager
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import com.garuna.iahybridcoach.data.workouts.detail.WorkoutDetail
import com.garuna.iahybridcoach.data.workouts.detail.WorkoutDetailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * ViewModel de la pantalla de detalle.
 *
 * El workoutId se inyecta via `load(workoutId)` desde el Composable.
 *
 * Flujo:
 *   1. Lee el Workout por id (Firestore one-shot).
 *   2. Empieza a observar el detalle (subcoleccion).
 *   3. Si el detalle viene null en la primera emision Y el workout viene de
 *      Health Connect, lanzamos una carga desde HC y guardamos. El listener
 *      reaccionara solo al nuevo dato.
 *   4. Para entrenos MANUAL, no hay detalle disponible. Quedamos en Ready
 *      con detail=null.
 */
class WorkoutDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnect = HealthConnectManager(application.applicationContext)

    private val _uiState =
        MutableStateFlow<WorkoutDetailUiState>(WorkoutDetailUiState.Loading)
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var hcFetchAttempted: Boolean = false

    fun load(workoutId: String) {
        loadJob?.cancel()
        hcFetchAttempted = false
        _uiState.value = WorkoutDetailUiState.Loading

        loadJob = viewModelScope.launch {
            val result = WorkoutsRepository.getWorkout(workoutId)
            val workout = result.getOrNull()
            if (workout == null) {
                _uiState.value = WorkoutDetailUiState.Error(
                    result.exceptionOrNull()?.message ?: "Entrenamiento no encontrado"
                )
                return@launch
            }

            // Estado inicial sin detalle aun.
            _uiState.value = WorkoutDetailUiState.Ready(workout = workout, detail = null)

            // Observar el detalle cacheado.
            WorkoutDetailRepository.observeDetail(workoutId).collect { detail ->
                val current = _uiState.value as? WorkoutDetailUiState.Ready ?: return@collect

                if (detail != null) {
                    _uiState.value = current.copy(
                        detail = detail,
                        loadingFromHc = false,
                        hcNoData = false
                    )
                } else {
                    // Aun no hay detalle. Si es de Health Connect y aun no
                    // hemos intentado descargarlo, lanzamos la fetch.
                    if (!hcFetchAttempted &&
                        workout.source == HealthConnectManager.SOURCE_HEALTH_CONNECT &&
                        workout.externalId.isNotBlank()
                    ) {
                        hcFetchAttempted = true
                        _uiState.value = current.copy(loadingFromHc = true)
                        fetchAndSaveFromHc(workoutId, workout)
                    } else {
                        _uiState.value = current.copy(loadingFromHc = false)
                    }
                }
            }
        }
    }

    private fun fetchAndSaveFromHc(workoutId: String, workout: Workout) {
        viewModelScope.launch {
            val fetched: WorkoutDetail? = runCatching {
                healthConnect.loadDetailForSession(workout.externalId)
            }.getOrNull()

            if (fetched == null) {
                val current = _uiState.value as? WorkoutDetailUiState.Ready
                if (current != null) {
                    _uiState.value = current.copy(loadingFromHc = false, hcNoData = true)
                }
                return@launch
            }

            // Si todo viene vacio (HC sin samples para este entreno), tambien
            // marcamos hcNoData para que la UI lo refleje.
            val allEmpty = fetched.hrSamples.isEmpty() &&
                fetched.speedSamples.isEmpty() &&
                fetched.routePoints.isEmpty() &&
                fetched.laps.isEmpty() &&
                fetched.segments.isEmpty()

            if (allEmpty) {
                val current = _uiState.value as? WorkoutDetailUiState.Ready
                if (current != null) {
                    _uiState.value = current.copy(loadingFromHc = false, hcNoData = true)
                }
                return@launch
            }

            // Guardar -> el observer del Flow detectara el cambio y actualizara.
            WorkoutDetailRepository.saveDetail(workoutId, fetched)
        }
    }
}
