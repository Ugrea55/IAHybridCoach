package com.garuna.iahybridcoach.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.data.profile.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* CLAUDE CODE:
 * Estados de la pantalla de onboarding:
 * - Idle: formulario visible, el usuario puede editar.
 * - Saving: peticion en curso, deshabilitamos el boton "Guardar".
 * - Error: ha fallado el guardado, mostramos mensaje + permitimos reintentar.
 *
 * No hace falta un estado "Success": cuando saveProfile tiene exito, el
 * observador del perfil en MainActivity detecta el cambio y navega solo a
 * MainScaffold. Asi evitamos duplicar la fuente de verdad.
 */
sealed interface OnboardingUiState {
    object Idle : OnboardingUiState
    object Saving : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}

class OnboardingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving
            UserProfileRepository.saveProfile(profile)
                .onSuccess {
                    // CLAUDE CODE: no emitimos Success. MainActivity esta
                    // observando UserProfileRepository.observeProfile() y
                    // navegara al detectar el documento recien creado.
                    _uiState.value = OnboardingUiState.Idle
                }
                .onFailure { e ->
                    _uiState.value = OnboardingUiState.Error(
                        e.message ?: "No se pudo guardar el perfil"
                    )
                }
        }
    }
}
