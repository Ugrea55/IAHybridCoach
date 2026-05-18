package com.garuna.iahybridcoach.ui.imports

import com.garuna.iahybridcoach.data.workouts.Workout

/* CLAUDE CODE:
 * Estados de la pantalla "Importar entrenos desde Health Connect".
 *
 * NotAvailable / NeedsUpdate: enseñamos un mensaje + boton para llevar al
 *   usuario al Play Store a instalar/actualizar Health Connect.
 * NeedsPermission: hay que disparar el dialog de Health Connect para
 *   pedir lectura de ExerciseSession.
 * Loading: leyendo y filtrando.
 * Ready: lista de candidatos con seleccion. duplicates son los que ya
 *   tenemos (los mostramos pero deshabilitados para que se vea que ya
 *   estan importados).
 * Importing: peticion de guardado en curso.
 * Done: importacion finalizada (mensaje + boton volver).
 * Error: mensaje generico + reintentar.
 */
sealed interface ImportWorkoutsUiState {
    object NotAvailable : ImportWorkoutsUiState
    object NeedsUpdate : ImportWorkoutsUiState
    object NeedsPermission : ImportWorkoutsUiState
    object Loading : ImportWorkoutsUiState
    data class Ready(
        val candidates: List<ImportCandidate>,
        val isImporting: Boolean = false
    ) : ImportWorkoutsUiState
    data class Done(val imported: Int) : ImportWorkoutsUiState
    data class Error(val message: String) : ImportWorkoutsUiState
}

/* CLAUDE CODE: candidato a importar. `alreadyImported` indica si ya existe
 * en Firestore (mismo externalId). Si lo es, en la UI mostramos pero
 * deshabilitado. `selected` solo aplica a no-importados. */
data class ImportCandidate(
    val workout: Workout,
    val alreadyImported: Boolean,
    val selected: Boolean
)
