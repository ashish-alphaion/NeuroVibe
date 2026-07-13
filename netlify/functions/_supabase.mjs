import { createHmac } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
import { getBearerToken, secureTokenEquals } from "./_shared.mjs";

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

export async function verifyDeviceRequest(request, deviceId) {
  const token = getBearerToken(request);
  if (!token) return false;

  // Temporary prototype compatibility. Remove this branch after every device
  // has been enrolled with its own database-backed credential.
  if (secureTokenEquals(token, process.env.DEVICE_API_TOKEN)) return true;

  const pepper = process.env.DEVICE_TOKEN_PEPPER?.trim();
  if (!pepper || !deviceId) return false;
  const tokenHash = createHmac("sha256", pepper).update(token, "utf8").digest("hex");
  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase
    .from("device_credentials")
    .select("id")
    .eq("device_id", deviceId)
    .eq("token_hash", tokenHash)
    .eq("status", "active")
    .or(`expires_at.is.null,expires_at.gt.${new Date().toISOString()}`)
    .maybeSingle();

  if (error || !data) return false;
  const { error: updateError } = await supabase
    .from("device_credentials")
    .update({ last_used_at: new Date().toISOString() })
    .eq("id", data.id);
  if (updateError) console.error("Device credential last_used_at update failed", updateError.code);
  return true;
}

export async function checkSupabaseConnection() {
  const supabase = getSupabaseAdmin();
  const { count, error } = await supabase
    .from("organizations")
    .select("id", { count: "exact", head: true });
  if (error) throw error;
  return { organizationCount: count ?? 0 };
}
