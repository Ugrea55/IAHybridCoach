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
import com.garuna.iahybridcoach.ui.login.LoginScreen
import com.garuna.iahybridcoach.ui.main.MainScaffold
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IAHybridCoachTheme {
                // CLAUDE CODE: navegacion de alto nivel: sesion vs no sesion.
                // Dentro de "con sesion" la navegacion entre tabs la maneja
                // MainScaffold con su propio NavController.
                //
                // El estado inicial se rellena con AuthRepository.currentUser:
                // si Firebase ya tiene sesion persistida en el dispositivo,
                // arrancamos directamente en MainScaffold; si no, en Login.
                var currentUser by remember {
                    mutableStateOf<FirebaseUser?>(AuthRepository.currentUser)
                }

                val user = currentUser
                if (user == null) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        LoginScreen(
                            onLoginSuccess = { signedInUser ->
                                currentUser = signedInUser
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } else {
                    MainScaffold(
                        onSignOut = {
                            AuthRepository.signOut()
                            currentUser = null
                        }
                    )
                }
            }
        }
    }
}
