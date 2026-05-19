/**
 * CLAUDE CODE:
 * Cloud Functions de IAHybridCoach.
 *
 * Funciones expuestas:
 *  - stravaExchangeCode: tras el OAuth, intercambia el authorization code
 *    por access_token + refresh_token y los guarda en Firestore.
 *  - stravaListActivities: lee las actividades de los ultimos N dias del
 *    atleta logueado, refrescando tokens si caducaron.
 *
 * Stack:
 *  - Firebase Functions v2 (region europe-west1).
 *  - Callable (`onCall`) para auth automatico con Firebase Auth.
 *  - `invoker: "public"` para que Cloud Run no rechace los tokens de
 *    Firebase (no son OAuth2 de GCP). El framework de Firebase Functions
 *    valida el ID token internamente.
 *  - Secret Manager para STRAVA_CLIENT_SECRET.
 */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions/v2";
import {initializeApp} from "firebase-admin/app";
import {getFirestore, FieldValue} from "firebase-admin/firestore";

const STRAVA_CLIENT_SECRET = defineSecret("STRAVA_CLIENT_SECRET");
const STRAVA_CLIENT_ID = "247273";

setGlobalOptions({region: "europe-west1"});

initializeApp();

// ----------------------------- Tipos -----------------------------

interface ExchangeCodeRequest {
  code?: string;
}

interface StravaTokenResponse {
  token_type: string;
  expires_at: number;
  expires_in: number;
  refresh_token: string;
  access_token: string;
  athlete?: {
    id: number;
    firstname?: string;
    lastname?: string;
  };
}

interface ListActivitiesRequest {
  daysBack?: number; // por defecto 30
}

interface GetActivityDetailRequest {
  activityId?: number;
}

interface StravaActivityDto {
  id: number;
  name: string;
  type: string;
  sport_type: string;
  start_date: string;
  start_date_local: string;
  elapsed_time: number;
  moving_time: number;
  distance: number;
  total_elevation_gain?: number;
  average_speed?: number;
  max_speed?: number;
  has_heartrate?: boolean;
  average_heartrate?: number;
  max_heartrate?: number;
  calories?: number;
  private?: boolean;
}

/** Subset que devolvemos al cliente (lo que necesita la UI). */
interface ActivitySummary {
  id: number;
  name: string;
  sportType: string;
  startDate: string;       // ISO 8601 UTC
  movingTimeSec: number;
  distanceMeters: number;
  avgHeartRate: number;
  maxHeartRate: number;
  caloriesKcal: number;
}

// ----------------------------- Helpers -----------------------------

const STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token";
const STRAVA_ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";
const STRAVA_ACTIVITY_URL = "https://www.strava.com/api/v3/activities";

/**
 * Lee los tokens del usuario; si el access_token caduca en menos de 60s,
 * lo refresca usando refresh_token y guarda los nuevos.
 *
 * Devuelve access_token valido para usar en llamadas a Strava.
 */
