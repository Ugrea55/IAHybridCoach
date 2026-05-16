package com.garuna.iahybridcoach.data.health

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/* CLAUDE CODE:
 * Repositorio de salud para el modelo "una entrada por dia".
 *
 * Cada dia tiene como mucho UN documento en /users/{uid}/healthEntries/{YYYY-MM-DD}.
 * Usar la fecha como ID del documento garantiza unicidad por dia sin necesidad
 * de queries ni indices.
 *
 * Decisiones:
 * - Lecturas one-shot (suspend), no listeners en tiempo real. La pantalla
 *   carga UNA VEZ los datos del dia y a partir de ahi el estado es local;
 *   los autosaves van solo del cliente al servidor. Asi evitamos que un
 *   evento de Firestore sobrescriba lo que el usuario esta escribiendo.
 *   Si en el futuro queremos sync multi-dispositivo, anyadiremos un
 *   observe() complementario.
 * - set() (no update) para guardar: si el doc no existe lo crea, si existe
 *   lo sobrescribe. Es el modelo correcto cuando la UI tiene el estado
 *   completo de la entrada.
 */
object HealthRepository {

    private const val USERS = "users"
    private const val HEALTH_ENTRIES = "healthEntries"

    private val firestore get() = Firebase.firestore
    private val currentUid: String?
        get() = Firebase.auth.currentUser?.uid

    /** Convierte una LocalDate al ID que usamos en Firestore (YYYY-MM-DD). */
    fun docIdFor(date: LocalDate): String = date.toString()

    /**
     * Lee el registro de salud de una fecha. Null si no existe documento
     * todavia para ese dia.
     */
    suspend fun getEntryForDate(date: LocalDate): Result<HealthEntry?> {
        val uid = currentUid
            ?: return Result.failure(IllegalStateException("Sin sesion activa"))

        return runCatching {
            val snapshot = firestore.collection(USERS)
                .document(uid)
                .collection(HEALTH_ENTRIES)
                .document(docIdFor(date))
                .get()
                .await()
            if (snapshot.exists()) snapshot.toObject<HealthEntry>() else null
        }
    }

    /**
     * Guarda (o sobrescribe) el registro del dia. Devuelve Unit en exito.
     */
    suspend fun saveEntryForDate(
        date: LocalDate,
        entry: HealthEntry
    ): Result<Unit> {
        val uid = currentUid
            ?: return Result.failure(IllegalStateException("Sin sesion activa"))

        return runCatching {
            firestore.collection(USERS)
                .document(uid)
                .collection(HEALTH_ENTRIES)
                .document(docIdFor(date))
                .set(entry)
                .await()
        }
    }
}
