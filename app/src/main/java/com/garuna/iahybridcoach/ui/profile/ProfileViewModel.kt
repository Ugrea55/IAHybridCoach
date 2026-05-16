package com.garuna.iahybridcoach.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.data.profile.UserProfileRepository
import com.garuna.iahybridcoach.ui.common.SaveStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * ViewModel de Perfil. Mismo patron que SaludViewModel:
 *  - Lectura one-shot del perfil al arrancar (no listener real-time para
 *    no sobrescribir ediciones locales).
 *  - Autosave con debounce de 500ms en cada cambio.
 *  - SaveStatus visible/invisible auto-gestionado.
 *
 * El usuario llega aqui SOLO si ya tiene perfil (MainActivity asegura el
 * onboarding antes de entrar a MainScaffold), asi que el caso "no hay
 * perfil" es un error inesperado.
 */
class ProfileViewModel : ViewModel() {

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val SAVED_VISIBLE_MS = 2000L
    }

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            runCatching { UserProfileRepository.observeProfile().first() }
                .onSuccess { profile ->
                    if (profile == null) {
                        _uiState.value = ProfileUiState.Error("Perfil no encontrado")
                    } else {
                        _uiState.value = ProfileUiState.Editing(profile, SaveStatus.Idle)
                    }
                }
                .onFailure { e ->
                    _uiState.value = ProfileUiState.Error(
                        e.message ?: "Error al cargar perfil"
                    )
                }
        }
    }

    /** La UI llama esto en cada cambio de campo. */
    fun onProfileChanged(newProfile: UserProfile) {
        val current = _uiState.value
        if (current !is ProfileUiState.Editing) return

        _uiState.value = current.copy(profile = newProfile, saveStatus = SaveStatus.Saving)

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            UserProfileRepository.saveProfile(newProfile)
                .onSuccess {
                    val state = _uiState.value
                    if (state is ProfileUiState.Editing) {
                        _uiState.value = state.copy(saveStatus = SaveStatus.Saved)
                    }
                    delay(SAVED_VISIBLE_MS)
                    val later = _uiState.value
                    if (later is ProfileUiState.Editing && later.saveStatus is SaveStatus.Saved) {
                        _uiState.value = later.copy(saveStatus = SaveStatus.Idle)
                    }
                }
                .onFailure { e ->
                    val state = _uiState.value
                    if (state is ProfileUiState.Editing) {
                        _uiState.value = state.copy(
                            saveStatus = SaveStatus.SaveError(
                                e.message ?: "Error al guardar"
                            )
                        )
                    }
                }
        }
    }

    fun retry() = loadProfile()
}
