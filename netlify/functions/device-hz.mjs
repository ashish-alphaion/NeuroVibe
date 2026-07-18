import { ApiError, json, options, readSmallJson } from "./_shared.mjs";

const DEVICE_ID_PATTERN = /^[A-Za-z0-9._:-]{1,64}$/;

export default async (request) => {
  if (request.method === "OPTIONS") return options(request);
  if (request.method !== "POST") {
    return json(request, 405, { error: "method_not_allowed" });
  }

  try {
    const body = await readSmallJson(request, 2_048);
    const deviceId = String(body.device_id ?? "");
    const requestedHz = Number(body.requested_hz);
    const pwmValue = Number(body.pwm_value);
    const uptimeMs = Number(body.uptime_ms);

    if (!DEVICE_ID_PATTERN.test(deviceId)) {
      throw new ApiError(400, "invalid_device_id", "device_id is required and contains unsupported characters.");
    }
    if (!Number.isFinite(requestedHz) || requestedHz < 0 || requestedHz > 230) {
      throw new ApiError(400, "invalid_frequency", "requested_hz must be between 0 and 230.");
    }
    if (!Number.isInteger(pwmValue) || pwmValue < 0 || pwmValue > 255) {
      throw new ApiError(400, "invalid_pwm", "pwm_value must be an integer between 0 and 255.");
    }
    if (typeof body.running !== "boolean") {
      throw new ApiError(400, "invalid_running_state", "running must be true or false.");
    }
    if (!Number.isFinite(uptimeMs) || uptimeMs < 0) {
      throw new ApiError(400, "invalid_uptime", "uptime_ms must be zero or greater.");
    }

    const telemetry = {
      device_id: deviceId,
      requested_hz: requestedHz,
      estimated_hz: body.estimated_hz == null ? null : Number(body.estimated_hz),
      pwm_value: pwmValue,
      running: body.running,
      uptime_ms: Math.trunc(uptimeMs),
      received_at_utc: new Date().toISOString(),
    };

    if (
      telemetry.estimated_hz !== null &&
      (!Number.isFinite(telemetry.estimated_hz) ||
        telemetry.estimated_hz < 0 ||
        telemetry.estimated_hz > 230)
    ) {
      throw new ApiError(400, "invalid_estimated_frequency", "estimated_hz must be null or between 0 and 230.");
    }

    console.log("device-hz telemetry", JSON.stringify(telemetry));
    return json(request, 202, {
      acknowledged: true,
      telemetry,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return json(request, error.status, {
        error: error.code,
        message: error.message,
      });
    }
    console.error("device-hz error", error);
    return json(request, 500, {
      error: "internal_error",
      message: "Telemetry could not be accepted.",
    });
  }
};
