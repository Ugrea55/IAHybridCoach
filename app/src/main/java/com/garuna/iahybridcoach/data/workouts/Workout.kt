package com.garuna.iahybridcoach.data.workouts

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/* CLAUDE CODE:
 * Modelo de un entrenamiento. Version simplificada (solo tipo, fecha y
 * descripcion). Si en el futuro queremos detalles como duracion, RPE,
 * ejercicios, etc., se anyadiran como campos opcionales para no romper los
 * documentos ya guardados.
 *
 * Se mapea con Firestore via toObject<Workout>() / set(workout). Para que la
 * deserializacion funcione TODOS los campos necesitan valor por defecto
 * (Firestore necesita un constructor sin argumentos).
 *
 * Decisiones:
 * - id se anota con @DocumentId: Firestore lo rellena automaticamente al leer
 *   con el ID del documento. Al escribir, este campo se ignora.
 * - tipo se guarda como String (nombre del enum) por compatibilidad: si
 *   refactorizamos WorkoutType, los datos antiguos siguen siendo legibles.
 * - fecha es Timestamp (tipo nativo de Firestore) para ordenar y consultar
 *   por rangos de forma eficiente.
 * - No incluimos userId: la ruta /users/{uid}/workouts/{id} ya implica el
 *   dueno y las reglas de seguridad lo garantizan.
 */
data class Workout(
    @DocumentId val id: String = "",
    val tipo: String = WorkoutType.OTRO.name,
    val fecha: Timestamp = Timestamp.now(),
    val descripcion: String = "",
    // CLAUDE CODE: origen del registro. "MANUAL" para entrenos creados
    // desde la app; "HEALTH_CONNECT" para los importados de Health Connect;
    // a futuro: "STRAVA", "GARMIN_DIRECT", etc. Util para diferenciar en
    // UI y para deduplicar.
    val source: String = "MANUAL",
    // CLAUDE CODE: ID en el sistema externo (p.ej. metadata.id de la
    // ExerciseSession de Health Connect). Para entrenos MANUAL queda vacio.
    // Usado para evitar reimportar el mismo entreno dos veces.
    val externalId: String = "",

    // CLAUDE CODE: campos de resumen que rellenamos desde Health Connect.
    // 0 / 0.0 = no disponible. La UI los muestra solo si > 0.
    val duracionMinutos: Int = 0,
    val distanciaMetros: Double = 0.0,
    val caloriasKcal: Double = 0.0,
    val fcMedia: Int = 0,
    val fcMaxima: Int = 0
) {
    /**
     * Version tipada del campo `tipo`. Tolerante a valores invalidos.
     *
     * @get:Exclude impide que Firestore serialice esta propiedad calculada
     * como si fuera un campo real.
     */
    @get:Exclude
    val tipoEnum: WorkoutType
        get() = runCatching { WorkoutType.valueOf(tipo) }
            .getOrDefault(WorkoutType.OTRO)
}
