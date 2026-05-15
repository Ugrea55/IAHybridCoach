package com.garuna.iahybridcoach.ui.entrenamientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * ViewModel de la pantalla de Entrenamientos.
 *
 * En init se suscribe al Flow en tiempo real de WorkoutsRepository y mapea
 * cada emision al EntrenamientosUiState correspondiente (Empty si la lista
 * llega vacia, Success si trae elementos, Error si Firestore falla).
 *
 * El listener de Firestore se cancela automaticamente cuando el ViewModel
 * se destruye, porque la corrutina vive en viewModelScope.
 */
class EntrenamientosViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<EntrenamientosUiState>(EntrenamientosUiState.Loading)
    val uiState: StateFlow<EntrenamientosUiState> = _uiState.asStateFlow()

    init {
        observeWorkouts()
    }

    private fun observeWorkouts() {
        viewModelScope.launch {
            WorkoutsRepository.observeWorkouts()
                .catch { e ->
                    _uiState.value = EntrenamientosUiState.Error(
                        e.message ?: "Error al cargar entrenamientos"
                    )
                }
                .collect { workouts ->
                    _uiState.value = if (workouts.isEmpty()) {
                        EntrenamientosUiState.Empty
                    } else {
                        EntrenamientosUiState.Success(workouts)
                    }
                }
        }
    }

    /**
     * Anyade un nuevo entrenamiento. No tocamos _uiState al exito: la lista
     * se refresca sola via el listener en tiempo real. Solo emitimos un
     * Error si la escritura falla.
     */
    fun addWorkout(workout: Workout) {
        viewModelScope.launch {
            WorkoutsRepository.addWorkout(workout)
                .onFailure { e ->
                    _uiState.value = EntrenamientosUiState.Error(
                        e.message ?: "No se pudo guardar el entrenamiento"
                    )
                }
        }
    }

    /**
     * Borra un entrenamiento. La lista se refresca sola via el listener en
     * tiempo real; no hay que tocar _uiState manualmente.
     */
    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch {
            WorkoutsRepository.deleteWorkout(workoutId)
                .onFailure { e ->
                    _uiState.value = EntrenamientosUiState.Error(
                        e.message ?: "No se pudo borrar el entrenamiento"
                    )
                }
        }
    }
}
