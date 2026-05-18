package com.garuna.iahybridcoach.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.ui.graphics.vector.ImageVector

/* CLAUDE CODE:
 * Define las 4 secciones de la barra inferior en un unico sitio: ruta de
 * Navigation Compose, etiqueta visible e icono. Centralizar esto evita
 * tener strings y configuracion repartidos por varios archivos.
 *
 * Sealed class porque el conjunto es cerrado: solo existen estas 4 entradas
 * y queremos que el compilador nos obligue a contemplarlas todas.
 */
sealed class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Entrenamientos : BottomNavDestination(
        route = "entrenamientos",
        label = "Entrenos",
        icon = Icons.Filled.FitnessCenter
    )

    object Salud : BottomNavDestination(
        route = "salud",
        label = "Salud",
        icon = Icons.Filled.MonitorHeart
    )

    object Calendario : BottomNavDestination(
        route = "calendario",
        label = "Calendario",
        icon = Icons.Filled.CalendarMonth
    )

    object Chat : BottomNavDestination(
        route = "chat",
        label = "Chat IA",
        icon = Icons.AutoMirrored.Filled.Chat
    )

    companion object {
        /* CLAUDE CODE:
         * Lista en el orden en que aparecen en la barra inferior.
         *
         * Usamos `by lazy` (no inicializacion directa) para evitar un bug
         * clasico de Kotlin: cuando el companion se inicializa antes que los
         * `object` anidados (Entrenamientos, Salud, ...), la lista acaba
         * conteniendo nulls. `lazy` difiere la construccion hasta la primera
         * lectura, momento en que los singletons ya estan inicializados.
         */
        val all: List<BottomNavDestination> by lazy {
            listOf(
                Entrenamientos,
                Salud,
                Calendario,
                Chat
            )
        }
    }
}
