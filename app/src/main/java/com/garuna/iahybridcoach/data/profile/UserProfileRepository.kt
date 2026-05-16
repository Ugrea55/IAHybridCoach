package com.garuna.iahybridcoach.data.profile

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Repositorio del perfil del usuario logueado.
 *
 * El documento vive en /users/{uid}. observeProfile emite null cuando el
 * documento no existe todavia (usuario nuevo que no ha pasado por
 * onboarding) o cuando hay datos.
 *
 * El consumidor (MainActivity) usa esta distincion null-vs-no-null junto
 * con un flag local de "primera emision" para diferenciar Loading,
 * Missing y Loaded.
 */
object UserProfileRepository {

    private const val USERS = "users"

    private val firestore get() = Firebase.firestore
    private val currentUid: String?
        get() = Firebase.auth.currentUser?.uid

    /** Stream del perfil del usuario en tiempo real. Null = no hay perfil. */
    fun observeProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(USERS)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val profile = if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject<UserProfile>()
                } else {
                    null
                }
                trySend(profile)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Guarda el perfil del usuario. Sobrescribe el documento entero, asi
     * que para edicion parcial habria que primero leer + modificar + guardar.
     * De momento solo lo usa el flujo de onboarding (creacion inicial).
     */
    suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        val uid = currentUid
            ?: return Result.failure(IllegalStateException("Sin sesion activa"))

        return runCatching {
            firestore.collection(USERS)
                .document(uid)
                .set(profile)
                .await()
        }
    }
}
