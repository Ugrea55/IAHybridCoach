package com.garuna.iahybridcoach.data.strava

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/* CLAUDE CODE:
 * Bus en memoria para que MainActivity (que recibe el intent del deep link
 * iahybridcoach://strava-callback) pueda comunicar el resultado a la
 * pantalla de conexion Strava (que vive dentro de Compose y no tiene
 * acceso directo al intent).
 *
 * Es un singleton process-wide. Si el usuario mata la app y vuelve al
 * deep link, el sistema reabre MainActivity y este bus reaparece vacio,
 * que es lo correcto: la pantalla de conexion volveria a iniciar el flujo.
 *
 * BufferOverflow.DROP_OLDEST garantiza que si nadie escucha cuando llega
 * el evento, no se acumulan; lo importante es siempre el ultimo.
 */
sealed interface StravaCallbackEvent {
    data class Success(val code: String) : StravaCallbackEvent
    data class Failure(val error: String) : StravaCallbackEvent
}

object StravaCallbackBus {

    private val _events = MutableSharedFlow<StravaCallbackEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<StravaCallbackEvent> = _events

    fun publish(event: StravaCallbackEvent) {
        Log.d("StravaCallbackBus", "publish: $event")
        val emitted = _events.tryEmit(event)
        Log.d("StravaCallbackBus", "tryEmit result=$emitted")
    }

    /**
     * "Consume" el ultimo evento limpiando el replay buffer. Lo llaman las
     * pantallas tras procesar el callback para evitar que un siguiente
     * collector reciba el mismo evento dos veces.
     */
    fun consumeLast() {
        _events.resetReplayCache()
    }
}
