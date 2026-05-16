package com.garuna.iahybridcoach.ui.salud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.health.HealthEntry
import com.garuna.iahybridcoach.data.health.HealthRepository
import com.garuna.iahybridcoach.data.profile.UserProfileRepository
import com.garuna.iahybridcoach.ui.common.SaveStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/* CLAUDE CODE:
 * ViewModel de Salud para el modelo "un registro por dia".
 *
 * Responsabilidades:
 * 1. Saber cual es la fecha de hoy (en hora local del dispositivo) y
 *    detectar si cambia mientras la app esta abierta.
 * 2. Cargar el documento del dia (o crear uno vacio en memoria si no existe).
 * 3. Aplicar cambios locales y guardarlos en Firestore con DEBOUNCE de 500ms
 *    para no bombardear la BD mientras el usuario mueve sliders.
 * 4. Exponer el estado del autosave (Idle/Saving/Saved/Error) para feedback.
 * 5. Saber si el formulario debe mostrar el bloque de ciclo menstrual segun
 *    el perfil del usuario.
 *
 * Limites conocidos:
 * - No hay listener en tiempo real sobre el doc del dia. Editar desde dos
 *   dispositivos a la vez = ultimo write gana. Aceptable para esta version.
 */
class SaludViewModel : ViewModel() {

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val SAVED_VISIBLE_MS = 2000L
        const val DATE_POLL_MS = 60_000L
    }

    private val _uiState = MutableStateFlow<SaludUiState>(SaludUiState.Loading)
    val uiState: StateFlow<SaludUiState> = _uiState.asStateFlow()

    private var currentDate: LocalDate = LocalDate.now()
    private var incluyeCiclo: Boolean = false
    private var saveJob: Job? = null

    init {
        observeProfile()
        loadEntryForDate(currentDate)
        startDateWatcher()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            UserProfileRepository.observeProfile()
                .catch { /* silencioso: si falla dejamos false */ }
                .collect { profile ->
                    incluyeCiclo = profile?.incluyeCicloMenstrual ?: false
                    // Si ya estamos en Editing, refrescamos el flag.
                    val current = _uiState.value
                    if (current is SaludUiState.Editing) {
                        _uiState.value = current.copy(incluyeCicloMenstrual = incluyeCiclo)
                    }
                }
        }
    }

    private fun loadEntryForDate(date: LocalDate) {
        currentDate = date
        _uiState.value = SaludUiState.Loading
        viewModelScope.launch {
            HealthRepository.getEntryForDate(date)
                .onSuccess { existing ->
                    val entry = existing ?: HealthEntry(fecha = startOfDayTimestamp(date))
                    _uiState.value = SaludUiState.Editing(
                        date = date,
                        entry = entry,
                        saveStatus = SaveStatus.Idle,
                        incluyeCicloMenstrual = incluyeCiclo
                    )
                }
                .onFailure { e ->
                    _uiState.value = SaludUiState.Error(
                        e.message ?: "No se pudo cargar el registro"
                    )
                }
        }
    }

    /* CLAUDE CODE:
     * Vigilante de cambio de dia. Si el usuario deja la app abierta sobre
     * Salud durante la medianoche, recargamos el doc para que el formulario
     * apunte al dia nuevo y aparezca vacio.
     */
    private fun startDateWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(DATE_POLL_MS)
                val today = LocalDate.now()
                if (today != currentDate) {
                    loadEntryForDate(today)
                }
            }
        }
    }

    /**
     * Recibe el HealthEntry actualizado desde la UI cada vez que el usuario
     * toca un campo. Aplica el cambio en memoria inmediatamente, lanza un
     * guardado con debounce y gestiona el estado de save status.
     */
    fun onEntryChanged(newEntry: HealthEntry) {
        val current = _uiState.value
        if (current !is SaludUiState.Editing) return

        _uiState.value = current.copy(entry = newEntry, saveStatus = SaveStatus.Saving)

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            HealthRepository.saveEntryForDate(currentDate, newEntry)
                .onSuccess {
                    val state = _uiState.value
                    if (state is SaludUiState.Editing) {
                        _uiState.value = state.copy(saveStatus = SaveStatus.Saved)
                    }
                    delay(SAVED_VISIBLE_MS)
                    val later = _uiState.value
                    if (later is SaludUiState.Editing && later.saveStatus is SaveStatus.Saved) {
                        _uiState.value = later.copy(saveStatus = SaveStatus.Idle)
                    }
                }
                .onFailure { e ->
                    val state = _uiState.value
                    if (state is SaludUiState.Editing) {
                        _uiState.value = state.copy(
                            saveStatus = SaveStatus.SaveError(
                                e.message ?: "Error al guardar"
                            )
                        )
                    }
                }
        }
    }

    /** Reintentar la carga inicial si fallo. */
    fun retry() {
        loadEntryForDate(LocalDate.now())
    }

    private fun startOfDayTimestamp(date: LocalDate): Timestamp {
        val millis = date.atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return Timestamp(Date(millis))
    }
}
