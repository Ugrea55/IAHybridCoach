package com.garuna.iahybridcoach

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.garuna.iahybridcoach.data.strava.StravaCallbackBus
import com.garuna.iahybridcoach.data.strava.StravaCallbackEvent
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

        // CLAUDE CODE: si la Activity se ha abierto por el deep link de
        // Strava (porque la app estaba cerrada), publicamos el evento.
        handleIntent(intent)

        setContent {
            IAHybridCoachTheme {
                var currentUser by remember {
                    mutableStateOf<FirebaseUser?>(AuthRepository.currentUser)
                }

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
                        .catch { }
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

    /* CLAUDE CODE: cuando la Activity ya esta viva y llega un nuevo intent
     * (deep link, share, etc.) Android llama onNewIntent. Manejamos el
     * caso de que el usuario abra el deep link teniendo la app abierta. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        Log.d(TAG, "handleIntent action=${intent?.action} data=$data")
        if (data == null) return
        if (data.scheme == "iahybridcoach" && data.host == "strava-callback") {
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            Log.d(TAG, "Strava callback recibido: code=${code?.take(8)}... error=$error")
            when {
                !code.isNullOrBlank() -> StravaCallbackBus.publish(
                    StravaCallbackEvent.Success(code)
                )
                !error.isNullOrBlank() -> StravaCallbackBus.publish(
                    StravaCallbackEvent.Failure(error)
                )
                else -> StravaCallbackBus.publish(
                    StravaCallbackEvent.Failure("Sin code ni error en el callback")
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
