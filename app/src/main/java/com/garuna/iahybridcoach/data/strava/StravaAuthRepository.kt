package com.garuna.iahybridcoach.data.strava

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/* CLAUDE CODE:
 * Encapsula la integracion con Strava desde el lado del cliente.
 *
 * Por ahora solo expone `exchangeCode`: recibe el code que devolvio el
 * OAuth de Strava y lo manda a la Cloud Function `stravaExchangeCode`
 * (region europe-west1). La funcion guarda los tokens en Firestore.
 *
 * En el futuro anyadiremos aqui mas wrappers (listActivities, getStreams,
 * disconnect, etc.) todos contra Cloud Functions.
 */
object StravaAuthRepository {

    /** URL completa que abriremos en el navegador para iniciar el OAuth. */
    fun buildAuthorizeUrl(): String {
        // CLAUDE CODE: scopes que pedimos. read = perfil basico,
        // activity:read_all = todas las actividades (incluso privadas) con
        // streams (FC, GPS, etc.). Estos son los que nos interesan para
        // poder cargar el detalle por segmentos.
        val scope = "read,activity:read_all"
        val params = listOf(
            "client_id" to STRAVA_CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "approval_prompt" to "auto",
            "scope" to scope
        ).joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        return "https://www.strava.com/oauth/mobile/authorize?$params"
    }

    /**
     * Llama a la Cloud Function `stravaExchangeCode` para canjear el code
     * por tokens. La funcion guarda los tokens en Firestore; el cliente
     * solo necesita saber si fue exito o no.
     *
     * Region "europe-west1" debe coincidir con la region donde desplegamos
     * la funcion.
     */
    suspend fun exchangeCode(code: String): Result<Unit> {
        return runCatching {
            val auth = Firebase.auth
            val user = auth.currentUser
            Log.d(TAG, "currentUser=${user?.uid}, anonymous=${user?.isAnonymous}")
            // CLAUDE CODE: forzamos refresh del id token para asegurar que la
            // llamada lleva un token valido y no uno caducado en cache.
            val tokenResult = user?.getIdToken(true)?.await()
            Log.d(TAG, "idToken length=${tokenResult?.token?.length}")

            val functions = FirebaseFunctions.getInstance("europe-west1")
            functions.getHttpsCallable("stravaExchangeCode")
                .call(mapOf("code" to code))
                .await()
        }
    }

    private const val TAG = "StravaAuthRepo"

    private const val STRAVA_CLIENT_ID = "247273"
    private const val REDIRECT_URI = "https://iahybridcoach.firebaseapp.com/strava/callback"
}
