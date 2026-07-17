import {
  ApiError,
  hasBearerToken,
  json,
  options,
  readSmallJson,
  requireSessionFields,
} from "./_shared.mjs";
import { getSupabaseAdmin, verifyDeviceRequest } from "./_supabase.mjs";

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
  const body = await readSmallJson(request);
  requireSessionFields(body);
  if (!(await verifyDeviceRequest(request, String(body.device_id), ["active", "sync_only"]))) {
    throw new ApiError(401, "unauthorized", "A valid device bearer token is required.");
  }

  const record = {
    ...body,
    requested_hz: Number(body.requested_hz),
    pwm_value: Number(body.pwm_value),
    duration_seconds: Number(body.duration_seconds),
  };
  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase.rpc("ingest_therapy_session", { payload: record });
  if (error) {
    if (error.message.includes("invalid_assignment")) {
      throw new ApiError(409, "invalid_assignment", "The active device assignment does not match this session.");
    }
    if (error.message.includes("device_sequence_conflict")) {
      throw new ApiError(409, "device_sequence_conflict", "The device sequence has already been used by another session.");
    }
    console.error("Supabase session ingest failed", { code: error.code, message: error.message });
    throw new ApiError(500, "database_error", "The session could not be stored.");
  }

  const status = data?.status ?? "accepted";
  return json(request, status === "accepted" ? 201 : 200, {
    status,
    acknowledged: true,
    session_id: body.session_id,
  });
}

async function listSessions(request) {
  if (!hasBearerToken(request, process.env.DOCTOR_API_TOKEN)) {
    throw new ApiError(401, "unauthorized", "A valid doctor bearer token is required.");
  }

  const supabase = getSupabaseAdmin();
  const { data: sessions, error } = await supabase
    .from("therapy_sessions")
    .select("*")
    .order("started_at_utc", { ascending: false })
    .limit(100);
  if (error) {
    console.error("Supabase session list failed", { code: error.code, message: error.message });
    throw new ApiError(500, "database_error", "Sessions could not be retrieved.");
  }
  return json(request, 200, { sessions, count: sessions.length, limited_to: 100 });
}
