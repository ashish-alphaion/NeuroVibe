import { json, options } from "./_shared.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "GET") return json(request, 405, { error: "method_not_allowed" });

  return json(request, 200, {
    service: "NeuroVibe temporary gateway",
    status: "online",
    storage: "netlify_blobs",
    time_utc: new Date().toISOString(),
  });
};
