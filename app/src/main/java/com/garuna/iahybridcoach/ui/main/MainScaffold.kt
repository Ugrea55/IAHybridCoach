package com.garuna.iahybridcoach.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garuna.iahybridcoach.ui.calendario.CalendarioScreen
import com.garuna.iahybridcoach.ui.chat.ChatScreen
import com.garuna.iahybridcoach.ui.entrenamientos.EntrenamientosScreen
import com.garuna.iahybridcoach.ui.profile.ProfileScreen
import com.garuna.iahybridcoach.ui.salud.SaludScreen

private const val ROUTE_PROFILE = "profile"

/* CLAUDE CODE:
 * Contenedor principal para usuarios autenticados. Monta:
 *   - TopAppBar adaptativa segun la ruta:
 *       - en cualquier tab: titulo "IAHybridCoach" + icono de menu con
 *         dropdown (Perfil / Cerrar sesion).
 *       - en /profile: flecha de volver + titulo "Perfil".
 *   - NavigationBar inferior (oculta en /profile).
 *   - NavHost que conmuta entre las pantallas segun la ruta activa.
 *
 * Profile se trata como una "ruta secundaria" del mismo NavHost: no aparece
 * en la barra inferior, se navega a ella desde el menu, y la flecha de
 * volver hace popBackStack() para regresar a la tab anterior.
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
    val isProfileRoute = currentRoute == ROUTE_PROFILE

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isProfileRoute) {
                TopAppBar(
                    title = { Text("Perfil") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("IAHybridCoach") },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Menu"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Perfil") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(ROUTE_PROFILE) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cerrar sesion") },
                                onClick = {
                                    menuExpanded = false
                                    onSignOut()
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            // CLAUDE CODE: la barra inferior se oculta en /profile para que
            // se note que es una pantalla "secundaria" y no una tab.
            if (!isProfileRoute) {
                NavigationBar {
                    BottomNavDestination.all.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
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
            composable(ROUTE_PROFILE) {
                ProfileScreen()
            }
        }
    }
}
