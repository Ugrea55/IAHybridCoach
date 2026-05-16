package com.garuna.iahybridcoach.ui.profile

import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.ui.common.SaveStatus

/* CLAUDE CODE:
 * Estado de la pantalla de Perfil. Mismo patron que Salud (Loading -> Editing
 * con SaveStatus). Reutilizamos SaveStatus de ui/common.
 */
sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Editing(
        val profile: UserProfile,
        val saveStatus: SaveStatus
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
