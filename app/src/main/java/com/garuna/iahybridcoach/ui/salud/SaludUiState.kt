package com.garuna.iahybridcoach.ui.salud

import com.garuna.iahybridcoach.data.health.HealthEntry
import com.garuna.iahybridcoach.ui.common.SaveStatus
import java.time.LocalDate

/* CLAUDE CODE:
 * Estado de la pantalla de Salud. SaveStatus vive en ui/common porque tambien
 * lo usa la pantalla de Perfil.
 */
sealed interface SaludUiState {
    object Loading : SaludUiState
    data class Editing(
        val date: LocalDate,
        val entry: HealthEntry,
        val saveStatus: SaveStatus,
        val incluyeCicloMenstrual: Boolean
    ) : SaludUiState
    data class Error(val message: String) : SaludUiState
}
