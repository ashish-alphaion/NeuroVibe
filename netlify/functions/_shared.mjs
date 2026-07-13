import { timingSafeEqual } from "node:crypto";

const BASE_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};

export function corsHeaders(request) {
  const allowed = process.env.ALLOWED_ORIGIN?.trim();
  const origin = request.headers.get("origin");

  return {
    ...BASE_HEADERS,
    ...(allowed && origin === allowed
      ? {
          "access-control-allow-origin": allowed,
          vary: "Origin",
          "access-control-allow-headers": "authorization, content-type",
          "access-control-allow-methods": "GET, POST, OPTIONS",
        }
      : {}),
  };
}

export function json(request, status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: corsHeaders(request),
  });
}

export function options(request) {
  return new Response(null, { status: 204, headers: corsHeaders(request) });
}

function secureEquals(actual, expected) {
  const left = Buffer.from(actual ?? "");
  const right = Buffer.from(expected ?? "");
  return left.length === right.length && left.length > 0 && timingSafeEqual(left, right);
}

export function hasBearerToken(request, expected) {
  const token = getBearerToken(request);
  return Boolean(expected) && secureEquals(token, expected);
}

export function getBearerToken(request) {
  const header = request.headers.get("authorization") ?? "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

export function secureTokenEquals(actual, expected) {
  return Boolean(expected) && secureEquals(actual, expected);
}

export async function readSmallJson(request, maxBytes = 32_768) {
  const declaredLength = Number(request.headers.get("content-length") ?? 0);
  if (declaredLength > maxBytes) throw new ApiError(413, "payload_too_large", "Payload is too large.");

  const text = await request.text();
  if (Buffer.byteLength(text, "utf8") > maxBytes) {
    throw new ApiError(413, "payload_too_large", "Payload is too large.");
  }

  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("not an object");
    return parsed;
  } catch {
    throw new ApiError(400, "invalid_json", "Request body must be a JSON object.");
  }
}

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function requireSessionFields(body) {
  const required = [
    "session_id", "patient_id", "device_id", "assignment_id", "device_sequence",
    "started_at_utc", "ended_at_utc", "requested_hz", "pwm_value",
    "duration_seconds", "status", "sync_source",
  ];
  const missing = required.filter((key) => body[key] === undefined || body[key] === null || body[key] === "");
  if (missing.length) {
    throw new ApiError(400, "missing_fields", `Missing required fields: ${missing.join(", ")}.`);
  }

  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(String(body.session_id))) {
    throw new ApiError(400, "invalid_session_id", "session_id contains unsupported characters.");
  }
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(String(body.device_id))) {
    throw new ApiError(400, "invalid_device_id", "device_id contains unsupported characters.");
  }

  const hz = Number(body.requested_hz);
  if (!Number.isFinite(hz) || hz < 0 || hz > 230) {
    throw new ApiError(400, "invalid_frequency", "requested_hz must be between 0 and 230 Hz.");
  }
  const duration = Number(body.duration_seconds);
  if (!Number.isFinite(duration) || duration < 0 || duration > 86_400) {
    throw new ApiError(400, "invalid_duration", "duration_seconds must be between 0 and 86400.");
  }
}
