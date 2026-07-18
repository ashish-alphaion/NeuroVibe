/*
  NeuroVibe ESP32-C3 firmware

  Connections:
    GPIO1 -> DRV8833 IN1
    GPIO2 -> DRV8833 IN2
    GPIO3 -> DRV8833 IN3
    GPIO4 -> DRV8833 IN4

  Operation:
    1. Connect to the hard-coded 2.4 GHz Wi-Fi network.
    2. Start both motors at the 60 Hz requested control setting.
    3. Send the current setting as JSON to the Netlify API.
    4. Repeat the Netlify update every 10 seconds.

  Serial Monitor: 115200 baud
*/

#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>

// ---------- Hard-coded Wi-Fi ----------
const char WIFI_SSID[] = "iPhone";
const char WIFI_PASSWORD[] = "me768604";

// ---------- Netlify API ----------
const char NETLIFY_URL[] =
  "https://neurovibeapi.netlify.app/api/device-hz";
const char DEVICE_ID[] = "NV-ESP32C3-01";

// ---------- DRV8833 pins ----------
constexpr uint8_t MOTOR1_IN1 = 1;
constexpr uint8_t MOTOR1_IN2 = 2;
constexpr uint8_t MOTOR2_IN1 = 3;
constexpr uint8_t MOTOR2_IN2 = 4;

// ---------- Motor configuration ----------
constexpr float REQUESTED_HZ = 60.0f;
constexpr uint8_t MOTOR_PWM = 118;
constexpr uint32_t PWM_CARRIER_HZ = 20000;
constexpr uint8_t PWM_RESOLUTION_BITS = 8;

// ---------- Timing ----------
constexpr uint32_t SERIAL_BAUD = 115200;
constexpr unsigned long WIFI_TIMEOUT_MS = 30000;
constexpr unsigned long NETLIFY_INTERVAL_MS = 10000;
constexpr unsigned long SERIAL_INTERVAL_MS = 3000;

bool motorsRunning = false;
bool wifiWasConnected = false;
unsigned long lastNetlifyMs = 0;
unsigned long lastSerialMs = 0;

void stopMotors() {
  ledcWrite(MOTOR1_IN1, 0);
  ledcWrite(MOTOR1_IN2, 0);
  ledcWrite(MOTOR2_IN1, 0);
  ledcWrite(MOTOR2_IN2, 0);
  motorsRunning = false;
  Serial.println("[MOTOR] Both motors stopped.");
}

void startMotorsAt60Hz() {
  // Fast-decay forward PWM: IN1/IN3 receive PWM; IN2/IN4 stay low.
  ledcWrite(MOTOR1_IN1, MOTOR_PWM);
  ledcWrite(MOTOR1_IN2, 0);
  ledcWrite(MOTOR2_IN1, MOTOR_PWM);
  ledcWrite(MOTOR2_IN2, 0);
  motorsRunning = true;

  Serial.printf(
    "[MOTOR] Running | Requested frequency=%.1f Hz | PWM=%u/255\n",
    REQUESTED_HZ,
    MOTOR_PWM
  );
  Serial.println(
    "[NOTE] Exact mechanical vibration Hz requires calibration or a sensor."
  );
}

String makeTelemetryJson() {
  String json;
  json.reserve(180);
  json += "{";
  json += "\"device_id\":\"";
  json += DEVICE_ID;
  json += "\",";
  json += "\"requested_hz\":";
  json += String(REQUESTED_HZ, 1);
  json += ",";
  json += "\"estimated_hz\":null,";
  json += "\"pwm_value\":";
  json += String(motorsRunning ? MOTOR_PWM : 0);
  json += ",";
  json += "\"running\":";
  json += motorsRunning ? "true" : "false";
  json += ",";
  json += "\"uptime_ms\":";
  json += String(millis());
  json += "}";
  return json;
}

bool sendTelemetryToNetlify() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[NETLIFY] Data not sent: Wi-Fi is disconnected.");
    return false;
  }

  const String json = makeTelemetryJson();

  WiFiClientSecure secureClient;
  secureClient.setInsecure();  // Prototype only; validate a CA in production.

  HTTPClient https;
  if (!https.begin(secureClient, NETLIFY_URL)) {
    Serial.println("[NETLIFY] Failed to initialize HTTPS.");
    return false;
  }

  https.addHeader("Content-Type", "application/json");

  Serial.printf("[NETLIFY] POST %s\n", NETLIFY_URL);
  Serial.printf("[NETLIFY] JSON: %s\n", json.c_str());

  const int httpStatus = https.POST(json);
  const String response = httpStatus > 0 ? https.getString() : "";
  https.end();

  Serial.printf("[NETLIFY] HTTP status: %d\n", httpStatus);
  if (!response.isEmpty()) {
    Serial.printf("[NETLIFY] Response: %s\n", response.c_str());
  }

  if (httpStatus >= 200 && httpStatus < 300) {
    Serial.println("[NETLIFY] Telemetry accepted.");
    return true;
  }

  Serial.println("[NETLIFY] Telemetry rejected or request failed.");
  return false;
}

