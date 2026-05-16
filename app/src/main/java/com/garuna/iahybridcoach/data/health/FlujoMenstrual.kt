package com.garuna.iahybridcoach.data.health

/* CLAUDE CODE:
 * Intensidad del flujo menstrual. Se persiste en Firestore como el nombre
 * del enum (LIGHT / MEDIUM / HEAVY). Cadena vacia en el campo significa
 * "no introducido" (campo opcional dentro del registro).
 */
enum class FlujoMenstrual(val label: String) {
    LIGHT("Ligero"),
    MEDIUM("Medio"),
    HEAVY("Abundante")
}
