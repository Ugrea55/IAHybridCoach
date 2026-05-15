package com.garuna.iahybridcoach.data.auth

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Repositorio que centraliza el acceso a FirebaseAuth. La UI no debe llamar
 * directamente a Firebase: pasa por aqui. Asi en el futuro podemos cambiar
 * la implementacion (mock para tests, otro proveedor de auth, etc.) sin
 * tocar la UI.
 *
 * Es un object porque FirebaseAuth ya es singleton internamente; no aporta
 * nada instanciarlo varias veces.
 */
object AuthRepository {

    private val firebaseAuth get() = Firebase.auth

    /** Usuario con sesion activa o null si no hay nadie logueado. */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Inicia sesion en Firebase Auth a partir del ID token devuelto por Google
     * Identity Services (Credential Manager). Devuelve el usuario o un Result
     * con el error para que la capa de UI decida que mostrar.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("ID token vacio"))
        }
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            authResult.user
                ?: throw IllegalStateException("Firebase devolvio AuthResult sin usuario")
        }
    }

    /** Cierra la sesion actual de Firebase. */
    fun signOut() {
        firebaseAuth.signOut()
    }
}
