package com.garuna.iahybridcoach.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutType
import com.google.firebase.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Date

/* CLAUDE CODE:
 * Encapsula el acceso a Health Connect. Tres responsabilidades:
 *   1. Saber si Health Connect esta DISPONIBLE en el dispositivo.
 *   2. Exponer el set de permisos que necesitamos.
 *   3. Leer ExerciseSessionRecord en un rango y mapearlo a nuestro Workout,
 *      enriqueciendo cada uno con resumen (distancia, calorias, FC media,
 *      FC maxima) via la API de Aggregate.
 *
 * El usuario aprueba los permisos uno a uno en el dialog nativo de Health
 * Connect. Si rechaza alguno (por ejemplo, FC), seguimos importando con los
 * campos disponibles; los que falten quedan a 0.
 */
class HealthConnectManager(private val context: Context) {

    /** Permisos que pedimos. Lectura de ejercicio + resumenes. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    /** Solo el de ExerciseSession es imprescindible para importar; el resto enriquece. */
    val essentialPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    fun availabilityStatus(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE ->
                HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.NeedsUpdate
            else ->
                HealthConnectAvailability.NotAvailable
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    /** Para considerar "tenemos lo minimo" basta con el de ExerciseSession. */
    suspend fun hasMinimumPermissions(): Boolean {
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(essentialPermissions)
    }

    /**
     * Lee las sesiones de ejercicio entre dos instantes, las convierte a
     * Workout y enriquece cada uno con resumenes (distancia, calorias, FC).
     * Para los permisos no concedidos, los campos correspondientes quedan a 0.
     */
    suspend fun readExerciseSessions(
        start: Instant,
        end: Instant
    ): List<Workout> {
        val client = client()
        val granted = client.permissionController.getGrantedPermissions()

        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val sessions = client.readRecords(request).records

        return sessions.map { session ->
            val stats = aggregateStatsForSession(client, session, granted)
            session.toWorkout(stats)
        }
    }

    /* CLAUDE CODE:
     * Llamada de agregacion para una sesion concreta. Solicitamos las metricas
     * para las que tenemos permiso. Si todo el bloque falla (rara vez ocurre,
     * pero por seguridad), devolvemos stats vacios.
     */
    private suspend fun aggregateStatsForSession(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
        granted: Set<String>
    ): SessionStats {
        val metrics = mutableSetOf<androidx.health.connect.client.aggregate.AggregateMetric<*>>()
        if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
            metrics += DistanceRecord.DISTANCE_TOTAL
        }
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
            metrics += TotalCaloriesBurnedRecord.ENERGY_TOTAL
        }
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            metrics += HeartRateRecord.BPM_AVG
            metrics += HeartRateRecord.BPM_MAX
        }

        if (metrics.isEmpty()) return SessionStats()

        val response: AggregationResult = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
                )
            )
        }.getOrElse { return SessionStats() }

        return SessionStats(
            distanceMeters = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
            caloriesKcal = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
            avgHeartRate = response[HeartRateRecord.BPM_AVG]?.toInt() ?: 0,
            maxHeartRate = response[HeartRateRecord.BPM_MAX]?.toInt() ?: 0
        )
    }

    private fun ExerciseSessionRecord.toWorkout(stats: SessionStats): Workout {
        val tipo = mapToWorkoutType(exerciseType)
        val durationMin = Duration.between(startTime, endTime).toMinutes().toInt()
        val actividad = specificActivityLabel(exerciseType)
        val titleOrFallback = title?.takeIf { it.isNotBlank() }
        val descripcion = titleOrFallback ?: actividad

        return Workout(
            tipo = tipo.name,
            fecha = Timestamp(Date.from(startTime)),
            descripcion = descripcion,
            source = SOURCE_HEALTH_CONNECT,
            externalId = metadata.id,
            duracionMinutos = durationMin,
            distanciaMetros = stats.distanceMeters,
            caloriasKcal = stats.caloriesKcal,
            fcMedia = stats.avgHeartRate,
            fcMaxima = stats.maxHeartRate
        )
    }

    private fun specificActivityLabel(exerciseType: Int): String {
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Carrera"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Cinta"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Bici estatica"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natacion (piscina)"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natacion (aguas abiertas)"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminar"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Senderismo"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Remo"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Remo (maquina)"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Eliptica"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Escaleras"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Escaladora"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Fuerza"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Levantamiento de pesas"
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calistenia"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Estiramientos"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxeo"
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Artes marciales"
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Futbol americano"
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "Futbol australiano"
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Futbol"
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Baloncesto"
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tenis"
            ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "Paddle/piragua"
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Baile"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "Respiracion"
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Otro entreno"
            else -> "Ejercicio"
        }
    }

    private fun mapToWorkoutType(exerciseType: Int): WorkoutType {
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> WorkoutType.CARDIO

            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WorkoutType.FUERZA

            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutType.MOVILIDAD

            else -> WorkoutType.OTRO
        }
    }

    companion object {
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
    }
}

enum class HealthConnectAvailability {
    Available,
    NeedsUpdate,
    NotAvailable
}

/* CLAUDE CODE: resumen agregado para una sesion concreta. 0 = no disponible. */
private data class SessionStats(
    val distanceMeters: Double = 0.0,
    val caloriesKcal: Double = 0.0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0
)
