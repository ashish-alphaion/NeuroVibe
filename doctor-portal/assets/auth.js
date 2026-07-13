import { supabase } from "./supabase-client.js";

export async function getAuthenticatedProfile() {
  const access = await inspectPortalAccess();
  if (!access.ok) {
    if (access.session) await supabase.auth.signOut();
    return null;
  }
  return { session: access.session, profile: access.profile };
}

export async function inspectPortalAccess() {
  const { data: { session }, error: sessionError } = await supabase.auth.getSession();
  if (sessionError) return { ok: false, code: "session_error", message: sessionError.message };
  if (!session) return { ok: false, code: "not_signed_in" };

  const { data: profile, error } = await supabase
    .from("profiles")
    .select("id, organization_id, email, full_name, role, status")
    .eq("id", session.user.id)
    .maybeSingle();

  if (error) return { ok: false, code: "profile_query_failed", message: error.message, session };
  if (!profile) return { ok: false, code: "profile_missing", session };
  if (profile.status !== "active") return { ok: false, code: "profile_inactive", profile, session };
  if (!["admin", "doctor"].includes(profile.role)) return { ok: false, code: "role_not_allowed", profile, session };
  if (!profile.organization_id) return { ok: false, code: "organization_missing", profile, session };
  return { ok: true, session, profile };
}

export async function requirePortalUser() {
  const auth = await getAuthenticatedProfile();
  if (!auth) {
    const returnTo = encodeURIComponent(location.pathname + location.search);
    location.replace(`/index.html?returnTo=${returnTo}`);
    return null;
  }
  return auth;
}

export async function signOut() {
  await supabase.auth.signOut();
  location.replace("/index.html");
}
