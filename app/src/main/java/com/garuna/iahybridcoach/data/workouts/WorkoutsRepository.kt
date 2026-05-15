package com.garuna.iahybridcoach.data.workouts

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Repositorio de entrenamientos del usuario logueado.
 *
 * Todas las operaciones trabajan contra la subcoleccion
 *   /users/{uid}/workouts
 * donde uid se obtiene de FirebaseAuth en tiempo de llamada. Si no hay
 * sesion activa, las funciones devuelven Failure / un Flow vacio.
 *
 * Es `object` porque FirebaseAuth y Firestore ya son singletons internos:
 * no aporta nada instanciarlo varias veces.
 */
object WorkoutsRepository {

    private const val USERS = "users"
    private const val WORKOUTS = "workouts"
    private const val FIELD_FECHA = "fecha"

    private val firestore get() = Firebase.firestore
    private val currentUid: String?
        get() = Firebase.auth.currentUser?.uid

    /**
     * Stream en tiempo real de los entrenamientos del usuario, ordenados por
     * fecha descendente. Si se anyade/borra desde otro dispositivo, el Flow
     * emite el nuevo listado automaticamente.
     */
    fun observeWorkouts(): Flow<List<Workout>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore.collection(USERS)
            .document(uid)
            .collection(WORKOUTS)
            .orderBy(FIELD_FECHA, Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // CLAUDE CODE: cerrar con error propaga la excepcion al consumidor
                // del Flow (ViewModel), que decidira que mostrar.
                close(error)
                return@addSnapshotListener
            }
            val workouts = snapshot?.toObjects<Workout>().orEmpty()
            trySend(workouts)
        }

        // CLAUDE CODE: awaitClose se ejecuta cuando el consumidor cancela el
        // Flow (p. ej. ViewModel cleared). Liberamos el listener para no
        // dejar suscripciones colgadas tirando de la cuota de Firestore.
        awaitClose { registration.remove() }
    }

    /**
     * Anyade un nuevo entrenamiento. El @DocumentId de Workout se ignora al
     * escribir; Firestore genera el ID. Devolvemos el ID generado por si la
     * UI lo necesita (por ejemplo para scrollear al recien creado).
     */
    suspend fun addWorkout(workout: Workout): Result<String> {
        val uid = currentUid
            ?: return Result.failure(IllegalStateException("Sin sesion activa"))

        return runCatching {
            val docRef = firestore.collection(USERS)
                .document(uid)
                .collection(WORKOUTS)
                .add(workout)
                .await()
            docRef.id
        }
    }

    /** Borra un entrenamiento por su ID. */
    suspend fun deleteWorkout(workoutId: String): Result<Unit> {
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
                .delete()
                .await()
        }
    }
}
