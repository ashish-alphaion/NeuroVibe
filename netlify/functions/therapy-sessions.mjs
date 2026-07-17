import {
  ApiError,
  hasBearerToken,
  json,
  options,
} from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);

  try {
    if (request.method === "POST") {
      throw new ApiError(410, "mobile_relay_required", "NeuroSense records must be uploaded by the authenticated patient app.");
    }
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
