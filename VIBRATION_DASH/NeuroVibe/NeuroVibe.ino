// NeuroSense local MVP firmware
// Hardware: ESP32-C3 Super Mini + DRV8833 + 2 ERM coin motors
//
// Features:
// - Stable device identity derived from the ESP32 eFuse MAC and saved in NVS
// - BLE setup, control, status and offline-record transfer
// - Persistent Wi-Fi, server, assignment and care-plan configuration
// - Strict 0-230 Hz system limit plus doctor-defined patient limits
// - LittleFS-backed session queue that survives reboot and loss of connectivity
// - Direct Wi-Fi upload to the NeuroVibe API with duplicate-safe session IDs
//
// Required library: ArduinoJson 7.x
// Required ESP32 board package: Espressif Arduino-ESP32 3.x
//
// IMPORTANT: The ERM motor frequency is estimated from calibrated PWM. Exact
// vibration frequency requires an accelerometer and closed-loop control.

#include <Arduino.h>
#include <ArduinoJson.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <Preferences.h>
#include <WiFi.h>
#include <time.h>

// -----------------------------------------------------------------------------
// Hardware and motor calibration
// -----------------------------------------------------------------------------

#define M1_IN1 2
#define M1_IN2 3
#define M2_IN1 4
#define M2_IN2 5

#define PWM_FREQ 20000
#define PWM_RESOLUTION 8
#define PWM_MAX 255

constexpr float SYSTEM_MIN_HZ = 0.0f;
constexpr float SYSTEM_MAX_HZ = 230.0f;
constexpr float CALIBRATION_MIN_RUNNING_HZ = 60.0f;
constexpr int PWM_AT_MIN_RUNNING_HZ = 70;
constexpr int PWM_AT_MAX_RUNNING_HZ = 255;

// -----------------------------------------------------------------------------
// BLE protocol
// -----------------------------------------------------------------------------

// Custom NeuroSense BLE UART-style service.
#define NEUROSENSE_SERVICE_UUID "7b1e0001-7f34-4fd8-a912-6c38ef4a5201"
#define COMMAND_CHARACTERISTIC_UUID "7b1e0002-7f34-4fd8-a912-6c38ef4a5201"
#define RESPONSE_CHARACTERISTIC_UUID "7b1e0003-7f34-4fd8-a912-6c38ef4a5201"

constexpr size_t MAX_BLE_COMMAND_BYTES = 512;
constexpr size_t BLE_RECORD_CHUNK_BYTES = 150;

BLEServer *bleServer = nullptr;
BLECharacteristic *responseCharacteristic = nullptr;
bool bleConnected = false;
bool previousBleConnected = false;
bool stopAfterBleDisconnect = false;
bool pendingTransferRequested = false;
bool wifiConnectRequested = false;
bool syncRequested = false;

// -----------------------------------------------------------------------------
// Persistent configuration
// -----------------------------------------------------------------------------

Preferences preferences;

struct DeviceConfiguration {
  String deviceId;
  String displayName;
  String wifiSsid;
  String wifiPassword;
  String apiBaseUrl;
  String apiToken;
  String patientId;
  String assignmentId;
  float minHz = CALIBRATION_MIN_RUNNING_HZ;
  float targetHz = 150.0f;
  float maxHz = SYSTEM_MAX_HZ;
  uint32_t maxDurationSeconds = 900;
  bool manualControlAllowed = true;
};

DeviceConfiguration config;

// -----------------------------------------------------------------------------
// Session state
// -----------------------------------------------------------------------------

struct ActiveSession {
  bool running = false;
  String sessionId;
  String scheduleId;
  String startedAtUtc;
  uint64_t startedAtMillis = 0;
  uint32_t durationLimitSeconds = 0;
  float requestedHz = 0.0f;
  int pwmValue = 0;
};

ActiveSession activeSession;
uint32_t deviceSequence = 0;

// -----------------------------------------------------------------------------
// Queue and timing
// -----------------------------------------------------------------------------

constexpr char QUEUE_FILE[] = "/session_queue.jsonl";
constexpr char QUEUE_TEMP_FILE[] = "/session_queue.tmp";
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 12000;
constexpr uint32_t WIFI_RETRY_INTERVAL_MS = 30000;
constexpr uint32_t SYNC_INTERVAL_MS = 15000;

uint64_t lastWiFiAttemptMillis = 0;
uint64_t lastSyncAttemptMillis = 0;

