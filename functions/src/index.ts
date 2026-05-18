/**
 * CLAUDE CODE:
 * Cloud Functions de IAHybridCoach.
 *
 * Por ahora solo expone `stravaExchangeCode`: recibe el `code` que la app
 * obtuvo tras el OAuth con Strava y lo intercambia (usando el client_secret
 * guardado en Secret Manager) por un access_token + refresh_token. Los
 * tokens se guardan en Firestore bajo el documento del usuario:
 *
 *   /users/{uid}/integrations/strava
 *
 * Si en el futuro queremos listar actividades o pedir streams, anyadiremos
 * mas funciones aqui (todas reusando los tokens guardados).
 *
 * Stack:
 *  - Firebase Functions v2 (region europe-west1 para latencia y porque el
 *    proyecto Firestore esta en eur3).
 *  - Callable (`onCall`): asi el cliente puede invocarla con el SDK de
 *    Firebase Functions y autenticacion automatica via Firebase Auth.
 *  - Client Secret de Strava en Secret Manager (NUNCA en codigo ni env vars).
 */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions/v2";
import {initializeApp} from "firebase-admin/app";
import {getFirestore, FieldValue} from "firebase-admin/firestore";

// CLAUDE CODE: secret se rellena con `firebase functions:secrets:set STRAVA_CLIENT_SECRET`.
const STRAVA_CLIENT_SECRET = defineSecret("STRAVA_CLIENT_SECRET");

// CLAUDE CODE: el client_id es publico, lo dejamos como parametro de entorno
// (o, mas simple, hardcoded). Si en el futuro tenemos staging/prod podria
// venir de Remote Config.
const STRAVA_CLIENT_ID = "247273";

// Region por defecto para todas las funciones del modulo.
setGlobalOptions({region: "europe-west1"});

initializeApp();

interface ExchangeCodeRequest {
  code?: string;
}

interface StravaTokenResponse {
  token_type: string;
  expires_at: number;     // epoch seconds
  expires_in: number;
  refresh_token: string;
  access_token: string;
  athlete?: {
    id: number;
    firstname?: string;
    lastname?: string;
  };
}

/**
 * Intercambia un authorization_code de Strava por tokens y los guarda en
 * Firestore para el usuario autenticado.
 */
export const stravaExchangeCode = onCall<ExchangeCodeRequest>(
  {
    secrets: [STRAVA_CLIENT_SECRET],
    cors: false,
    // CLAUDE CODE: callable v2 corre sobre Cloud Run. Por defecto Cloud Run
    // rechaza llamadas sin OAuth2 de GCP, pero los clientes Firebase mandan
    // tokens de Firebase Auth (formato distinto). Por eso necesitamos
    // marcar el endpoint como "publico" a nivel Cloud Run; la verificacion
    // del Firebase Auth la hace el framework de Firebase Functions y
    // nosotros chequeamos request.auth?.uid dentro del codigo. Sin esto
    // todas las llamadas reciben UNAUTHENTICATED antes de llegar a nosotros.
    invoker: "public",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError(
        "unauthenticated",
        "Necesitas iniciar sesion en la app antes de conectar Strava.",
      );
    }

    const code = request.data?.code?.trim();
    if (!code) {
      throw new HttpsError(
        "invalid-argument",
        "Falta el 'code' del flujo OAuth.",
      );
    }

    // Llamada a Strava para canjear el code.
    const params = new URLSearchParams({
      client_id: STRAVA_CLIENT_ID,
      client_secret: STRAVA_CLIENT_SECRET.value(),
      code: code,
      grant_type: "authorization_code",
    });

    const response = await fetch("https://www.strava.com/oauth/token", {
      method: "POST",
      headers: {"Content-Type": "application/x-www-form-urlencoded"},
      body: params.toString(),
    });

    if (!response.ok) {
      const text = await response.text();
      throw new HttpsError(
        "internal",
        `Strava rechazo el code: ${response.status} ${text}`,
      );
    }

    const tokens = (await response.json()) as StravaTokenResponse;

    // Guardar en Firestore. Mantenemos refresh_token y expires_at para
    // poder renovar el access_token sin volver a pedir consentimiento.
    await getFirestore()
      .collection("users")
      .doc(uid)
      .collection("integrations")
      .doc("strava")
      .set({
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        expiresAt: tokens.expires_at,
        athleteId: tokens.athlete?.id ?? null,
        athleteName: [tokens.athlete?.firstname, tokens.athlete?.lastname]
          .filter((s) => !!s)
          .join(" ") || null,
        connectedAt: FieldValue.serverTimestamp(),
      });

    return {
      success: true,
      athleteName: tokens.athlete?.firstname ?? null,
    };
  },
);
