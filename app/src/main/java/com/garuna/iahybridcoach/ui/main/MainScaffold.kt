package com.garuna.iahybridcoach.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.garuna.iahybridcoach.ui.calendario.CalendarioScreen
import com.garuna.iahybridcoach.ui.chat.ChatScreen
import com.garuna.iahybridcoach.ui.detail.WorkoutDetailScreen
import com.garuna.iahybridcoach.ui.entrenamientos.EntrenamientosScreen
import com.garuna.iahybridcoach.ui.imports.ImportWorkoutsScreen
import com.garuna.iahybridcoach.ui.profile.ProfileScreen
import com.garuna.iahybridcoach.ui.salud.SaludScreen

private const val ROUTE_PROFILE = "profile"
private const val ROUTE_IMPORT_WORKOUTS = "import_workouts"
private const val ROUTE_WORKOUT_DETAIL = "workout_detail"
private const val ARG_WORKOUT_ID = "workoutId"

/* CLAUDE CODE:
 * Contenedor principal para usuarios autenticados.
 *
 * TopAppBar adaptativa segun la ruta:
 *   - en una tab generica:   titulo + menu (Perfil / Cerrar sesion).
 *   - en /entrenamientos:    igual + icono de IMPORTAR (Health Connect).
 *   - en /profile o /import: flecha de volver + titulo de pantalla.
 *
 * Bottom bar oculta en rutas secundarias (profile, import_workouts).
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
    val isImportRoute = currentRoute == ROUTE_IMPORT_WORKOUTS
    val isDetailRoute = currentRoute?.startsWith("$ROUTE_WORKOUT_DETAIL/") == true
    val isSecondaryRoute = isProfileRoute || isImportRoute || isDetailRoute
    val isEntrenosTab = currentRoute == BottomNavDestination.Entrenamientos.route

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            when {
                isProfileRoute -> SecondaryRouteTopBar(
                    title = "Perfil",
                    onBack = { navController.popBackStack() }
                )
                isImportRoute -> SecondaryRouteTopBar(
                    title = "Importar entrenos",
                    onBack = { navController.popBackStack() }
                )
                isDetailRoute -> SecondaryRouteTopBar(
                    title = "Entrenamiento",
                    onBack = { navController.popBackStack() }
                )
                else -> TopAppBar(
                    title = { Text("IAHybridCoach") },
                    actions = {
                        if (isEntrenosTab) {
                            IconButton(onClick = {
                                navController.navigate(ROUTE_IMPORT_WORKOUTS) {
                                    launchSingleTop = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "Importar entrenos"
                                )
                            }
                        }
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
            if (!isSecondaryRoute) {
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
                EntrenamientosScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate("$ROUTE_WORKOUT_DETAIL/$workoutId") {
                            launchSingleTop = true
                        }
                    }
                )
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
            composable(ROUTE_IMPORT_WORKOUTS) {
                ImportWorkoutsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_WORKOUT_DETAIL/{$ARG_WORKOUT_ID}",
                arguments = listOf(navArgument(ARG_WORKOUT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString(ARG_WORKOUT_ID).orEmpty()
                WorkoutDetailScreen(workoutId = id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryRouteTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
        }
    )
}