// -----------------------------------------------------------------------------
// Forward declarations
// -----------------------------------------------------------------------------

void processBleCommand(const String &payload);
void notifyJson(JsonDocument &document);
void notifyBlePayload(const String &payload);
void notifyError(const String &command, const String &code, const String &message);
void notifyOk(const String &command, JsonDocument *data = nullptr);
void notifyStatus();
void sendPendingRecordsOverBle();
bool connectWiFi(bool forceAttempt = false);
bool uploadOldestQueuedSession();
bool removeQueuedSessionById(const String &sessionId);
void stopAndRecordSession(const String &reason, const String &status);
void stopMotors();

// -----------------------------------------------------------------------------
// BLE callbacks
// -----------------------------------------------------------------------------

class NeuroSenseServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    bleConnected = true;
    Serial.println("BLE client connected");
  }

  void onDisconnect(BLEServer *server) override {
    bleConnected = false;
    if (activeSession.running) {
      // The MVP session is app-controlled. Stop safely when control is lost.
      stopAfterBleDisconnect = true;
    }
    Serial.println("BLE client disconnected");
  }
};

class NeuroSenseCommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String payload = characteristic->getValue();
    if (payload.length() == 0) return;
    if (payload.length() > MAX_BLE_COMMAND_BYTES) {
      notifyError("unknown", "payload_too_large", "BLE command exceeds 512 bytes.");
      return;
    }
    processBleCommand(payload);
  }
};

