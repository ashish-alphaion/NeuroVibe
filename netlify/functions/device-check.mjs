import { ApiError, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin, verifyDeviceRequest } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });
  try {
    const body = await readSmallJson(request);
    const deviceId = String(body.device_id || "").trim();
    const patientId = String(body.patient_id || "").trim();
    const assignmentId = String(body.assignment_id || "").trim();
    if (!deviceId || !patientId || !assignmentId) throw new ApiError(400, "missing_fields", "Device, patient and assignment identifiers are required.");
    if (!(await verifyDeviceRequest(request, deviceId))) throw new ApiError(401, "unauthorized", "The device credential is invalid.");
    const supabase = getSupabaseAdmin();
    const { data: assignment, error } = await supabase.from("device_assignments")
      .select("id,patient_id,device_id,status,patients(program_status)")
      .eq("id", assignmentId).eq("patient_id", patientId).eq("device_id", deviceId).eq("status", "active").maybeSingle();
    if (error) throw error;
    if (!assignment || !["enrolled", "active"].includes(assignment.patients?.program_status)) {
      throw new ApiError(409, "assignment_not_active", "The doctor assignment is not active for this device and patient.");
    }
    const now = new Date();
    const leaseExpiresAt = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
    const { error: leaseError } = await supabase.from("device_assignments").update({
      lease_expires_at: leaseExpiresAt,
      last_renewed_at: now.toISOString(),
    }).eq("id", assignmentId).eq("status", "active");
    if (leaseError) throw leaseError;
    await supabase.from("devices").update({ lifecycle_status: "active", last_seen_at: now.toISOString() }).eq("id", deviceId);
    return json(request, 200, {
      verified: true,
      device_id: deviceId,
      patient_id: patientId,
      assignment_id: assignmentId,
      assignment_valid_until: leaseExpiresAt,
      assignment_valid_until_epoch: Math.floor(new Date(leaseExpiresAt).getTime() / 1000),
      server_time_epoch: Math.floor(now.getTime() / 1000),
    });
  } catch (error) {
    console.error("device-check error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The device assignment could not be verified." });
  }
};
