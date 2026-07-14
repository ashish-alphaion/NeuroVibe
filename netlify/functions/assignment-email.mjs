import { ApiError, getBearerToken, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });
  try {
    const token = getBearerToken(request);
    const body = await readSmallJson(request);
    const patientId = String(body.patient_id || "");
    const deviceId = String(body.device_id || "");
    if (!token || !patientId || !deviceId) throw new ApiError(400, "missing_fields", "Patient and device are required.");
    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "A doctor session is required.");
    const { data: profile } = await supabase.from("profiles").select("id,organization_id,role,status").eq("id", userResult.user.id).maybeSingle();
    if (!profile || !["doctor", "admin"].includes(profile.role) || profile.status !== "active") throw new ApiError(403, "doctor_required", "Only active clinical staff can send assignment email.");
    const { data: patient, error: patientError } = await supabase.from("patients").select("id,full_name,patient_code,email,organization_id").eq("id", patientId).eq("organization_id", profile.organization_id).maybeSingle();
    if (patientError) throw patientError;
    if (!patient?.email) throw new ApiError(409, "patient_email_missing", "The patient has no email address.");
    const { data: assignment } = await supabase.from("device_assignments").select("id").eq("patient_id", patientId).eq("device_id", deviceId).eq("status", "active").maybeSingle();
    if (!assignment) throw new ApiError(409, "assignment_missing", "The active device assignment was not found.");

    const apiKey = process.env.RESEND_API_KEY?.trim();
    const from = process.env.ASSIGNMENT_EMAIL_FROM?.trim();
    if (!apiKey || !from) return json(request, 200, { sent: false, email_configured: false, message: "Assignment saved. Configure RESEND_API_KEY and ASSIGNMENT_EMAIL_FROM to send email automatically." });
    const response = await fetch("https://api.resend.com/emails", { method: "POST", headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" }, body: JSON.stringify({
      from, to: [patient.email], subject: "Your NeuroSense device assignment",
      text: `Hello ${patient.full_name},\n\nYour doctor assigned NeuroSense device ${deviceId} to patient ${patient.patient_code}.\n\nOpen NeuroVibe, sign in, and enter this Device ID during first-time setup. The app will verify the assignment before allowing the device to operate.\n\nNeuroVibe care team`,
    }) });
    if (!response.ok) throw new ApiError(502, "email_failed", "The assignment was saved, but the email provider rejected the message.");
    return json(request, 200, { sent: true, email_configured: true });
  } catch (error) {
    console.error("assignment-email error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The assignment email could not be sent." });
  }
};
