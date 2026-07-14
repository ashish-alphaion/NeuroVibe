import { ApiError, getBearerToken, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });
  try {
    const token = getBearerToken(request);
    const body = await readSmallJson(request);
    if (body.confirmation !== "RESET_NEUROVIBE_PROTOTYPE") throw new ApiError(400, "confirmation_required", "The exact reset confirmation is required.");
    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "An administrator session is required.");
    const { data: admin } = await supabase.from("profiles").select("id,organization_id,role,status").eq("id", userResult.user.id).maybeSingle();
    if (!admin || admin.role !== "admin" || admin.status !== "active" || !admin.organization_id) throw new ApiError(403, "admin_required", "Only an active organization administrator can reset prototype data.");
    const organizationId = admin.organization_id;
    const { data: patientProfiles, error: profileError } = await supabase.from("profiles").select("id").eq("organization_id", organizationId).eq("role", "patient");
    if (profileError) throw profileError;
    const { data: devices, error: deviceQueryError } = await supabase.from("devices").select("id").eq("organization_id", organizationId);
    if (deviceQueryError) throw deviceQueryError;
    const deviceIds = (devices || []).map((row) => row.id);

    const deleteOrgRows = async (table) => {
      const { error } = await supabase.from(table).delete().eq("organization_id", organizationId);
      if (error) throw error;
    };
    for (const table of ["sync_receipts"]) {
      const { data: sessions } = await supabase.from("therapy_sessions").select("id").eq("organization_id", organizationId);
      const ids = (sessions || []).map((row) => row.id);
      if (ids.length) { const { error } = await supabase.from(table).delete().in("event_id", ids); if (error) throw error; }
    }
    for (const table of ["therapy_sessions", "notifications", "appointments", "scheduled_sessions", "care_plans", "device_heartbeats", "device_enrollments", "device_assignments", "audit_events"]) await deleteOrgRows(table);
    if (deviceIds.length) { const { error } = await supabase.from("device_credentials").delete().in("device_id", deviceIds); if (error) throw error; }
    await deleteOrgRows("devices");
    await deleteOrgRows("patients");
    for (const profile of patientProfiles || []) {
      const { error } = await supabase.auth.admin.deleteUser(profile.id);
      if (error) throw error;
    }
    await supabase.from("audit_events").insert({ organization_id: organizationId, actor_id: admin.id, action: "prototype.reset", entity_type: "organization", entity_id: organizationId, summary: "Prototype patient, device and usage data reset", metadata: { deleted_patient_accounts: patientProfiles?.length || 0, deleted_devices: deviceIds.length } });
    return json(request, 200, { reset: true, preserved: ["organization", "admin_and_doctor_accounts", "database_schema"], deleted_patient_accounts: patientProfiles?.length || 0, deleted_devices: deviceIds.length });
  } catch (error) {
    console.error("reset-prototype error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "reset_failed", message: "Prototype data reset failed before completion." });
  }
};
