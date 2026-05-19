package com.garuna.iahybridcoach.ui.imports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.healthconnect.HealthConnectAvailability
import com.garuna.iahybridcoach.data.healthconnect.HealthConnectManager
import com.garuna.iahybridcoach.data.workouts.WorkoutsRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Instant
import java.time.temporal.ChronoUnit

/* CLAUDE CODE:
 * ViewModel para la pantalla de importacion desde Health Connect.
 *
 * Es AndroidViewModel porque HealthConnectManager necesita Context para
 * inicializar el cliente. Si en el futuro el manager se inyecta con DI
 * (Hilt), esto pasara a ViewModel normal.
 *
 * Flujo de estados tipico:
 *   refreshAvailability()
 *     -> NotAvailable | NeedsUpdate -> fin (UI guia al user al Play Store)
 *     -> needs permission
 *           -> NeedsPermission (la UI dispara el dialog)
 *           -> tras conceder: loadCandidates() -> Ready
 *           -> tras denegar: NeedsPermission de nuevo
 *     -> ya con permiso -> loadCandidates() -> Ready
 *
 * Rango por defecto: ultimos 30 dias.
 */
class ImportWorkoutsViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnect = HealthConnectManager(application.applicationContext)

    private val _uiState =
        MutableStateFlow<ImportWorkoutsUiState>(ImportWorkoutsUiState.Loading)
    val uiState: StateFlow<ImportWorkoutsUiState> = _uiState.asStateFlow()

    private val _missingPermissions = MutableStateFlow<Set<String>>(emptySet())
    /** Permisos del set completo que aun no estan concedidos. */
    val missingPermissions: StateFlow<Set<String>> = _missingPermissions.asStateFlow()

    private val _stravaConnected = MutableStateFlow(false)
    /** True si el usuario ya hizo OAuth con Strava y tenemos tokens. */
    val stravaConnected: StateFlow<Boolean> = _stravaConnected.asStateFlow()

    val permissions: Set<String> get() = healthConnect.permissions

    init {
        refreshAvailability()
        observeStravaConnection()
    }

    /* CLAUDE CODE:
     * Observa /users/{uid}/integrations/strava para saber si hay tokens.
     * Si el documento existe, Strava esta conectado. Si se desconecta o
     * borra desde fuera, la UI lo detecta solo.
     */
    private fun observeStravaConnection() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            callbackFlow {
                val reg = Firebase.firestore
                    .collection("users").document(uid)
                    .collection("integrations").document("strava")
                    .addSnapshotListener { snap, err ->
                        if (err != null) {
                            close(err)
                            return@addSnapshotListener
                        }
                        trySend(snap?.exists() == true)
                    }
                awaitClose { reg.remove() }
            }
                .catch { _stravaConnected.value = false }
                .collect { exists -> _stravaConnected.value = exists }
        }
    }

    private fun refreshMissingPermissions() {
        viewModelScope.launch {
            val granted = healthConnect.grantedPermissions()
            _missingPermissions.value = healthConnect.permissions - granted
        }
    }

    /** Recalcula el estado inicial. Llamar tambien al volver del Play Store. */
    fun refreshAvailability() {
        viewModelScope.launch {
            when (healthConnect.availabilityStatus()) {
                HealthConnectAvailability.NotAvailable -> {
                    _uiState.value = ImportWorkoutsUiState.NotAvailable
                }
                HealthConnectAvailability.NeedsUpdate -> {
                    _uiState.value = ImportWorkoutsUiState.NeedsUpdate
                }
                HealthConnectAvailability.Available -> {
                    if (healthConnect.hasMinimumPermissions()) {
                        loadCandidates()
                    } else {
                        _uiState.value = ImportWorkoutsUiState.NeedsPermission
                    }
                }
            }
            refreshMissingPermissions()
        }
    }

    /**
     * La UI llama esto cuando el contrato de permisos devuelve el set
     * concedido. Si el usuario concedio el permiso ESENCIAL (lectura de
     * ExerciseSession) seguimos adelante, aunque haya rechazado los
     * extras (distancia, calorias, FC). Los entrenos importados saldran
     * sin esos campos.
     */
    fun onPermissionsResult(grantedPermissions: Set<String>) {
        if (grantedPermissions.containsAll(healthConnect.essentialPermissions)) {
            loadCandidates()
        } else {
            _uiState.value = ImportWorkoutsUiState.NeedsPermission
        }
        refreshMissingPermissions()
    }

    private fun loadCandidates() {
        _uiState.value = ImportWorkoutsUiState.Loading
        viewModelScope.launch {
            runCatching {
                val end = Instant.now()
                val start = end.minus(DAYS_BACK, ChronoUnit.DAYS)
                val sessions = healthConnect.readExerciseSessions(start, end)
                val existing = WorkoutsRepository.existingExternalIds()
                    .getOrElse { emptySet() }
                // CLAUDE CODE: prioridad Strava: si ya hay un Workout
                // source="STRAVA" en +-5 min, marcamos el de HC como ya
                // importado (no se reimporta).
                val stravaWorkouts = runCatching {
                    WorkoutsRepository.observeWorkouts().first()
                }.getOrElse { emptyList() }
                    .filter { it.source == "STRAVA" }

                sessions
                    .sortedByDescending { it.fecha.seconds }
                    .map { workout ->
                        val isDupByExtId = workout.externalId in existing
                        val isDupByStrava = !isDupByExtId &&
                            stravaWorkouts.any { stravaW ->
                                abs(stravaW.fecha.seconds - workout.fecha.seconds) <= DEDUP_WINDOW_SECONDS
                            }
                        val isDup = isDupByExtId || isDupByStrava
                        ImportCandidate(
                            workout = workout,
                            alreadyImported = isDup,
                            selected = !isDup
                        )
                    }
            }.onSuccess { list ->
                _uiState.value = ImportWorkoutsUiState.Ready(candidates = list)
            }.onFailure { e ->
                _uiState.value = ImportWorkoutsUiState.Error(
                    e.message ?: "Error al leer Health Connect"
                )
            }
        }
    }

    /** Toggle de seleccion. No tiene efecto sobre candidatos duplicados. */
    fun toggleSelection(candidate: ImportCandidate) {
        val state = _uiState.value as? ImportWorkoutsUiState.Ready ?: return
        if (candidate.alreadyImported) return
        val updated = state.candidates.map {
            if (it.workout.externalId == candidate.workout.externalId) {
                it.copy(selected = !it.selected)
            } else it
        }
        _uiState.value = state.copy(candidates = updated)
    }

    fun selectAll() = setAllSelected(true)
    fun selectNone() = setAllSelected(false)

    private fun setAllSelected(value: Boolean) {
        val state = _uiState.value as? ImportWorkoutsUiState.Ready ?: return
        val updated = state.candidates.map {
            if (it.alreadyImported) it else it.copy(selected = value)
        }
        _uiState.value = state.copy(candidates = updated)
    }

    /** Importa los candidatos seleccionados. */
    fun importSelected() {
        val state = _uiState.value as? ImportWorkoutsUiState.Ready ?: return
        val toImport = state.candidates.filter { it.selected && !it.alreadyImported }
        if (toImport.isEmpty()) return

        _uiState.value = state.copy(isImporting = true)
        viewModelScope.launch {
            var imported = 0
            for (candidate in toImport) {
                WorkoutsRepository.addWorkout(candidate.workout)
                    .onSuccess { imported++ }
            }
            _uiState.value = ImportWorkoutsUiState.Done(imported = imported)
        }
    }

    companion object {
        private const val DAYS_BACK = 30L
        private const val DEDUP_WINDOW_SECONDS = 5L * 60L
    }
}