// -----------------------------------------------------------------------------
// Setup and main loop
// -----------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  delay(500);

  ledcAttach(M1_IN1, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(M1_IN2, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(M2_IN1, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(M2_IN2, PWM_FREQ, PWM_RESOLUTION);
  stopMotors();

  if (!LittleFS.begin(true)) {
    Serial.println("FATAL: LittleFS could not be mounted");
  }

  loadConfiguration();
  initializeDeviceIdentity();
  initializeBle();

  if (config.wifiSsid.length() > 0) connectWiFi(true);

  Serial.println();
  Serial.println("========================================");
  Serial.println("NeuroSense firmware started");
  Serial.printf("Device ID: %s\n", config.deviceId.c_str());
  Serial.printf("BLE name: %s\n", config.displayName.c_str());
  Serial.printf("Pending sessions: %u\n", countQueuedSessions());
  Serial.println("========================================");
}

void loop() {
  const uint64_t currentMillis = millis();

  if (stopAfterBleDisconnect) {
    stopAfterBleDisconnect = false;
    stopAndRecordSession("ble_disconnected", "interrupted");
  }

  if (activeSession.running) {
    const uint64_t elapsedMs = currentMillis - activeSession.startedAtMillis;
    if (elapsedMs >= static_cast<uint64_t>(activeSession.durationLimitSeconds) * 1000ULL) {
      stopAndRecordSession("duration_complete", "completed");
    }
  }

  if (pendingTransferRequested && bleConnected && !activeSession.running) {
    pendingTransferRequested = false;
    sendPendingRecordsOverBle();
  }

  if (wifiConnectRequested && !activeSession.running) {
    wifiConnectRequested = false;
    const bool connected = connectWiFi(true);
    if (bleConnected) {
      JsonDocument result;
      result["type"] = "wifi_result";
      result["connected"] = connected;
      result["ssid"] = config.wifiSsid;
      notifyJson(result);
    }
  }

  if (syncRequested && !activeSession.running) {
    syncRequested = false;
    const bool uploaded = uploadOldestQueuedSession();
    if (bleConnected) {
      JsonDocument result;
      result["type"] = "sync_result";
      result["uploaded"] = uploaded;
      result["pending_sessions"] = countQueuedSessions();
      notifyJson(result);
    }
  }

  if (!activeSession.running && WiFi.status() != WL_CONNECTED &&
      config.wifiSsid.length() > 0 && currentMillis - lastWiFiAttemptMillis >= WIFI_RETRY_INTERVAL_MS) {
    connectWiFi(true);
  }

  if (!activeSession.running && WiFi.status() == WL_CONNECTED &&
      currentMillis - lastSyncAttemptMillis >= SYNC_INTERVAL_MS) {
    lastSyncAttemptMillis = currentMillis;
    uploadOldestQueuedSession();
  }

  if (!bleConnected && previousBleConnected) {
    delay(250);
    bleServer->startAdvertising();
    previousBleConnected = false;
  } else if (bleConnected && !previousBleConnected) {
    previousBleConnected = true;
  }

  delay(10);
}

// -----------------------------------------------------------------------------
// Device identity and configuration
// -----------------------------------------------------------------------------

void initializeDeviceIdentity() {
  if (config.deviceId.length() > 0 && config.displayName.length() > 0) return;

  const uint64_t chipId = ESP.getEfuseMac();
  char suffix[7];
  snprintf(suffix, sizeof(suffix), "%06llX", chipId & 0xFFFFFFULL);
  config.deviceId = String("DEV-NEUROSENSE-") + suffix;
  config.displayName = String("NeuroSense-") + suffix;

  preferences.begin("neurosense", false);
  preferences.putString("device_id", config.deviceId);
  preferences.putString("display_name", config.displayName);
  preferences.end();
}

void loadConfiguration() {
  preferences.begin("neurosense", true);
  config.deviceId = preferences.getString("device_id", "");
  config.displayName = preferences.getString("display_name", "");
  config.wifiSsid = preferences.getString("wifi_ssid", "");
  config.wifiPassword = preferences.getString("wifi_pass", "");
  config.apiBaseUrl = preferences.getString("api_url", "");
  config.apiToken = preferences.getString("api_token", "");
  config.patientId = preferences.getString("patient_id", "");
  config.assignmentId = preferences.getString("assign_id", "");
  config.minHz = preferences.getFloat("min_hz", CALIBRATION_MIN_RUNNING_HZ);
  config.targetHz = preferences.getFloat("target_hz", 150.0f);
  config.maxHz = preferences.getFloat("max_hz", SYSTEM_MAX_HZ);
  config.maxDurationSeconds = preferences.getUInt("max_duration", 900);
  config.manualControlAllowed = preferences.getBool("manual", true);
  deviceSequence = preferences.getUInt("sequence", 0);
  preferences.end();
}

void saveWiFiConfiguration(const String &ssid, const String &password) {
  config.wifiSsid = ssid;
  config.wifiPassword = password;
  preferences.begin("neurosense", false);
  preferences.putString("wifi_ssid", ssid);
  preferences.putString("wifi_pass", password);
  preferences.end();
}

void saveServerConfiguration(const String &apiBaseUrl, const String &apiToken) {
  config.apiBaseUrl = apiBaseUrl;
  config.apiToken = apiToken;
  while (config.apiBaseUrl.endsWith("/")) config.apiBaseUrl.remove(config.apiBaseUrl.length() - 1);
  preferences.begin("neurosense", false);
  preferences.putString("api_url", config.apiBaseUrl);
  preferences.putString("api_token", config.apiToken);
  preferences.end();
}

void saveAssignment(const String &patientId, const String &assignmentId) {
  config.patientId = patientId;
  config.assignmentId = assignmentId;
  preferences.begin("neurosense", false);
  preferences.putString("patient_id", patientId);
  preferences.putString("assign_id", assignmentId);
  preferences.end();
}

void saveCarePlan(float minHz, float targetHz, float maxHz,
                  uint32_t maxDurationSeconds, bool manualControlAllowed) {
  config.minHz = minHz;
  config.targetHz = targetHz;
  config.maxHz = maxHz;
  config.maxDurationSeconds = maxDurationSeconds;
  config.manualControlAllowed = manualControlAllowed;
  preferences.begin("neurosense", false);
  preferences.putFloat("min_hz", minHz);
  preferences.putFloat("target_hz", targetHz);
  preferences.putFloat("max_hz", maxHz);
  preferences.putUInt("max_duration", maxDurationSeconds);
  preferences.putBool("manual", manualControlAllowed);
  preferences.end();
}

void incrementDeviceSequence() {
  deviceSequence++;
  preferences.begin("neurosense", false);
  preferences.putUInt("sequence", deviceSequence);
  preferences.end();
}

// -----------------------------------------------------------------------------
// BLE initialization and notifications
// -----------------------------------------------------------------------------

void initializeBle() {
  BLEDevice::init(config.displayName.c_str());
  BLEDevice::setMTU(247);

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new NeuroSenseServerCallbacks());

  BLEService *service = bleServer->createService(NEUROSENSE_SERVICE_UUID);

  BLECharacteristic *commandCharacteristic = service->createCharacteristic(
    COMMAND_CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  commandCharacteristic->setCallbacks(new NeuroSenseCommandCallbacks());

  responseCharacteristic = service->createCharacteristic(
    RESPONSE_CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  responseCharacteristic->addDescriptor(new BLE2902());
  responseCharacteristic->setValue("{\"type\":\"ready\"}");

  service->start();
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(NEUROSENSE_SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();
}

void notifyJson(JsonDocument &document) {
  if (!bleConnected || responseCharacteristic == nullptr) return;
  String output;
  serializeJson(document, output);
  notifyBlePayload(output);
}

void notifyBlePayload(const String &payload) {
  if (!bleConnected || responseCharacteristic == nullptr) return;
  if (payload.length() <= BLE_RECORD_CHUNK_BYTES) {
    responseCharacteristic->setValue(payload.c_str());
    responseCharacteristic->notify();
    return;
  }

  String begin = String("#JSONBEGIN:") + String(payload.length());
  responseCharacteristic->setValue(begin.c_str());
  responseCharacteristic->notify();
  delay(20);
  for (size_t offset = 0; offset < payload.length() && bleConnected; offset += BLE_RECORD_CHUNK_BYTES) {
    const String chunk = payload.substring(offset, min(offset + BLE_RECORD_CHUNK_BYTES, payload.length()));
    responseCharacteristic->setValue(chunk.c_str());
    responseCharacteristic->notify();
    delay(20);
  }
  if (bleConnected) {
    responseCharacteristic->setValue("#JSONEND");
    responseCharacteristic->notify();
  }
}

void notifyError(const String &command, const String &code, const String &message) {
  JsonDocument response;
  response["type"] = "error";
  response["command"] = command;
  response["code"] = code;
  response["message"] = message;
  notifyJson(response);
}

void notifyOk(const String &command, JsonDocument *data) {
  JsonDocument response;
  response["type"] = "ok";
  response["command"] = command;
  if (data != nullptr) response["data"] = data->as<JsonVariantConst>();
  notifyJson(response);
}

void notifyStatus() {
  JsonDocument response;
  response["type"] = "status";
  response["device_id"] = config.deviceId;
  response["display_name"] = config.displayName;
  response["ble_connected"] = bleConnected;
  response["wifi_connected"] = WiFi.status() == WL_CONNECTED;
  response["wifi_ssid"] = config.wifiSsid;
  response["provisioned"] = config.patientId.length() > 0 && config.assignmentId.length() > 0;
  response["patient_id"] = config.patientId;
  response["assignment_id"] = config.assignmentId;
  response["min_hz"] = config.minHz;
  response["target_hz"] = config.targetHz;
  response["max_hz"] = config.maxHz;
  response["max_duration_seconds"] = config.maxDurationSeconds;
  response["manual_control_allowed"] = config.manualControlAllowed;
  response["session_running"] = activeSession.running;
  response["current_hz"] = activeSession.requestedHz;
  response["pending_sessions"] = countQueuedSessions();
  notifyJson(response);
}

// -----------------------------------------------------------------------------
// BLE command handling
// -----------------------------------------------------------------------------

void processBleCommand(const String &payload) {
  JsonDocument command;
  DeserializationError error = deserializeJson(command, payload);
  if (error) {
    notifyError("unknown", "invalid_json", "Command must be valid JSON.");
    return;
  }

  const String type = command["type"] | "";
  if (type.length() == 0) {
    notifyError("unknown", "missing_type", "Command requires a type field.");
    return;
  }

  if (type == "get_info" || type == "get_status") {
    notifyStatus();
    return;
  }

  if (type == "set_wifi") {
    const String ssid = command["ssid"] | "";
    const String password = command["password"] | "";
    if (ssid.length() == 0) {
      notifyError(type, "missing_ssid", "Wi-Fi SSID is required.");
      return;
    }
    saveWiFiConfiguration(ssid, password);
    wifiConnectRequested = true;
    JsonDocument data;
    data["saved"] = true;
    data["connection_pending"] = true;
    data["ssid"] = ssid;
    notifyOk(type, &data);
    return;
  }

  if (type == "set_server") {
    const String baseUrl = command["api_base_url"] | "";
    const String token = command["api_token"] | "";
    if (baseUrl.length() == 0 || token.length() == 0) {
      notifyError(type, "missing_server_configuration", "api_base_url and api_token are required.");
      return;
    }
    saveServerConfiguration(baseUrl, token);
    notifyOk(type);
    return;
  }

  if (type == "set_assignment") {
    const String patientId = command["patient_id"] | "";
    const String assignmentId = command["assignment_id"] | "";
    if (patientId.length() == 0 || assignmentId.length() == 0) {
      notifyError(type, "missing_assignment", "patient_id and assignment_id are required.");
      return;
    }
    saveAssignment(patientId, assignmentId);
    notifyOk(type);
    return;
  }

  if (type == "set_limits") {
    const float minHz = command["min_hz"] | config.minHz;
    const float targetHz = command["target_hz"] | config.targetHz;
    const float maxHz = command["max_hz"] | config.maxHz;
    const uint32_t maxDuration = command["max_duration_seconds"] | config.maxDurationSeconds;
    const bool manual = command["manual_control_allowed"] | config.manualControlAllowed;
    if (!validFrequencyRange(minHz, targetHz, maxHz) || maxDuration == 0) {
      notifyError(type, "invalid_limits", "Limits require 0 <= min <= target <= max <= 230 and duration > 0.");
      return;
    }
    saveCarePlan(minHz, targetHz, maxHz, maxDuration, manual);
    notifyOk(type);
    return;
  }

  if (type == "start_session") {
    if (activeSession.running) {
      notifyError(type, "session_already_running", "A session is already running.");
      return;
    }
    if (config.patientId.length() == 0 || config.assignmentId.length() == 0) {
      notifyError(type, "device_not_assigned", "Patient and assignment configuration are required.");
      return;
    }
    const float requestedHz = command["target_hz"] | config.targetHz;
    const uint32_t durationSeconds = command["duration_seconds"] | config.maxDurationSeconds;
    if (requestedHz <= 0.0f || !frequencyAllowed(requestedHz)) {
      notifyError(type, "frequency_not_allowed", "Target frequency is outside the configured care plan.");
      return;
    }
    if (durationSeconds == 0 || durationSeconds > config.maxDurationSeconds) {
      notifyError(type, "duration_not_allowed", "Duration is outside the configured care plan.");
      return;
    }
    startSession(command["schedule_id"] | "", requestedHz, durationSeconds);
    JsonDocument data;
    data["session_id"] = activeSession.sessionId;
    data["target_hz"] = activeSession.requestedHz;
    data["duration_seconds"] = activeSession.durationLimitSeconds;
    notifyOk(type, &data);
    return;
  }

  if (type == "set_frequency") {
    if (!activeSession.running) {
      notifyError(type, "no_active_session", "No session is running.");
      return;
    }
    if (!config.manualControlAllowed) {
      notifyError(type, "manual_control_disabled", "Manual frequency control is disabled by the care plan.");
      return;
    }
    const float targetHz = command["hz"] | -1.0f;
    if (!frequencyAllowed(targetHz)) {
      notifyError(type, "frequency_not_allowed", "Frequency is outside the configured care plan.");
      return;
    }
    if (targetHz == 0.0f) {
      stopAndRecordSession("frequency_zero", "interrupted");
      JsonDocument data;
      data["hz"] = 0;
      data["session_stopped"] = true;
      notifyOk(type, &data);
      return;
    }
    applyVibrationFrequency(targetHz);
    JsonDocument data;
    data["hz"] = activeSession.requestedHz;
    data["pwm"] = activeSession.pwmValue;
    notifyOk(type, &data);
    return;
  }

  if (type == "stop_session") {
    if (!activeSession.running) {
      notifyError(type, "no_active_session", "No session is running.");
      return;
    }
    const String reason = command["reason"] | "patient_stop";
    stopAndRecordSession(reason, "interrupted");
    notifyOk(type);
    return;
  }

  if (type == "emergency_stop") {
    if (activeSession.running) stopAndRecordSession("emergency_stop", "interrupted");
    else stopMotors();
    notifyOk(type);
    return;
  }

  if (type == "get_pending") {
    pendingTransferRequested = true;
    JsonDocument data;
    data["pending_sessions"] = countQueuedSessions();
    notifyOk(type, &data);
    return;
  }

  if (type == "ack_session") {
    const String sessionId = command["session_id"] | "";
    if (sessionId.length() == 0) {
      notifyError(type, "missing_session_id", "session_id is required.");
      return;
    }
    const bool removed = removeQueuedSessionById(sessionId);
    JsonDocument data;
    data["removed"] = removed;
    data["pending_sessions"] = countQueuedSessions();
    notifyOk(type, &data);
    return;
  }

  if (type == "sync_now") {
    syncRequested = true;
    JsonDocument data;
    data["sync_pending"] = true;
    data["pending_sessions"] = countQueuedSessions();
    notifyOk(type, &data);
    return;
  }

  if (type == "factory_reset") {
    const String confirmation = command["confirmation"] | "";
    if (activeSession.running) {
      notifyError(type, "session_running", "Stop the active session before reset.");
      return;
    }
    if (confirmation != "RESET_NEUROSENSE") {
      notifyError(type, "confirmation_required", "Use confirmation RESET_NEUROSENSE.");
      return;
    }
    notifyOk(type);
    delay(200);
    factoryReset();
    return;
  }

  notifyError(type, "unknown_command", "The command type is not supported.");
}

// -----------------------------------------------------------------------------
// Session and motor control
// -----------------------------------------------------------------------------

bool validFrequencyRange(float minHz, float targetHz, float maxHz) {
  return minHz >= SYSTEM_MIN_HZ && minHz <= targetHz && targetHz <= maxHz && maxHz <= SYSTEM_MAX_HZ;
}

bool frequencyAllowed(float frequencyHz) {
  if (frequencyHz < SYSTEM_MIN_HZ || frequencyHz > SYSTEM_MAX_HZ) return false;
  if (frequencyHz == 0.0f) return true;
  return frequencyHz >= config.minHz && frequencyHz <= config.maxHz;
}

void startSession(const String &scheduleId, float targetHz, uint32_t durationSeconds) {
  incrementDeviceSequence();
  activeSession.running = true;
  activeSession.sessionId = String("SES-") + config.deviceId + "-" + String(deviceSequence);
  activeSession.scheduleId = scheduleId;
  activeSession.startedAtUtc = isoTimestamp();
  activeSession.startedAtMillis = millis();
  activeSession.durationLimitSeconds = durationSeconds;
  applyVibrationFrequency(targetHz);
  Serial.printf("Session started: %s at %.1f Hz\n", activeSession.sessionId.c_str(), targetHz);
}

void applyVibrationFrequency(float targetHz) {
  if (!frequencyAllowed(targetHz)) return;
  if (targetHz == 0.0f) {
    stopMotors();
    activeSession.requestedHz = 0.0f;
    activeSession.pwmValue = 0;
    return;
  }
  const int pwm = frequencyToPwm(targetHz);
  runBothMotors(pwm);
  activeSession.requestedHz = targetHz;
  activeSession.pwmValue = pwm;
}

void stopAndRecordSession(const String &reason, const String &status) {
  if (!activeSession.running) {
    stopMotors();
    return;
  }

  stopMotors();
  const uint32_t durationSeconds = static_cast<uint32_t>((millis() - activeSession.startedAtMillis) / 1000ULL);

  JsonDocument session;
  session["session_id"] = activeSession.sessionId;
  session["patient_id"] = config.patientId;
  session["device_id"] = config.deviceId;
  session["assignment_id"] = config.assignmentId;
  if (activeSession.scheduleId.length() > 0) session["schedule_id"] = activeSession.scheduleId;
  session["device_sequence"] = deviceSequence;
  session["started_at_utc"] = activeSession.startedAtUtc;
  session["ended_at_utc"] = isoTimestamp();
  session["requested_hz"] = activeSession.requestedHz;
  session["estimated_hz"] = pwmToEstimatedFrequency(activeSession.pwmValue);
  session["measured_hz"] = nullptr;
  session["pwm_value"] = activeSession.pwmValue;
  session["duration_seconds"] = durationSeconds;
  session["status"] = status;
  session["completion_reason"] = reason;
  session["sync_source"] = "device_wifi";
  session["timestamp_source"] = time(nullptr) > 100000 ? "ntp" : "uptime_fallback";

  String record;
  serializeJson(session, record);
  const bool queued = appendQueuedSession(record);

  Serial.printf("Session stopped: %s (%s), queued=%s\n",
                activeSession.sessionId.c_str(), reason.c_str(), queued ? "yes" : "no");

  activeSession = ActiveSession();

  if (bleConnected) {
    JsonDocument event;
    event["type"] = "session_saved";
    event["queued"] = queued;
    event["pending_sessions"] = countQueuedSessions();
    notifyJson(event);
  }
}

int frequencyToPwm(float frequencyHz) {
  if (frequencyHz <= 0.0f) return 0;
  if (frequencyHz < CALIBRATION_MIN_RUNNING_HZ) {
    return constrain(
      static_cast<int>(roundf(frequencyHz * PWM_AT_MIN_RUNNING_HZ / CALIBRATION_MIN_RUNNING_HZ)),
      1, PWM_AT_MIN_RUNNING_HZ
    );
  }
  const float ratio = (frequencyHz - CALIBRATION_MIN_RUNNING_HZ) /
                      (SYSTEM_MAX_HZ - CALIBRATION_MIN_RUNNING_HZ);
  return constrain(
    static_cast<int>(roundf(PWM_AT_MIN_RUNNING_HZ + ratio * (PWM_AT_MAX_RUNNING_HZ - PWM_AT_MIN_RUNNING_HZ))),
    PWM_AT_MIN_RUNNING_HZ, PWM_MAX
  );
}

float pwmToEstimatedFrequency(int pwmValue) {
  if (pwmValue <= 0) return 0.0f;
  if (pwmValue < PWM_AT_MIN_RUNNING_HZ) {
    return static_cast<float>(pwmValue) * CALIBRATION_MIN_RUNNING_HZ / PWM_AT_MIN_RUNNING_HZ;
  }
  const float ratio = static_cast<float>(pwmValue - PWM_AT_MIN_RUNNING_HZ) /
                      static_cast<float>(PWM_AT_MAX_RUNNING_HZ - PWM_AT_MIN_RUNNING_HZ);
  return CALIBRATION_MIN_RUNNING_HZ + ratio * (SYSTEM_MAX_HZ - CALIBRATION_MIN_RUNNING_HZ);
}

void runBothMotors(int pwmValue) {
  pwmValue = constrain(pwmValue, 0, PWM_MAX);
  ledcWrite(M1_IN1, pwmValue);
  ledcWrite(M1_IN2, 0);
  ledcWrite(M2_IN1, pwmValue);
  ledcWrite(M2_IN2, 0);
}

void stopMotors() {
  ledcWrite(M1_IN1, 0);
  ledcWrite(M1_IN2, 0);
  ledcWrite(M2_IN1, 0);
  ledcWrite(M2_IN2, 0);
}

// -----------------------------------------------------------------------------
// LittleFS queue
// -----------------------------------------------------------------------------

bool appendQueuedSession(const String &record) {
  File file = LittleFS.open(QUEUE_FILE, FILE_APPEND);
  if (!file) return false;
  const size_t written = file.println(record);
  file.flush();
  file.close();
  return written == record.length() + 2 || written == record.length() + 1;
}

uint32_t countQueuedSessions() {
  File file = LittleFS.open(QUEUE_FILE, FILE_READ);
  if (!file) return 0;
  uint32_t count = 0;
  while (file.available()) {
    String line = file.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) count++;
  }
  file.close();
  return count;
}

bool peekOldestQueuedSession(String &record) {
  File file = LittleFS.open(QUEUE_FILE, FILE_READ);
  if (!file) return false;
  while (file.available()) {
    record = file.readStringUntil('\n');
    record.trim();
    if (record.length() > 0) {
      file.close();
      return true;
    }
  }
  file.close();
  return false;
}

bool removeQueuedSessionById(const String &sessionId) {
  File input = LittleFS.open(QUEUE_FILE, FILE_READ);
  if (!input) return false;
  File output = LittleFS.open(QUEUE_TEMP_FILE, FILE_WRITE);
  if (!output) {
    input.close();
    return false;
  }

  bool removed = false;
  while (input.available()) {
    String line = input.readStringUntil('\n');
    line.trim();
    if (line.length() == 0) continue;
    JsonDocument record;
    if (!deserializeJson(record, line)) {
      const String id = record["session_id"] | "";
      if (!removed && id == sessionId) {
        removed = true;
        continue;
      }
    }
    output.println(line);
  }
  input.close();
  output.flush();
  output.close();

  LittleFS.remove(QUEUE_FILE);
  LittleFS.rename(QUEUE_TEMP_FILE, QUEUE_FILE);
  return removed;
}

void sendPendingRecordsOverBle() {
  File file = LittleFS.open(QUEUE_FILE, FILE_READ);
  if (!file) {
    JsonDocument done;
    done["type"] = "pending_complete";
    done["count"] = 0;
    notifyJson(done);
    return;
  }

  uint32_t sent = 0;
  while (file.available() && bleConnected) {
    String record = file.readStringUntil('\n');
    record.trim();
    if (record.length() == 0) continue;

    JsonDocument parsed;
    if (deserializeJson(parsed, record)) continue;
    const String sessionId = parsed["session_id"] | "unknown";

    String begin = String("#BEGIN:") + sessionId + ":" + String(record.length());
    responseCharacteristic->setValue(begin.c_str());
    responseCharacteristic->notify();
    delay(25);

    for (size_t offset = 0; offset < record.length() && bleConnected; offset += BLE_RECORD_CHUNK_BYTES) {
      const String chunk = record.substring(offset, min(offset + BLE_RECORD_CHUNK_BYTES, record.length()));
      responseCharacteristic->setValue(chunk.c_str());
      responseCharacteristic->notify();
      delay(25);
    }

    String end = String("#END:") + sessionId;
    responseCharacteristic->setValue(end.c_str());
    responseCharacteristic->notify();
    delay(40);
    sent++;
  }
  file.close();

  JsonDocument done;
  done["type"] = "pending_complete";
  done["count"] = sent;
  notifyJson(done);
}

// -----------------------------------------------------------------------------
// Wi-Fi and server synchronization
// -----------------------------------------------------------------------------

bool connectWiFi(bool forceAttempt) {
  if (WiFi.status() == WL_CONNECTED) return true;
  if (config.wifiSsid.length() == 0) return false;
  const uint64_t currentMillis = millis();
  if (!forceAttempt && currentMillis - lastWiFiAttemptMillis < WIFI_RETRY_INTERVAL_MS) return false;

  lastWiFiAttemptMillis = currentMillis;
  WiFi.mode(WIFI_STA);
  WiFi.begin(config.wifiSsid.c_str(), config.wifiPassword.c_str());
  const uint64_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) delay(200);

  if (WiFi.status() == WL_CONNECTED) {
    configTime(0, 0, "pool.ntp.org", "time.google.com");
    Serial.printf("Wi-Fi connected: %s, IP=%s\n", config.wifiSsid.c_str(), WiFi.localIP().toString().c_str());
    return true;
  }

  Serial.println("Wi-Fi connection failed; sessions will remain queued");
  return false;
}

bool uploadOldestQueuedSession() {
  if (activeSession.running || WiFi.status() != WL_CONNECTED ||
      config.apiBaseUrl.length() == 0 || config.apiToken.length() == 0) return false;

  String record;
  if (!peekOldestQueuedSession(record)) return false;

  JsonDocument parsed;
  if (deserializeJson(parsed, record)) return false;
  const String sessionId = parsed["session_id"] | "";
  // A record uploaded directly by the device must identify the direct route.
  parsed["sync_source"] = "device_wifi";
  serializeJson(parsed, record);

  HTTPClient http;
  const String url = config.apiBaseUrl + "/api/therapy-sessions";
  if (!http.begin(url)) return false;
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", String("Bearer ") + config.apiToken);
  const int responseCode = http.POST(record);
  const String responseBody = http.getString();
  http.end();

  if (responseCode == 200 || responseCode == 201) {
    JsonDocument response;
    if (!deserializeJson(response, responseBody) && (response["acknowledged"] | false)) {
      removeQueuedSessionById(sessionId);
      Serial.printf("Uploaded and acknowledged session %s\n", sessionId.c_str());
      return true;
    }
  }

  Serial.printf("Session upload failed: HTTP %d\n", responseCode);
  return false;
}

// -----------------------------------------------------------------------------
// Time and reset helpers
// -----------------------------------------------------------------------------

String isoTimestamp() {
  const time_t currentTime = time(nullptr);
  if (currentTime > 100000) {
    struct tm utc;
    gmtime_r(&currentTime, &utc);
    char timestamp[25];
    strftime(timestamp, sizeof(timestamp), "%Y-%m-%dT%H:%M:%SZ", &utc);
    return String(timestamp);
  }
  // Preserves event ordering before NTP is available. The server/app must treat
  // this as an unsynchronized timestamp, not trusted wall-clock time.
  char fallback[25];
  snprintf(fallback, sizeof(fallback), "1970-01-01T00:00:%02lluZ", (millis() / 1000ULL) % 60ULL);
  return String(fallback);
}

void factoryReset() {
  stopMotors();
  preferences.begin("neurosense", false);
  preferences.clear();
  preferences.end();
  LittleFS.remove(QUEUE_FILE);
  WiFi.disconnect(true, true);
  delay(250);
  ESP.restart();
}
