package com.garuna.iahybridcoach.ui.calendario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/* CLAUDE CODE:
 * ViewModel del Calendario. Reutiliza el mismo Flow de WorkoutsRepository
 * (no duplicamos la query). Mantiene el listado en tiempo real e indexado
 * por fecha para que la UI no recorra todos los entrenos al pintar cada dia.
 */
class CalendarioViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<CalendarioUiState>(CalendarioUiState.Loading)
    val uiState: StateFlow<CalendarioUiState> = _uiState.asStateFlow()

    init {
        observeWorkouts()
    }

    private fun observeWorkouts() {
        viewModelScope.launch {
            WorkoutsRepository.observeWorkouts()
                .catch { e ->
                    _uiState.value = CalendarioUiState.Error(
                        e.message ?: "Error al cargar entrenamientos"
                    )
                }
                .collect { workouts ->
                    val byDate = workouts.groupBy { it.toLocalDate() }
                    _uiState.value = CalendarioUiState.Ready(byDate)
                }
        }
    }

    fun addWorkout(workout: Workout) {
        viewModelScope.launch {
            WorkoutsRepository.addWorkout(workout)
                .onFailure { e ->
                    _uiState.value = CalendarioUiState.Error(
                        e.message ?: "No se pudo guardar el entrenamiento"
                    )
                }
        }
    }

    /* CLAUDE CODE:
     * Convierte el Timestamp del Workout a LocalDate en la zona del
     * dispositivo. Asi un entreno guardado a las 23:30 hora local cuenta
     * en el dia local correcto y no se va al siguiente por culpa de UTC.
     */
    private fun Workout.toLocalDate(): LocalDate {
        return fecha.toDate()
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
