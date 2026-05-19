package com.garuna.iahybridcoach.data.strava

/* CLAUDE CODE:
 * Resumen de una actividad de Strava tal y como la devuelve nuestra Cloud
 * Function `stravaListActivities`. Es el subset minimo que necesitamos para
 * pintar la lista de import: id (para externalId + detalle posterior),
 * nombre, tipo, fecha inicio, duracion en movimiento, distancia, FC y kcal.
 *
 * El detalle pesado (laps, samples, GPS) se carga aparte cuando el usuario
 * abre la pantalla de detalle del workout importado.
 */
data class StravaActivitySummary(
    val id: Long,
    val name: String,
    val sportType: String,
    /** ISO 8601 UTC, ej. "2026-05-15T18:11:57Z". */
    val startDate: String,
    val movingTimeSec: Long,
    val distanceMeters: Double,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val caloriesKcal: Double
)
