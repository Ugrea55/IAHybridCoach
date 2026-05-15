package com.garuna.iahybridcoach.data.workouts

/* CLAUDE CODE:
 * Tipos de entrenamiento. El nombre del enum (FUERZA, CARDIO, ...) es lo que
 * se persiste en Firestore como String dentro del campo `tipo` de Workout.
 *
 * La propiedad label es solo para la UI (texto bonito en castellano). Asi,
 * si en el futuro cambiamos el label, los datos guardados no se rompen.
 */
enum class WorkoutType(val label: String) {
    FUERZA("Fuerza"),
    CARDIO("Cardio"),
    MOVILIDAD("Movilidad"),
    OTRO("Otro")
}
