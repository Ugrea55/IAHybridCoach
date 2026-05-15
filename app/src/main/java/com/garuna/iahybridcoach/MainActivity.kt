package com.garuna.iahybridcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garuna.iahybridcoach.data.auth.AuthRepository
import com.garuna.iahybridcoach.ui.home.HomeScreen
import com.garuna.iahybridcoach.ui.login.LoginScreen
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IAHybridCoachTheme {
                // CLAUDE CODE: navegacion manual minima. Mientras solo tengamos
                // dos pantallas (Login y Home) no merece la pena meter una
                // libreria de navegacion.
                //
                // El estado inicial se rellena con AuthRepository.currentUser:
                // si Firebase ya tiene sesion persistida en el dispositivo,
                // arrancamos directamente en Home. Si no, en Login.
                var currentUser by remember {
                    mutableStateOf<FirebaseUser?>(AuthRepository.currentUser)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)
                    val user = currentUser
                    if (user == null) {
                        LoginScreen(
                            onLoginSuccess = { signedInUser ->
                                currentUser = signedInUser
                            },
                            modifier = screenModifier
                        )
                    } else {
                        HomeScreen(
                            userDisplayName = user.displayName,
                            userEmail = user.email,
                            onSignOutClick = {
                                AuthRepository.signOut()
                                currentUser = null
                            },
                            modifier = screenModifier
                        )
                    }
                }
            }
        }
    }
}
