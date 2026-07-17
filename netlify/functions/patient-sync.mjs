import { ApiError, getBearerToken, json, options, readSmallJson, requireSessionFields } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });

  try {
    const token = getBearerToken(request);
    if (!token) throw new ApiError(401, "unauthorized", "A patient app session is required.");
    const body = await readSmallJson(request);
    body.sync_source = "mobile_ble_relay";
    requireSessionFields(body);

    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "The patient session is invalid.");

    const { data: patient, error: patientError } = await supabase
      .from("patients")
      .select("id,user_id,program_status")
      .eq("user_id", userResult.user.id)
      .maybeSingle();
    if (patientError) throw patientError;
    if (!patient || !["enrolled", "active"].includes(patient.program_status)) {
      throw new ApiError(403, "patient_inactive", "This patient account is not active.");
    }

    const { data: assignment, error: assignmentError } = await supabase
      .from("device_assignments")
      .select("id,patient_id,device_id,status,starts_at,ends_at,lease_expires_at")
      .eq("id", String(body.assignment_id))
      .eq("patient_id", patient.id)
      .eq("device_id", String(body.device_id))
      .maybeSingle();
    if (assignmentError) throw assignmentError;
    const startedAt = new Date(String(body.started_at_utc));
    const startsAt = assignment ? new Date(assignment.starts_at) : null;
    const endsAt = assignment?.ends_at ? new Date(assignment.ends_at) : null;
    const leaseEndsAt = assignment?.lease_expires_at ? new Date(assignment.lease_expires_at) : null;
    const occurredDuringAssignment = assignment && !Number.isNaN(startedAt.getTime()) &&
      startedAt >= startsAt &&
      ((assignment.status === "active" && (!leaseEndsAt || startedAt <= leaseEndsAt)) ||
       (assignment.status === "closed" && endsAt && startedAt <= endsAt));
    if (!assignment || !occurredDuringAssignment || String(body.patient_id) !== patient.id) {
      throw new ApiError(403, "invalid_assignment", "This usage record does not belong to the signed-in patient and assigned device.");
    }

    const { data, error } = await supabase.rpc("ingest_therapy_session", { payload: body });
    if (error) throw new ApiError(409, "usage_rejected", error.message.includes("invalid_assignment") ? "The active device assignment does not match this record." : "The usage record was rejected.");
    return json(request, data?.status === "accepted" ? 201 : 200, {
      status: data?.status ?? "accepted",
      acknowledged: true,
      session_id: body.session_id,
    });
  } catch (error) {
    console.error("patient-sync error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The app could not synchronize this usage record." });
  }
};
