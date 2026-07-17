import { createClient } from "@supabase/supabase-js";

let adminClient;

export function getSupabaseAdmin() {
  const url = process.env.SUPABASE_URL?.trim();
  const secret = process.env.SUPABASE_SECRET_KEY?.trim();
  if (!url || !secret) throw new Error("supabase_environment_missing");

  if (!adminClient) {
    adminClient = createClient(url, secret, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
    });
  }
  return adminClient;
}

export async function checkSupabaseConnection() {
  const supabase = getSupabaseAdmin();
  const { count, error } = await supabase
    .from("organizations")
    .select("id", { count: "exact", head: true });
  if (error) throw error;
  return { organizationCount: count ?? 0 };
}
