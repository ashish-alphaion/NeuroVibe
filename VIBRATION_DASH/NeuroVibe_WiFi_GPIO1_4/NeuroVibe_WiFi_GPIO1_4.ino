// NeuroVibe ESP32-C3 firmware
// GPIO1-4 -> DRV8833 IN1-IN4
// Connects to hard-coded Wi-Fi and POSTs the played Hz as JSON to Netlify.

#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>

// ---------- Wi-Fi configuration ----------
const char *WIFI_SSID = "ALPHAION";
const char *WIFI_PASSWORD = "AlphA2022#";

// ---------- Netlify configuration ----------
const char *NETLIFY_URL = "https://neurovibeapi.netlify.app/api/device-hz";
const char *DEVICE_ID = "NV-ESP32C3-01";

// ---------- DRV8833 connections ----------
constexpr uint8_t IN1 = 1;
constexpr uint8_t IN2 = 2;
constexpr uint8_t IN3 = 3;
constexpr uint8_t IN4 = 4;

// PWM carrier frequency is not the physical vibration frequency.
constexpr uint32_t PWM_CARRIER_HZ = 20000;
constexpr uint8_t PWM_RESOLUTION_BITS = 8;
constexpr uint8_t MIN_RUNNING_PWM = 70;
constexpr uint8_t MAX_PWM = 255;
constexpr float MAX_REQUESTED_HZ = 230.0f;
constexpr float STARTUP_HZ = 60.0f;

constexpr unsigned long SERIAL_BAUD = 115200;
constexpr unsigned long TELEMETRY_INTERVAL_MS = 10000;
constexpr unsigned long SERIAL_REPORT_INTERVAL_MS = 3000;

float requestedHz = 0.0f;
uint8_t currentPwm = 0;
unsigned long lastTelemetryMs = 0;
unsigned long lastSerialReportMs = 0;

void stopMotors() {
  ledcWrite(IN1, 0);
  ledcWrite(IN2, 0);
  ledcWrite(IN3, 0);
  ledcWrite(IN4, 0);
  requestedHz = 0.0f;
  currentPwm = 0;
  Serial.println("[MOTOR] STOPPED | Requested Hz=0 | PWM=0");
}

uint8_t hzToPwm(float hz) {
  if (hz <= 0.0f) return 0;

  const float scaled =
    MIN_RUNNING_PWM +
    ((hz - 1.0f) / (MAX_REQUESTED_HZ - 1.0f)) *
      (MAX_PWM - MIN_RUNNING_PWM);

  return static_cast<uint8_t>(constrain(round(scaled), MIN_RUNNING_PWM, MAX_PWM));
}

void playHz(float hz) {
  hz = constrain(hz, 0.0f, MAX_REQUESTED_HZ);
  if (hz <= 0.0f) {
    stopMotors();
    return;
  }

  requestedHz = hz;
  currentPwm = hzToPwm(hz);

  // Forward PWM: IN1/IN3 receive PWM while IN2/IN4 remain low.
  ledcWrite(IN1, currentPwm);
  ledcWrite(IN2, 0);
  ledcWrite(IN3, currentPwm);
  ledcWrite(IN4, 0);

  Serial.printf(
    "[MOTOR] RUNNING | Requested Hz=%.1f | PWM=%u/255\n",
    requestedHz,
    currentPwm
  );
  Serial.println(
    "[NOTE] Requested Hz is an estimated control value; exact mechanical Hz requires a sensor."
  );
}

String buildTelemetryJson() {
  String json = "{";
  json += "\"device_id\":\"" + String(DEVICE_ID) + "\",";
  json += "\"requested_hz\":" + String(requestedHz, 1) + ",";
  json += "\"estimated_hz\":null,";
  json += "\"pwm_value\":" + String(currentPwm) + ",";
  json += "\"running\":";
  json += requestedHz > 0.0f ? "true" : "false";
  json += ",";
  json += "\"uptime_ms\":" + String(millis());
  json += "}";
  return json;
}

bool sendHzToNetlify() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[NETLIFY] Not sent: Wi-Fi is not connected.");
    return false;
  }

  const String payload = buildTelemetryJson();
  WiFiClientSecure secureClient;

  // Prototype TLS mode. For production, install and validate the CA certificate.
  secureClient.setInsecure();

  HTTPClient https;
  Serial.printf("[NETLIFY] POST %s\n", NETLIFY_URL);
  Serial.printf("[NETLIFY] JSON %s\n", payload.c_str());

  if (!https.begin(secureClient, NETLIFY_URL)) {
    Serial.println("[NETLIFY] Could not start the HTTPS request.");
    return false;
  }

  https.addHeader("Content-Type", "application/json");
  const int statusCode = https.POST(payload);
  const String response = statusCode > 0 ? https.getString() : "";
  https.end();

  Serial.printf("[NETLIFY] HTTP status: %d\n", statusCode);