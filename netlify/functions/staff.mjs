import { ApiError, getBearerToken, json, options, readSmallJson } from "./_shared.mjs";
import { getSupabaseAdmin } from "./_supabase.mjs";

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") return json(request, 405, { error: "method_not_allowed" });

  try {
    const token = getBearerToken(request);
    if (!token) throw new ApiError(401, "unauthorized", "A doctor portal session is required.");
    const body = await readSmallJson(request);
    const email = String(body.email || "").trim().toLowerCase();
    const fullName = String(body.full_name || "").trim();
    const role = body.role === "admin" ? "admin" : "doctor";
    if (!email.includes("@") || fullName.length < 2) throw new ApiError(400, "invalid_staff", "A valid name and email are required.");

    const supabase = getSupabaseAdmin();
    const { data: userResult, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userResult.user) throw new ApiError(401, "unauthorized", "The portal session is invalid.");
    const { data: actor } = await supabase.from("profiles").select("organization_id, role, status").eq("id", userResult.user.id).maybeSingle();
    if (!actor || actor.role !== "admin" || actor.status !== "active" || !actor.organization_id) {
      throw new ApiError(403, "admin_required", "Only an active administrator may invite staff.");
    }

    const { data: invited, error: inviteError } = await supabase.auth.admin.inviteUserByEmail(email, { data: { full_name: fullName } });
    if (inviteError) throw new ApiError(inviteError.status || 400, "invite_failed", inviteError.message);
    const { error: profileError } = await supabase.from("profiles").update({
      organization_id: actor.organization_id,
      full_name: fullName,
      role,
      status: "invited",
    }).eq("id", invited.user.id);
    if (profileError) throw profileError;

    return json(request, 201, { invited: true, id: invited.user.id, email, role });
  } catch (error) {
    console.error("staff invite error", error);
    if (error instanceof ApiError) return json(request, error.status, { error: error.code, message: error.message });
    return json(request, 500, { error: "internal_error", message: "The staff invitation could not be created." });
  }
};