bool initializeMotorPins() {
  return
    ledcAttach(MOTOR1_IN1, PWM_CARRIER_HZ, PWM_RESOLUTION_BITS) &&
    ledcAttach(MOTOR1_IN2, PWM_CARRIER_HZ, PWM_RESOLUTION_BITS) &&
    ledcAttach(MOTOR2_IN1, PWM_CARRIER_HZ, PWM_RESOLUTION_BITS) &&
    ledcAttach(MOTOR2_IN2, PWM_CARRIER_HZ, PWM_RESOLUTION_BITS);
}

bool connectToWiFiOnce() {
  Serial.printf("[WIFI] Connecting to \"%s\"", WIFI_SSID);

  // This is the only WiFi.begin() call in the program.
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  const unsigned long startedMs = millis();
  while (
    WiFi.status() != WL_CONNECTED &&
    millis() - startedMs < WIFI_TIMEOUT_MS
  ) {
    Serial.print(".");
    delay(500);
  }
  Serial.println();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WIFI] Connection failed.");
    Serial.println("[CHECK] Confirm ALPHAION is available on 2.4 GHz.");
    return false;
  }

  Serial.println("[WIFI] Connected.");
  Serial.printf("[WIFI] IP address: %s\n", WiFi.localIP().toString().c_str());
  Serial.printf("[WIFI] Signal strength: %d dBm\n", WiFi.RSSI());
  return true;
}

void setup() {
  Serial.begin(SERIAL_BAUD);

  // Give the ESP32-C3 USB serial interface time to attach.
  const unsigned long serialStartedMs = millis();
  while (!Serial && millis() - serialStartedMs < 5000) {
    delay(10);
  }
  delay(1000);

  Serial.println();
  Serial.println("==============================================");
  Serial.println(" NeuroVibe single-file firmware");
  Serial.println(" Wi-Fi + 60 Hz control + Netlify JSON");
  Serial.println("==============================================");

  Serial.println("[STEP 1] Initializing GPIO1, GPIO2, GPIO3 and GPIO4.");
  if (!initializeMotorPins()) {
    Serial.println("[FATAL] PWM initialization failed.");
    while (true) {
      delay(1000);
    }
  }
  stopMotors();

  Serial.println("[STEP 2] Starting Wi-Fi station mode.");
  WiFi.mode(WIFI_STA);

  if (!connectToWiFiOnce()) {
    Serial.println("[SAFETY] Motors remain stopped.");
    Serial.println("[INFO] Reset the ESP32-C3 to try connecting again.");
    return;
  }

  wifiWasConnected = true;

  Serial.println("[STEP 3] Starting both motors at 60 Hz.");
  startMotorsAt60Hz();

  Serial.println("[STEP 4] Sending initial JSON to Netlify.");
  sendTelemetryToNetlify();
  lastNetlifyMs = millis();

  Serial.println("[READY] Setup complete.");
}

void loop() {
  const unsigned long now = millis();
  const bool wifiConnected = WiFi.status() == WL_CONNECTED;

  if (wifiWasConnected && !wifiConnected) {
    wifiWasConnected = false;
    Serial.println("[WIFI] Connection lost.");
    stopMotors();
  }

  if (
    wifiConnected &&
    now - lastNetlifyMs >= NETLIFY_INTERVAL_MS
  ) {
    lastNetlifyMs = now;
    sendTelemetryToNetlify();
  }

  if (now - lastSerialMs >= SERIAL_INTERVAL_MS) {
    lastSerialMs = now;
    Serial.printf(
      "[ALIVE] Wi-Fi=%s | Motors=%s | Requested Hz=%.1f | PWM=%u\n",
      wifiConnected ? "CONNECTED" : "DISCONNECTED",
      motorsRunning ? "RUNNING" : "STOPPED",
      REQUESTED_HZ,
      motorsRunning ? MOTOR_PWM : 0
    );
  }

  delay(10);
}
