package com.garuna.iahybridcoach.data.profile

/* CLAUDE CODE:
 * Genero del usuario. El nombre del enum se persiste en Firestore como
 * String dentro del campo `genero` de UserProfile.
 *
 * La distincion importa para decidir que campos extra mostrar en el
 * formulario de Salud (ciclo menstrual aplica a MUJER y OTRO).
 */
enum class Genero(val label: String) {
    HOMBRE("Hombre"),
    MUJER("Mujer"),
    OTRO("Otro")
}
