package com.garuna.iahybridcoach.data.strava

import com.garuna.iahybridcoach.data.workouts.detail.HrSample
import com.garuna.iahybridcoach.data.workouts.detail.LapInfo
import com.garuna.iahybridcoach.data.workouts.detail.RoutePoint
import com.garuna.iahybridcoach.data.workouts.detail.SpeedSample
import com.garuna.iahybridcoach.data.workouts.detail.WorkoutDetail
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Wrapper de las Cloud Functions de Strava que devuelven datos al cliente
 * (a diferencia de StravaAuthRepository que solo gestiona el OAuth).
 *
 * Hoy expone `listActivities`. Mas adelante anyadiremos getActivityDetail
 * (streams, laps) para enriquecer el WorkoutDetail.
 *
 * Truco de reflexion: HttpsCallableResult marca `data` como visible-for-
 * testing en algunas versiones del SDK, lo que impide acceder al campo
 * con sintaxis Kotlin estandar. Usamos reflection java para leerlo. Si
 * en el futuro Firebase cambia el SDK y `data` queda publico, podemos
 * volver a `result.data` sin tocar nada mas.
 */
object StravaActivitiesRepository {

    suspend fun listActivities(daysBack: Int = 30): Result<List<StravaActivitySummary>> {
        return runCatching {
            val functions = FirebaseFunctions.getInstance("europe-west1")
            val result = functions.getHttpsCallable("stravaListActivities")
                .call(mapOf("daysBack" to daysBack))
                .await()

            val payload = readDataReflectively(result)
            @Suppress("UNCHECKED_CAST")
            val activities = (payload?.get("activities") as? List<Map<String, Any?>>).orEmpty()

            activities.map { it.toStravaActivitySummary() }
        }
    }

    /**
     * Trae el detalle pesado (laps + samples + ruta) de una actividad de
     * Strava. Llama a stravaGetActivityDetail. El resultado se puede pasar
     * directamente a WorkoutDetailRepository.saveDetail.
     */
    suspend fun getActivityDetail(activityId: Long): Result<WorkoutDetail> {
        return runCatching {
            val functions = FirebaseFunctions.getInstance("europe-west1")
            val result = functions.getHttpsCallable("stravaGetActivityDetail")
                .call(mapOf("activityId" to activityId))
                .await()
            val payload = readDataReflectively(result) ?: emptyMap()

            WorkoutDetail(
                hrSamples = (payload["hrSamples"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { it.toHrSample() }
                    .orEmpty(),
                speedSamples = (payload["speedSamples"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { it.toSpeedSample() }
                    .orEmpty(),
                routePoints = (payload["routePoints"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { it.toRoutePoint() }
                    .orEmpty(),
                laps = (payload["laps"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { it.toLapInfo() }
                    .orEmpty(),
                segments = emptyList()
            )
        }
    }

    private fun Map<String, Any?>.toHrSample(): HrSample = HrSample(
        timeOffsetMillis = (get("timeOffsetMillis") as? Number)?.toLong() ?: 0L,
        bpm = (get("bpm") as? Number)?.toInt() ?: 0
    )

    private fun Map<String, Any?>.toSpeedSample(): SpeedSample = SpeedSample(
        timeOffsetMillis = (get("timeOffsetMillis") as? Number)?.toLong() ?: 0L,
        mps = (get("mps") as? Number)?.toDouble() ?: 0.0
    )

    private fun Map<String, Any?>.toRoutePoint(): RoutePoint = RoutePoint(
        timeOffsetMillis = (get("timeOffsetMillis") as? Number)?.toLong() ?: 0L,
        lat = (get("lat") as? Number)?.toDouble() ?: 0.0,
        lon = (get("lon") as? Number)?.toDouble() ?: 0.0,
        altMeters = (get("altMeters") as? Number)?.toDouble()
    )

    private fun Map<String, Any?>.toLapInfo(): LapInfo = LapInfo(
        startOffsetMillis = (get("startOffsetMillis") as? Number)?.toLong() ?: 0L,
        durationSeconds = (get("durationSeconds") as? Number)?.toLong() ?: 0L,
        distanceMeters = (get("distanceMeters") as? Number)?.toDouble() ?: 0.0,
        avgHeartRate = (get("avgHeartRate") as? Number)?.toInt() ?: 0,
        avgSpeedMps = (get("avgSpeedMps") as? Number)?.toDouble() ?: 0.0
    )

    /** Lee el campo `data` de HttpsCallableResult evitando restricciones de visibilidad. */
    private fun readDataReflectively(result: Any): Map<String, Any?>? {
        val field = result.javaClass.getDeclaredField("data").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return field.get(result) as? Map<String, Any?>
    }

    private fun Map<String, Any?>.toStravaActivitySummary(): StravaActivitySummary {
        return StravaActivitySummary(
            id = (get("id") as? Number)?.toLong() ?: 0L,
            name = get("name") as? String ?: "",
            sportType = get("sportType") as? String ?: "",
            startDate = get("startDate") as? String ?: "",
            movingTimeSec = (get("movingTimeSec") as? Number)?.toLong() ?: 0L,
            distanceMeters = (get("distanceMeters") as? Number)?.toDouble() ?: 0.0,
            avgHeartRate = (get("avgHeartRate") as? Number)?.toInt() ?: 0,
            maxHeartRate = (get("maxHeartRate") as? Number)?.toInt() ?: 0,
            caloriesKcal = (get("caloriesKcal") as? Number)?.toDouble() ?: 0.0
        )
    }
}