async function getValidAccessToken(uid: string): Promise<string> {
  const docRef = getFirestore()
    .collection("users")
    .doc(uid)
    .collection("integrations")
    .doc("strava");

  const snapshot = await docRef.get();
  if (!snapshot.exists) {
    throw new HttpsError(
      "failed-precondition",
      "No hay conexion con Strava. Conecta Strava antes de importar.",
    );
  }
  const data = snapshot.data() as {
    accessToken: string;
    refreshToken: string;
    expiresAt: number;
  };

  const nowSeconds = Math.floor(Date.now() / 1000);
  // 60s de margen para evitar usar un token justo caducado.
  if (data.expiresAt - nowSeconds > 60) {
    return data.accessToken;
  }

  // Token caducado o por caducar -> refrescar.
  const params = new URLSearchParams({
    client_id: STRAVA_CLIENT_ID,
    client_secret: STRAVA_CLIENT_SECRET.value(),
    grant_type: "refresh_token",
    refresh_token: data.refreshToken,
  });
  const response = await fetch(STRAVA_TOKEN_URL, {
    method: "POST",
    headers: {"Content-Type": "application/x-www-form-urlencoded"},
    body: params.toString(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new HttpsError(
      "unauthenticated",
      `Strava rechazo el refresh_token: ${response.status} ${text}. ` +
        "Vuelve a conectar Strava desde la app.",
    );
  }
  const refreshed = (await response.json()) as StravaTokenResponse;
  await docRef.update({
    accessToken: refreshed.access_token,
    refreshToken: refreshed.refresh_token,
    expiresAt: refreshed.expires_at,
    refreshedAt: FieldValue.serverTimestamp(),
  });
  return refreshed.access_token;
}

// ----------------------------- Funciones -----------------------------

export const stravaExchangeCode = onCall<ExchangeCodeRequest>(
  {
    secrets: [STRAVA_CLIENT_SECRET],
    cors: false,
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

    const params = new URLSearchParams({
      client_id: STRAVA_CLIENT_ID,
      client_secret: STRAVA_CLIENT_SECRET.value(),
      code: code,
      grant_type: "authorization_code",
    });

    const response = await fetch(STRAVA_TOKEN_URL, {
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

/**
 * Lista actividades del atleta de los ultimos N dias (por defecto 30).
 * Devuelve un subset reducido (campos que necesita la UI). El cliente
 * decide cuales importar como Workout.
 */
export const stravaListActivities = onCall<ListActivitiesRequest>(
  {
    secrets: [STRAVA_CLIENT_SECRET],
    cors: false,
    invoker: "public",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError(
        "unauthenticated",
        "Necesitas iniciar sesion en la app.",
      );
    }

    const daysBack = Math.max(1, Math.min(365, request.data?.daysBack ?? 30));
    const afterEpochSeconds =
      Math.floor(Date.now() / 1000) - daysBack * 24 * 3600;

    const accessToken = await getValidAccessToken(uid);

    const url = new URL(STRAVA_ACTIVITIES_URL);
    url.searchParams.set("after", String(afterEpochSeconds));
    url.searchParams.set("per_page", "100");

    const response = await fetch(url.toString(), {
      method: "GET",
      headers: {Authorization: `Bearer ${accessToken}`},
    });
    if (!response.ok) {
      const text = await response.text();
      throw new HttpsError(
        "internal",
        `Strava devolvio ${response.status} al listar actividades: ${text}`,
      );
    }
    const list = (await response.json()) as StravaActivityDto[];

    const summaries: ActivitySummary[] = list.map((a) => ({
      id: a.id,
      name: a.name ?? "",
      sportType: a.sport_type ?? a.type ?? "",
      startDate: a.start_date,
      movingTimeSec: a.moving_time ?? 0,
      distanceMeters: a.distance ?? 0,
      avgHeartRate: Math.round(a.average_heartrate ?? 0),
      maxHeartRate: Math.round(a.max_heartrate ?? 0),
      caloriesKcal: a.calories ?? 0,
    }));

    return {activities: summaries};
  },
);

/**
 * Detalle de una actividad: laps + streams (FC, velocidad, GPS).
 *
 * Devuelve un objeto ya con la forma del modelo `WorkoutDetail` del cliente
 * (campos `hrSamples`, `speedSamples`, `routePoints`, `laps`, `segments`).
 * El cliente lo guarda tal cual en Firestore.
 */
export const stravaGetActivityDetail = onCall<GetActivityDetailRequest>(
  {
    secrets: [STRAVA_CLIENT_SECRET],
    cors: false,
    invoker: "public",
    // CLAUDE CODE: subimos memoria a 512Mi porque procesar streams de
    // actividades largas (10000+ puntos) puede pasar 256Mi.
    memory: "512MiB",
    timeoutSeconds: 60,
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError(
        "unauthenticated",
        "Necesitas iniciar sesion en la app.",
      );
    }
    const activityId = request.data?.activityId;
    if (!activityId) {
      throw new HttpsError(
        "invalid-argument",
        "Falta el activityId.",
      );
    }

    const accessToken = await getValidAccessToken(uid);

    // 1) Detalle base (con laps embebidos).
    const detailUrl = `${STRAVA_ACTIVITY_URL}/${activityId}`;
    const detailRes = await fetch(detailUrl, {
      headers: {Authorization: `Bearer ${accessToken}`},
    });
    if (!detailRes.ok) {
      throw new HttpsError(
        "internal",
        `Strava devolvio ${detailRes.status} al pedir activity ${activityId}`,
      );
    }
    const detail = await detailRes.json() as {
      start_date?: string;
      laps?: Array<{
        elapsed_time?: number;
        moving_time?: number;
        distance?: number;
        average_heartrate?: number;
        average_speed?: number;
      }>;
    };

    // 2) Streams (FC, velocidad, GPS, tiempo, altitud) en una sola llamada.
    const streamsUrl = `${STRAVA_ACTIVITY_URL}/${activityId}/streams?` +
      "keys=time,heartrate,velocity_smooth,latlng,altitude&key_by_type=true";
    const streamsRes = await fetch(streamsUrl, {
      headers: {Authorization: `Bearer ${accessToken}`},
    });
    let streams: Record<string, {data?: unknown[]}> = {};
    if (streamsRes.ok) {
      streams = await streamsRes.json() as Record<string, {data?: unknown[]}>;
    }
    // Si el endpoint de streams devuelve 404 lo tratamos como "sin datos"
    // (algunas actividades manuales no los tienen).

    const timeData = (streams["time"]?.data as number[] | undefined) ?? [];
    const hrData = (streams["heartrate"]?.data as number[] | undefined) ?? [];
    const speedData = (streams["velocity_smooth"]?.data as number[] | undefined) ?? [];
    const latlngData = (streams["latlng"]?.data as Array<[number, number]> | undefined) ?? [];
    const altData = (streams["altitude"]?.data as number[] | undefined) ?? [];

    const hrSamples = hrData.map((bpm, i) => ({
      timeOffsetMillis: (timeData[i] ?? 0) * 1000,
      bpm: Math.round(bpm),
    }));
    const speedSamples = speedData.map((mps, i) => ({
      timeOffsetMillis: (timeData[i] ?? 0) * 1000,
      mps: mps,
    }));
    const routePoints = latlngData.map((coord, i) => ({
      timeOffsetMillis: (timeData[i] ?? 0) * 1000,
      lat: coord[0],
      lon: coord[1],
      altMeters: altData[i] ?? null,
    }));

    // Laps: Strava no expone offsets directamente, los calculamos sumando
    // elapsed_time. Si elapsed_time falta usamos moving_time como
    // aproximacion.
    let lapOffset = 0;
    const laps = (detail.laps ?? []).map((lap) => {
      const duration = lap.elapsed_time ?? lap.moving_time ?? 0;
      const info = {
        startOffsetMillis: lapOffset,
        durationSeconds: duration,
        distanceMeters: lap.distance ?? 0,
        avgHeartRate: Math.round(lap.average_heartrate ?? 0),
        avgSpeedMps: lap.average_speed ?? 0,
      };
      lapOffset += duration * 1000;
      return info;
    });

    return {
      hrSamples,
      speedSamples,
      routePoints,
      laps,
      segments: [], // Strava no devuelve segments como tal aqui (son otros conceptos).
    };
  },
);
