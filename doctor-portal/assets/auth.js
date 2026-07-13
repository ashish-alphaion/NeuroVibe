import { supabase } from "./supabase-client.js";

export async function getAuthenticatedProfile() {
  const { data: { session }, error: sessionError } = await supabase.auth.getSession();
  if (sessionError || !session) return null;

  const { data: profile, error } = await supabase
    .from("profiles")
    .select("id, organization_id, email, full_name, role, status")
    .eq("id", session.user.id)
    .maybeSingle();

  if (error || !profile || profile.status !== "active" || !["admin", "doctor"].includes(profile.role)) {
    await supabase.auth.signOut();
    return null;
  }
  return { session, profile };
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
