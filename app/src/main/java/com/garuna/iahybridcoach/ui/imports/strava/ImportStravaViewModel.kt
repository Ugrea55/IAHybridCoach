package com.garuna.iahybridcoach.ui.imports.strava

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.strava.StravaActivitiesRepository
import com.garuna.iahybridcoach.data.strava.StravaActivitySummary
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutType
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import kotlin.math.abs

/* CLAUDE CODE:
 * ViewModel de "Importar de Strava".
 *
 * Flujo:
 *   1. Init -> Loading. Llama a stravaListActivities (CF).
 *   2. Para cada actividad cruzamos con los Workout existentes:
 *      - alreadyImported = ya hay un Workout con externalId="strava-{id}".
 *      - replacesHealthConnect = hay un Workout HC con fecha en +-5 min y
 *        no es ya un import de Strava. Al importar, lo borraremos.
 *   3. Estado Ready con los candidatos. Por defecto seleccionados todos los
 *      no ya importados.
 *   4. Al importar: borrar los HC marcados + insertar los Strava.
 *
 * Mapeo de sport_type -> WorkoutType:
 *   Run/TrailRun/VirtualRun/Walk/Hike -> CARDIO
 *   Ride/VirtualRide/MountainBikeRide/EBikeRide -> CARDIO
 *   Swim -> CARDIO
 *   WeightTraining/Crossfit -> FUERZA
 *   Yoga/Pilates/Stretching -> MOVILIDAD
 *   resto -> OTRO
 */
class ImportStravaViewModel : ViewModel() {

    private companion object {
        const val DAYS_BACK = 30
        const val DEDUP_WINDOW_SECONDS = 5L * 60L
    }

    private val _uiState =
        MutableStateFlow<ImportStravaUiState>(ImportStravaUiState.Loading)
    val uiState: StateFlow<ImportStravaUiState> = _uiState.asStateFlow()

    init {
        loadCandidates()
    }

    fun retry() = loadCandidates()

    private fun loadCandidates() {
        _uiState.value = ImportStravaUiState.Loading
        viewModelScope.launch {
            runCatching {
                val activitiesResult = StravaActivitiesRepository.listActivities(DAYS_BACK)
                val activities = activitiesResult.getOrThrow()
                val existingWorkouts = WorkoutsRepository.observeWorkouts().first()
                computeCandidates(activities, existingWorkouts)
            }.onSuccess { list ->
                _uiState.value = ImportStravaUiState.Ready(candidates = list)
            }.onFailure { e ->
                _uiState.value = ImportStravaUiState.Error(
                    e.message ?: "No se pudieron cargar las actividades de Strava"
                )
            }
        }
    }

    private fun computeCandidates(
        activities: List<StravaActivitySummary>,
        existing: List<Workout>
    ): List<StravaImportCandidate> {
        val byStravaId: Set<String> = existing
            .mapNotNull { it.externalId.takeIf { id -> id.startsWith("strava-") } }
            .toSet()
        val hcWorkouts: List<Workout> = existing.filter { it.source == "HEALTH_CONNECT" }

        return activities.map { act ->
            val stravaExtId = "strava-${act.id}"
            val alreadyImported = stravaExtId in byStravaId
            val startEpoch = parseIsoToEpochSeconds(act.startDate)
            val matchedHc: Workout? = if (!alreadyImported) {
                hcWorkouts.firstOrNull { hc ->
                    abs(hc.fecha.seconds - startEpoch) <= DEDUP_WINDOW_SECONDS
                }
            } else null

            StravaImportCandidate(
                activity = act,
                alreadyImported = alreadyImported,
                replacesHealthConnect = matchedHc != null,
                replacedHcWorkoutId = matchedHc?.id,
                selected = !alreadyImported
            )
        }
    }

    fun toggleSelection(candidate: StravaImportCandidate) {
        val state = _uiState.value as? ImportStravaUiState.Ready ?: return
        if (candidate.alreadyImported) return
        val updated = state.candidates.map {
            if (it.activity.id == candidate.activity.id) {
                it.copy(selected = !it.selected)
            } else it
        }
        _uiState.value = state.copy(candidates = updated)
    }

    fun selectAll() = setAllSelected(true)
    fun selectNone() = setAllSelected(false)

    private fun setAllSelected(value: Boolean) {
        val state = _uiState.value as? ImportStravaUiState.Ready ?: return
        val updated = state.candidates.map {
            if (it.alreadyImported) it else it.copy(selected = value)
        }
        _uiState.value = state.copy(candidates = updated)
    }

    fun importSelected() {
        val state = _uiState.value as? ImportStravaUiState.Ready ?: return
        val toImport = state.candidates.filter { it.selected && !it.alreadyImported }
        if (toImport.isEmpty()) return

        _uiState.value = state.copy(isImporting = true)
        viewModelScope.launch {
            var imported = 0
            var replacedHc = 0
            for (candidate in toImport) {
                // 1. Borrar el HC equivalente si lo hay (priorizar Strava).
                candidate.replacedHcWorkoutId?.let { hcId ->
                    WorkoutsRepository.deleteWorkout(hcId)
                        .onSuccess { replacedHc++ }
                }
                // 2. Crear el Workout de Strava.
                val workout = candidate.activity.toWorkout()
                WorkoutsRepository.addWorkout(workout)
                    .onSuccess { imported++ }
            }
            _uiState.value = ImportStravaUiState.Done(
                imported = imported,
                replacedHc = replacedHc
            )
        }
    }

    private fun StravaActivitySummary.toWorkout(): Workout {
        val startEpoch = parseIsoToEpochSeconds(startDate)
        val tipo = mapSportTypeToWorkoutType(sportType)
        return Workout(
            tipo = tipo.name,
            fecha = Timestamp(Date(startEpoch * 1000L)),
            descripcion = name.ifBlank { sportType },
            source = "STRAVA",
            externalId = "strava-$id",
            duracionMinutos = (movingTimeSec / 60L).toInt(),
            distanciaMetros = distanceMeters,
            caloriasKcal = caloriesKcal,
            fcMedia = avgHeartRate,
            fcMaxima = maxHeartRate
        )
    }

    private fun mapSportTypeToWorkoutType(sportType: String): WorkoutType {
        return when (sportType) {
            "Run", "TrailRun", "VirtualRun", "Walk", "Hike",
            "Ride", "VirtualRide", "MountainBikeRide", "EBikeRide", "GravelRide",
            "Swim", "Rowing", "VirtualRow", "Kayaking", "StandUpPaddling",
            "AlpineSki", "BackcountrySki", "NordicSki", "Snowboard",
            "Elliptical", "StairStepper" -> WorkoutType.CARDIO

            "WeightTraining", "Crossfit", "Workout" -> WorkoutType.FUERZA

            "Yoga", "Pilates", "Stretching" -> WorkoutType.MOVILIDAD

            else -> WorkoutType.OTRO
        }
    }

    private fun parseIsoToEpochSeconds(iso: String): Long {
        return runCatching { Instant.parse(iso).epochSecond }.getOrDefault(0L)
    }
}
