package com.garuna.iahybridcoach.data.workouts.detail

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Repositorio del detalle pesado de un workout.
 *
 * Path: /users/{uid}/workouts/{wid}/details/data
 * El doc se llama siempre "data" (1 unico documento por workout). Asi no
 * necesitamos @DocumentId y la lectura por id es directa.
 *
 * Listener en tiempo real para que la pantalla de detalle se actualice si
 * el detalle se rellena desde otro punto (p.ej. importacion en background
 * en el futuro).
 */
object WorkoutDetailRepository {

    private const val USERS = "users"
    private const val WORKOUTS = "workouts"
    private const val DETAILS = "details"
    private const val DATA_DOC = "data"

    private val firestore get() = Firebase.firestore
    private val currentUid: String?
        get() = Firebase.auth.currentUser?.uid

    /** Stream del detalle. Null = todavia no esta cacheado. */
    fun observeDetail(workoutId: String): Flow<WorkoutDetail?> = callbackFlow {
        val uid = currentUid
        if (uid == null || workoutId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection(USERS)
            .document(uid)
            .collection(WORKOUTS)
            .document(workoutId)
            .collection(DETAILS)
            .document(DATA_DOC)

        val reg = docRef.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            val value = if (snap != null && snap.exists()) snap.toObject<WorkoutDetail>() else null
            trySend(value)
        }
        awaitClose { reg.remove() }
    }

    /** Guarda (sobrescribe) el detalle de un workout. */
    suspend fun saveDetail(workoutId: String, detail: WorkoutDetail): Result<Unit> {
        val uid = currentUid
            ?: return Result.failure(IllegalStateException("Sin sesion activa"))
        if (workoutId.isBlank()) {
            return Result.failure(IllegalArgumentException("workoutId vacio"))
        }
        return runCatching {
            firestore.collection(USERS)
                .document(uid)
                .collection(WORKOUTS)
                .document(workoutId)
                .collection(DETAILS)
                .document(DATA_DOC)
                .set(detail)
                .await()
        }
    }
}
