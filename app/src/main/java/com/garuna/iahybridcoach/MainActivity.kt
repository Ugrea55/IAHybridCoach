package com.garuna.iahybridcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.garuna.iahybridcoach.data.auth.AuthRepository
import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.data.profile.UserProfileRepository
import com.garuna.iahybridcoach.ui.login.LoginScreen
import com.garuna.iahybridcoach.ui.main.MainScaffold
import com.garuna.iahybridcoach.ui.onboarding.OnboardingScreen
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.catch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IAHybridCoachTheme {
                // CLAUDE CODE: navegacion de alto nivel.
                //   Sin sesion          -> LoginScreen
                //   Sesion + cargando   -> spinner
                //   Sesion sin perfil   -> OnboardingScreen
                //   Sesion con perfil   -> MainScaffold (4 tabs)
                var currentUser by remember {
                    mutableStateOf<FirebaseUser?>(AuthRepository.currentUser)
                }

                // CLAUDE CODE: estado del perfil. profileLoaded distingue entre
                // "todavia no he leido Firestore" y "ya he leido y no hay perfil".
                var profile by remember { mutableStateOf<UserProfile?>(null) }
                var profileLoaded by remember { mutableStateOf(false) }

                LaunchedEffect(currentUser) {
                    if (currentUser == null) {
                        profile = null
                        profileLoaded = false
                        return@LaunchedEffect
                    }
                    profileLoaded = false
                    UserProfileRepository.observeProfile()
                        .catch { /* silencioso: el spinner se quedaria pero el usuario puede reintentar reiniciando */ }
                        .collect { value ->
                            profile = value
                            profileLoaded = true
                        }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)
                    val user = currentUser
                    when {
                        user == null -> LoginScreen(
                            onLoginSuccess = { signedInUser ->
                                currentUser = signedInUser
                            },
                            modifier = screenModifier
                        )
                        !profileLoaded -> Box(
                            modifier = screenModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        profile == null -> OnboardingScreen(
                            sugerenciaNombre = user.displayName,
                            modifier = screenModifier
                        )
                        else -> MainScaffold(
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
}
