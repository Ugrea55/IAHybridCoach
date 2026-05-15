package com.garuna.iahybridcoach.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garuna.iahybridcoach.ui.calendario.CalendarioScreen
import com.garuna.iahybridcoach.ui.chat.ChatScreen
import com.garuna.iahybridcoach.ui.entrenamientos.EntrenamientosScreen
import com.garuna.iahybridcoach.ui.salud.SaludScreen

/* CLAUDE CODE:
 * Contenedor principal para usuarios autenticados. Monta:
 *   - TopAppBar con titulo de la app y boton de cerrar sesion.
 *   - NavigationBar inferior con las 4 secciones (definidas en BottomNavDestination).
 *   - NavHost que conmuta entre las pantallas segun la ruta activa.
 *
 * El callback onSignOut lo recibe de MainActivity, que es quien sabe como
 * limpiar el estado de sesion y volver al Login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "IAHybridCoach") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesion"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                BottomNavDestination.all.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            // CLAUDE CODE: patron oficial de Navigation Compose para
                            // tabs: vuelve al destino inicial conservando estado,
                            // evita apilar la misma tab varias veces y restaura el
                            // estado anterior si ya se habia visitado.
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(text = destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavDestination.Entrenamientos.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavDestination.Entrenamientos.route) {
                EntrenamientosScreen()
            }
            composable(BottomNavDestination.Salud.route) {
                SaludScreen()
            }
            composable(BottomNavDestination.Calendario.route) {
                CalendarioScreen()
            }
            composable(BottomNavDestination.Chat.route) {
                ChatScreen()
            }
        }
    }
}
