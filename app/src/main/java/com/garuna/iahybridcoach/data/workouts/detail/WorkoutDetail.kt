package com.garuna.iahybridcoach.data.workouts.detail

/* CLAUDE CODE:
 * Detalle pesado de un entrenamiento (muestras de FC, ruta GPS, vueltas,
 * segmentos). Se guarda como documento separado en una subcoleccion:
 *   /users/{uid}/workouts/{wid}/details/data
 *
 * Razon de la subcoleccion: estos arrays pueden ocupar bastante (cientos
 * o miles de puntos). Manteniendolos fuera del documento principal del
 * Workout permite que la lista de entrenos cargue rapido y solo se baja
 * el detalle cuando el usuario lo pide.
 *
 * Limite tecnico: cada documento Firestore tiene 1MB. Para entrenos de
 * hasta ~3 horas cabe sobrado. Si en el futuro hay entrenos mas largos,
 * habra que partir en chunks o usar Firebase Storage.
 *
 * timeOffsetMillis (en sub-modelos) = milisegundos desde el inicio del
 * entreno. Mucho mas compacto que guardar el Instant absoluto en cada
 * muestra.
 */
data class WorkoutDetail(
    val hrSamples: List<HrSample> = emptyList(),
    val speedSamples: List<SpeedSample> = emptyList(),
    val routePoints: List<RoutePoint> = emptyList(),
    val laps: List<LapInfo> = emptyList(),
    val segments: List<SegmentInfo> = emptyList()
)

data class HrSample(
    val timeOffsetMillis: Long = 0L,
    val bpm: Int = 0
)

data class SpeedSample(
    /** Velocidad instantanea en metros por segundo. */
    val timeOffsetMillis: Long = 0L,
    val mps: Double = 0.0
)

data class RoutePoint(
    val timeOffsetMillis: Long = 0L,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** Altitud en metros. null si la fuente no la registra. */
    val altMeters: Double? = null
)

data class LapInfo(
    val startOffsetMillis: Long = 0L,
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    /** FC media en bpm. 0 = no disponible. */
    val avgHeartRate: Int = 0,
    /** Velocidad media en m/s. 0 = no disponible. */
    val avgSpeedMps: Double = 0.0
)

data class SegmentInfo(
    val startOffsetMillis: Long = 0L,
    val durationSeconds: Long = 0L,
    /** Codigo bruto del tipo segun Health Connect; lo mostramos como String. */
    val segmentTypeCode: Int = 0,
    val repetitions: Int = 0
)
