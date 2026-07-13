import { json, options } from "./_shared.mjs";
import { checkSupabaseConnection } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "GET") return json(request, 405, { error: "method_not_allowed" });

  try {
    await checkSupabaseConnection();
    return json(request, 200, {
      service: "NeuroVibe API gateway",
      status: "online",
      storage: "supabase_postgresql",
      database: "connected",
      time_utc: new Date().toISOString(),
    });
  } catch (error) {
    console.error("health database check failed", error);
    return json(request, 503, {
      service: "NeuroVibe API gateway",
      status: "degraded",
      storage: "supabase_postgresql",
      database: "unavailable",
      time_utc: new Date().toISOString(),
    });
  }
};
