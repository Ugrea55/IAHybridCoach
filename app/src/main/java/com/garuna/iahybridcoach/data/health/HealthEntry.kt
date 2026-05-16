package com.garuna.iahybridcoach.data.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/* CLAUDE CODE:
 * Registro de un dato de salud en un dia. Se guarda como documento en
 *   /users/{uid}/healthEntries/{id}
 *
 * Diseno:
 * - Todos los campos son opcionales. Usamos 0 / "" / emptyList() como
 *   sentinela de "no introducido" en lugar de tipos nullable. Es mas simple
 *   con Firestore (no hay que preocuparse de Long? -> Int?) y la UI
 *   distinguira facilmente.
 * - Los campos menstruales solo se rellenan si el perfil del usuario indica
 *   genero != HOMBRE. En el formulario los ocultaremos directamente.
 * - Permitimos varios registros por dia: no imponemos unicidad por fecha. La
 *   UI mostrara el ultimo o todos segun decidamos en sub-paso 7.3.
 */
data class HealthEntry(
    @DocumentId val id: String = "",
    val fecha: Timestamp = Timestamp.now(),

    // Generales
    val calidadSueno: Int = 0,
    val nivelDolor: Int = 0,
    val lugarDolor: String = "",
    val motivacion: Int = 0,
    val energia: Int = 0,

    // Solo si genero != HOMBRE
    val menstruacion: Boolean = false,
    val flujoMenstrual: String = "",
    val sintomasMenstruales: List<String> = emptyList()
) {
    /** Acceso tipado al flujo. Null si esta vacio o no es valido. */
    @get:Exclude
    val flujoEnum: FlujoMenstrual?
        get() = if (flujoMenstrual.isBlank()) null
        else runCatching { FlujoMenstrual.valueOf(flujoMenstrual) }.getOrNull()

    /** Conjunto tipado de sintomas. Ignora valores invalidos. */
    @get:Exclude
    val sintomasEnum: Set<SintomaMenstrual>
        get() = sintomasMenstruales.mapNotNull { nombre ->
            runCatching { SintomaMenstrual.valueOf(nombre) }.getOrNull()
        }.toSet()
}
