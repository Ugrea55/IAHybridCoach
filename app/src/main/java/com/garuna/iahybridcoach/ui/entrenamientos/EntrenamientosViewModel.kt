package com.garuna.iahybridcoach.ui.entrenamientos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.healthconnect.HealthConnectAvailability
import com.garuna.iahybridcoach.data.healthconnect.HealthConnectManager
import com.garuna.iahybridcoach.data.strava.StravaActivitiesRepository
import com.garuna.iahybridcoach.data.strava.StravaActivitySummary
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutType
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.abs

/* CLAUDE CODE:
 * ViewModel de la pantalla de Entrenamientos.
 *
 * Suscripcion en tiempo real al Flow de Firestore + auto-sync silencioso
 * al arrancar: trae nuevos workouts de Strava (via Cloud Function) y
 * Health Connect (si hay permisos), con dedup por externalId y por
 * proximidad de tiempo entre fuentes.
 *
 * Como necesitamos Context para HealthConnectManager, este es un
 * AndroidViewModel.
 */
class EntrenamientosViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val SYNC_DAYS_BACK = 30L
        const val DEDUP_WINDOW_SECONDS = 5L * 60L
    }

    private val healthConnect = HealthConnectManager(application.applicationContext)

    private val _uiState =
        MutableStateFlow<EntrenamientosUiState>(EntrenamientosUiState.Loading)
    val uiState: StateFlow<EntrenamientosUiState> = _uiState.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    /** True mientras el auto-sync esta en curso (banner "Sincronizando..."). */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        observeWorkouts()
        // Auto-sync en background al arrancar el VM (sin bloquear la UI).
        viewModelScope.launch { syncAll() }
    }

    /** Re-lanza el sync. Util para botones manuales o pull-to-refresh. */
    fun forceSync() {
        viewModelScope.launch { syncAll() }
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

    // ---- Sync ----

    private suspend fun syncAll() {
        if (_syncing.value) return
        _syncing.value = true
        try {
            // Las dos fuentes se ejecutan en serie para evitar carreras en
            // la lectura/escritura de Firestore (deduplican entre si).
            val existing = WorkoutsRepository.observeWorkouts().first()
            val afterStrava = syncStrava(existing)
            syncHealthConnect(afterStrava)
        } catch (_: Throwable) {
            // CLAUDE CODE: silencioso. Los errores del sync se ignoran a
            // proposito: la app sigue usable con los datos que ya hay.
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun syncStrava(existing: List<Workout>): List<Workout> {
        if (!isStravaConnected()) return existing
        val activitiesResult = StravaActivitiesRepository.listActivities(SYNC_DAYS_BACK.toInt())
        val activities = activitiesResult.getOrNull() ?: return existing
        if (activities.isEmpty()) return existing

        val existingExternalIds = existing.map { it.externalId }.toSet()
        var current = existing.toMutableList()

        for (act in activities) {
            val extId = "strava-${act.id}"
            if (extId in existingExternalIds) continue
            val newWorkout = act.toWorkout()
            // Dedup contra HC: borrar el HC equivalente.
            val hcMatch = current.firstOrNull { existing ->
                existing.source == HealthConnectManager.SOURCE_HEALTH_CONNECT &&
                    abs(existing.fecha.seconds - newWorkout.fecha.seconds) <= DEDUP_WINDOW_SECONDS
            }
            hcMatch?.let {
                WorkoutsRepository.deleteWorkout(it.id)
                current.remove(it)
            }
            val addResult = WorkoutsRepository.addWorkout(newWorkout)
            addResult.getOrNull()?.let { id ->
                current.add(newWorkout.copy(id = id))
            }
        }
        return current
    }

    private suspend fun syncHealthConnect(existing: List<Workout>): List<Workout> {
        if (healthConnect.availabilityStatus() != HealthConnectAvailability.Available) return existing
        if (!healthConnect.hasMinimumPermissions()) return existing
        val end = Instant.now()
        val start = end.minus(SYNC_DAYS_BACK, ChronoUnit.DAYS)
        val sessions = runCatching { healthConnect.readExerciseSessions(start, end) }
            .getOrDefault(emptyList())
        if (sessions.isEmpty()) return existing

        val existingExternalIds = existing.map { it.externalId }.toSet()
        val stravaWorkouts = existing.filter { it.source == "STRAVA" }
        var current = existing.toMutableList()

        for (s in sessions) {
            if (s.externalId in existingExternalIds) continue
            // Si hay un Strava equivalente, NO importamos el HC (Strava manda).
            val stravaCollision = stravaWorkouts.any { strava ->
                abs(strava.fecha.seconds - s.fecha.seconds) <= DEDUP_WINDOW_SECONDS
            }
            if (stravaCollision) continue
            val addResult = WorkoutsRepository.addWorkout(s)
            addResult.getOrNull()?.let { id ->
                current.add(s.copy(id = id))
            }
        }
        return current
    }

    private suspend fun isStravaConnected(): Boolean {
        val uid = Firebase.auth.currentUser?.uid ?: return false
        return runCatching {
            Firebase.firestore.collection("users").document(uid)
                .collection("integrations").document("strava")
                .get().await().exists()
        }.getOrDefault(false)
    }

    private fun StravaActivitySummary.toWorkout(): Workout {
        val startEpoch = runCatching { Instant.parse(startDate).epochSecond }.getOrDefault(0L)
        return Workout(
            tipo = mapSportType(sportType).name,
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

    private fun mapSportType(sportType: String): WorkoutType = when (sportType) {
        "Run", "TrailRun", "VirtualRun", "Walk", "Hike",
        "Ride", "VirtualRide", "MountainBikeRide", "EBikeRide", "GravelRide",
        "Swim", "Rowing", "VirtualRow", "Kayaking", "StandUpPaddling",
        "AlpineSki", "BackcountrySki", "NordicSki", "Snowboard",
        "Elliptical", "StairStepper" -> WorkoutType.CARDIO
        "WeightTraining", "Crossfit", "Workout" -> WorkoutType.FUERZA
        "Yoga", "Pilates", "Stretching" -> WorkoutType.MOVILIDAD
        else -> WorkoutType.OTRO
    }

    // ---- CRUD que ya tenia ----

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

    fun updateWorkout(workout: Workout) {
        viewModelScope.launch {
            WorkoutsRepository.updateWorkout(workout)
                .onFailure { e ->
                    _uiState.value = EntrenamientosUiState.Error(
                        e.message ?: "No se pudo actualizar el entrenamiento"
                    )
                }
        }
    }

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

    fun deleteMany(workoutIds: Collection<String>) {
        if (workoutIds.isEmpty()) return
        viewModelScope.launch {
            for (id in workoutIds) {
                WorkoutsRepository.deleteWorkout(id)
                    .onFailure { e ->
                        _uiState.value = EntrenamientosUiState.Error(
                            e.message ?: "Error al borrar"
                        )
                        return@launch
                    }
            }
        }
    }
}
