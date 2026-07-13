import { createHmac, randomBytes } from "node:crypto";
import { ApiError, getBearerToken, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });

  try {
    const token = getBearerToken(request);
    if (!token) throw new ApiError(401, "unauthorized", "A patient app session is required.");
    const body = await readSmallJson(request);
    const deviceId = String(body.device_id || "").trim();
    if (!/^[A-Za-z0-9._:-]{3,128}$/.test(deviceId)) throw new ApiError(400, "invalid_device_id", "A valid device ID is required.");

    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "The patient session is invalid.");
    const { data: profile, error: profileError } = await supabase.from("profiles")
      .select("id, organization_id, role, status")
      .eq("id", userResult.user.id).maybeSingle();
    if (profileError) throw profileError;
    if (!profile || profile.role !== "patient" || profile.status !== "active" || !profile.organization_id) {
      throw new ApiError(403, "patient_required", "Only an active patient account can provision its assigned device.");
    }

    const { data: patient, error: patientError } = await supabase.from("patients")
      .select("id, doctor_id, program_status")
      .eq("user_id", profile.id).eq("organization_id", profile.organization_id).maybeSingle();
    if (patientError) throw patientError;
    if (!patient || !["enrolled", "active"].includes(patient.program_status)) {
      throw new ApiError(403, "patient_inactive", "The patient is not enrolled in an active program.");
    }

    const { data: assignment, error: assignmentError } = await supabase.from("device_assignments")
      .select("id, device_id, devices(id, display_name, lifecycle_status)")
      .eq("patient_id", patient.id).eq("device_id", deviceId).eq("status", "active").maybeSingle();
    if (assignmentError) throw assignmentError;
    if (!assignment) throw new ApiError(403, "device_not_assigned", "This NeuroSense device is not assigned to the signed-in patient.");

    const { data: plan, error: planError } = await supabase.from("care_plans")
      .select("id, name, min_hz, target_hz, max_hz, duration_seconds, max_duration_seconds, manual_control_allowed")
      .eq("patient_id", patient.id).eq("status", "active").maybeSingle();
    if (planError) throw planError;

    const pepper = process.env.DEVICE_TOKEN_PEPPER?.trim();
    if (!pepper) throw new ApiError(503, "provisioning_unavailable", "Device token provisioning is not configured on the server.");
    const deviceToken = `nvd_${randomBytes(32).toString("base64url")}`;
    const tokenHash = createHmac("sha256", pepper).update(deviceToken, "utf8").digest("hex");

    const now = new Date().toISOString();
    const { error: revokeError } = await supabase.from("device_credentials")
      .update({ status: "revoked", revoked_at: now })
      .eq("device_id", deviceId).eq("status", "active");
    if (revokeError) throw revokeError;
    const { error: credentialError } = await supabase.from("device_credentials").insert({
      device_id: deviceId,
      token_hash: tokenHash,
      status: "active",
      created_by: patient.doctor_id,
    });
    if (credentialError) throw credentialError;

    await supabase.from("devices").update({ lifecycle_status: "active" }).eq("id", deviceId);
    await supabase.from("patients").update({ program_status: "active" }).eq("id", patient.id);
    await supabase.from("audit_events").insert({
      organization_id: profile.organization_id,
      actor_id: profile.id,
      action: "device.provisioned_by_patient",
      entity_type: "device",
      entity_id: deviceId,
      summary: "Assigned NeuroSense device provisioned through the patient app",
      metadata: { patient_id: patient.id, assignment_id: assignment.id },
    });

    return json(request, 200, {
      device_id: deviceId,
      patient_id: patient.id,
      assignment_id: assignment.id,
      api_base_url: process.env.PUBLIC_API_BASE_URL?.trim() || "https://neurovibeapi.netlify.app",
      api_token: deviceToken,
      care_plan: plan,
    });
  } catch (error) {
    console.error("device provisioning error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The assigned device could not be provisioned." });
  }
};
