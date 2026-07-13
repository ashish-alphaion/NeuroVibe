import { ApiError, getBearerToken, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

const ALLOWED_GENDERS = new Set(["female", "male", "non_binary", "prefer_not_to_say", "other"]);

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });

  let invitedUserId = null;
  try {
    const token = getBearerToken(request);
    if (!token) throw new ApiError(401, "unauthorized", "A doctor portal session is required.");

    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "The portal session is invalid.");

    const { data: actor, error: actorError } = await supabase
      .from("profiles")
      .select("id, organization_id, role, status")
      .eq("id", userResult.user.id)
      .maybeSingle();
    if (actorError) throw actorError;
    if (!actor || !["admin", "doctor"].includes(actor.role) || actor.status !== "active" || !actor.organization_id) {
      throw new ApiError(403, "staff_required", "Only active clinical staff may enroll patients.");
    }

    const body = await readSmallJson(request);
    const fullName = String(body.full_name || "").trim();
    const patientCode = String(body.patient_code || "").trim().toUpperCase();
    const email = String(body.email || "").trim().toLowerCase();
    const gender = body.gender ? String(body.gender) : null;
    if (fullName.length < 2) throw new ApiError(400, "invalid_name", "Patient name must contain at least two characters.");
    if (!/^[A-Z0-9._:-]{2,64}$/.test(patientCode)) throw new ApiError(400, "invalid_patient_code", "Patient code contains unsupported characters.");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new ApiError(400, "invalid_email", "A valid patient email is required for app access.");
    if (gender && !ALLOWED_GENDERS.has(gender)) throw new ApiError(400, "invalid_gender", "The selected gender value is invalid.");

    const { data: duplicateCode, error: duplicateCodeError } = await supabase
      .from("patients")
      .select("id")
      .eq("organization_id", actor.organization_id)
      .eq("patient_code", patientCode)
      .maybeSingle();
    if (duplicateCodeError) throw duplicateCodeError;
    const { data: duplicateEmail, error: duplicateEmailError } = await supabase
      .from("patients")
      .select("id")
      .eq("organization_id", actor.organization_id)
      .eq("email", email)
      .maybeSingle();
    if (duplicateEmailError) throw duplicateEmailError;
    if (duplicateCode || duplicateEmail) throw new ApiError(409, "patient_exists", "A patient with this code or email already exists.");

    const { data: existingProfile, error: profileLookupError } = await supabase
      .from("profiles")
      .select("id, organization_id, role")
      .eq("email", email)
      .maybeSingle();
    if (profileLookupError) throw profileLookupError;

    let userId;
    let invitationSent = false;
    if (existingProfile) {
      if (existingProfile.role !== "patient") throw new ApiError(409, "email_in_use", "This email belongs to a staff account.");
      if (existingProfile.organization_id && existingProfile.organization_id !== actor.organization_id) {
        throw new ApiError(409, "email_in_use", "This patient account belongs to another organization.");
      }
      const { data: linkedPatient } = await supabase.from("patients").select("id").eq("user_id", existingProfile.id).maybeSingle();
      if (linkedPatient) throw new ApiError(409, "account_linked", "This patient login is already linked to a patient record.");
      userId = existingProfile.id;
    } else {
      const redirectTo = process.env.PATIENT_APP_REDIRECT_URL?.trim() || "neurovibe://auth/callback";
      const { data: invited, error: inviteError } = await supabase.auth.admin.inviteUserByEmail(email, {
        data: { full_name: fullName },
        redirectTo,
      });
      if (inviteError) throw new ApiError(inviteError.status || 400, "invite_failed", inviteError.message);
      if (!invited.user) throw new ApiError(500, "invite_failed", "Supabase did not return the invited patient account.");
      userId = invited.user.id;
      invitedUserId = userId;
      invitationSent = true;
    }

    const { error: profileUpdateError } = await supabase.from("profiles").update({
      organization_id: actor.organization_id,
      full_name: fullName,
      role: "patient",
      status: "active",
    }).eq("id", userId);
    if (profileUpdateError) throw profileUpdateError;

    const patientPayload = {
      organization_id: actor.organization_id,
      user_id: userId,
      doctor_id: actor.id,
      patient_code: patientCode,
      full_name: fullName,
      date_of_birth: body.date_of_birth || null,
      gender,
      phone: body.phone ? String(body.phone).trim() : null,
      email,
      program_status: "enrolled",
      consent_status: "pending",
    };
    const { data: patient, error: patientError } = await supabase.from("patients").insert(patientPayload).select("id, patient_code, full_name, email, user_id").single();
    if (patientError) throw patientError;

    const { error: auditError } = await supabase.from("audit_events").insert({
      organization_id: actor.organization_id,
      actor_id: actor.id,
      action: invitationSent ? "patient.invited" : "patient.account_linked",
      entity_type: "patient",
      entity_id: patient.id,
      summary: invitationSent ? `Patient ${fullName} enrolled and invited` : `Patient ${fullName} enrolled and linked to an existing account`,
      metadata: { user_id: userId },
    });
    if (auditError) console.error("Patient enrollment audit failed", auditError.code);

    invitedUserId = null;
    return json(request, 201, { patient, invitation_sent: invitationSent, account_linked: true });
  } catch (error) {
    if (invitedUserId) {
      try { await getSupabaseAdmin().auth.admin.deleteUser(invitedUserId); }
      catch (cleanupError) { console.error("Invited user cleanup failed", cleanupError); }
    }
    console.error("patient enrollment error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The patient account could not be created." });
  }
};
