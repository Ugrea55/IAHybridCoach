package com.garuna.iahybridcoach.ui.common

/* CLAUDE CODE:
 * Estado del autosave para pantallas con guardado automatico (Salud, Perfil,
 * y los que vengan). Reutilizable para no repetir el sealed interface en
 * cada modulo.
 *
 * Idle:    no hay cambios pendientes ni acabados de guardar.
 * Saving:  hay un cambio en debounce o en write a Firestore.
 * Saved:   ultimo guardado completado con exito (UI lo oculta despues
 *          de un par de segundos automaticamente).
 * SaveError: el ultimo guardado fallo.
 */
sealed interface SaveStatus {
    object Idle : SaveStatus
    object Saving : SaveStatus
    object Saved : SaveStatus
    data class SaveError(val message: String) : SaveStatus
}
