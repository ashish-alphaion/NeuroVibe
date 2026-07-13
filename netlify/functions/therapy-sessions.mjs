import { getStore } from "@netlify/blobs";
import {
  ApiError,
  hasBearerToken,
  json,
  options,
  readSmallJson,
  requireSessionFields,
} from "./_shared.mjs";

const STORE_NAME = "neurovibe-therapy-sessions";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);

  try {
    if (request.method === "POST") return await uploadSession(request);
    if (request.method === "GET") return await listSessions(request);
    return json(request, 405, { error: "method_not_allowed" });
  } catch (error) {
    console.error("therapy-sessions error", error);
    if (error instanceof ApiError) {
      return json(request, error.status, { error: error.code, message: error.message });
    }
    return json(request, 500, { error: "internal_error", message: "The gateway could not process the request." });
  }
};

async function uploadSession(request) {
  if (!hasBearerToken(request, process.env.DEVICE_API_TOKEN)) {
    throw new ApiError(401, "unauthorized", "A valid device bearer token is required.");
  }

  const body = await readSmallJson(request);
  requireSessionFields(body);

  const record = {
    ...body,
    requested_hz: Number(body.requested_hz),
    pwm_value: Number(body.pwm_value),
    duration_seconds: Number(body.duration_seconds),
    received_at_utc: new Date().toISOString(),
  };

  // Each session gets its own immutable key. Retries are idempotent and devices
  // cannot overwrite another session or a previously accepted version.
  const store = getStore({ name: STORE_NAME, consistency: "strong" });
  const key = `sessions/${body.device_id}/${body.session_id}`;
  const result = await store.setJSON(key, record, {
    onlyIfNew: true,
    metadata: { device_id: String(body.device_id), patient_id: String(body.patient_id) },
  });

  return json(request, result.modified ? 201 : 200, {
    status: result.modified ? "accepted" : "already_accepted",
    acknowledged: true,
    session_id: body.session_id,
  });
}

async function listSessions(request) {
  if (!hasBearerToken(request, process.env.DOCTOR_API_TOKEN)) {
    throw new ApiError(401, "unauthorized", "A valid doctor bearer token is required.");
  }

  const store = getStore({ name: STORE_NAME, consistency: "strong" });
  const { blobs } = await store.list({ prefix: "sessions/" });
  const selected = blobs.slice(-100);
  const sessions = (await Promise.all(
    selected.map(({ key }) => store.get(key, { type: "json", consistency: "strong" })),
  )).filter(Boolean);

  sessions.sort((a, b) => String(b.received_at_utc).localeCompare(String(a.received_at_utc)));
  return json(request, 200, { sessions, count: sessions.length, limited_to: 100 });
}
