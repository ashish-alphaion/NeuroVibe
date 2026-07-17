// NeuroSense simple Bluetooth motor controller
// Hardware: ESP32-C3 Super Mini + DRV8833 + two 3 V ERM coin motors
//
// Commands:
//   {"type":"get_status"}
//   {"type":"set_frequency","hz":120}
//   {"type":"stop"}
//
// Required library: ArduinoJson 7.x

#include <Arduino.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

#define M1_IN1 2
#define M1_IN2 3
#define M2_IN1 4
#define M2_IN2 5

#define PWM_FREQUENCY 20000
#define PWM_RESOLUTION 8
#define PWM_MAX 255

#define SERVICE_UUID "7b1e0001-7f34-4fd8-a912-6c38ef4a5201"
#define COMMAND_UUID "7b1e0002-7f34-4fd8-a912-6c38ef4a5201"
#define RESPONSE_UUID "7b1e0003-7f34-4fd8-a912-6c38ef4a5201"

constexpr float MAX_HZ = 230.0f;
constexpr int MIN_RUNNING_PWM = 70;
constexpr char FIRMWARE_VERSION[] = "1.0.0-simple-ble";

BLEServer *bleServer = nullptr;
BLECharacteristic *responseCharacteristic = nullptr;
bool bleConnected = false;
bool wasConnected = false;
float currentHz = 0.0f;
int currentPwm = 0;

void stopMotors() {
  ledcWrite(M1_IN1, 0);
  ledcWrite(M1_IN2, 0);
  ledcWrite(M2_IN1, 0);
  ledcWrite(M2_IN2, 0);
  currentHz = 0.0f;
  currentPwm = 0;
}

void applyFrequency(float hz) {
  hz = constrain(hz, 0.0f, MAX_HZ);
  if (hz <= 0.0f) {
    stopMotors();
    return;
  }

  currentHz = hz;
  currentPwm = static_cast<int>(round(
    MIN_RUNNING_PWM + ((hz - 1.0f) / (MAX_HZ - 1.0f)) * (PWM_MAX - MIN_RUNNING_PWM)
  ));
  currentPwm = constrain(currentPwm, MIN_RUNNING_PWM, PWM_MAX);

  ledcWrite(M1_IN1, currentPwm);
  ledcWrite(M1_IN2, 0);
  ledcWrite(M2_IN1, currentPwm);
  ledcWrite(M2_IN2, 0);
}

void notifyResponse(const char *type, const char *message = nullptr) {
  if (!bleConnected || responseCharacteristic == nullptr) return;
  JsonDocument response;
  response["type"] = type;
  response["connected"] = bleConnected;
  response["running"] = currentHz > 0.0f;
  response["hz"] = currentHz;
  response["pwm"] = currentPwm;
  response["firmware"] = FIRMWARE_VERSION;
  if (message != nullptr) response["message"] = message;
  String payload;
  serializeJson(response, payload);
  responseCharacteristic->setValue(payload.c_str());
  responseCharacteristic->notify();
}

void processCommand(const String &payload) {
  JsonDocument command;
  if (deserializeJson(command, payload)) {
    notifyResponse("error", "Invalid command");
    return;
  }

  const String type = command["type"] | "";
  if (type == "get_status") {
    notifyResponse("status");
    return;
  }
  if (type == "set_frequency") {
    const float hz = command["hz"] | -1.0f;
    if (hz < 0.0f || hz > MAX_HZ) {
      notifyResponse("error", "Frequency must be between 0 and 230 Hz");
      return;
    }
    applyFrequency(hz);
    notifyResponse("status", hz > 0.0f ? "Motors running" : "Motors stopped");
    return;
  }
  if (type == "stop") {
    stopMotors();
    notifyResponse("status", "Motors stopped");
    return;
  }
  notifyResponse("error", "Unknown command");
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    bleConnected = true;
    Serial.println("Bluetooth connected");
  }

  void onDisconnect(BLEServer *) override {
    bleConnected = false;
    stopMotors();
    Serial.println("Bluetooth disconnected; motors stopped");
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    const String payload = characteristic->getValue();
    if (!payload.isEmpty()) processCommand(payload);
  }
};

void setup() {
  Serial.begin(115200);

  ledcAttach(M1_IN1, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttach(M1_IN2, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttach(M2_IN1, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttach(M2_IN2, PWM_FREQUENCY, PWM_RESOLUTION);
  stopMotors();

  BLEDevice::init("NeuroSense");
  BLEDevice::setMTU(247);
  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());

  BLEService *service = bleServer->createService(SERVICE_UUID);
  BLECharacteristic *command = service->createCharacteristic(
    COMMAND_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  command->setCallbacks(new CommandCallbacks());

  responseCharacteristic = service->createCharacteristic(
    RESPONSE_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  responseCharacteristic->setValue("{\"type\":\"ready\"}");

  service->start();
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.println("NeuroSense simple BLE controller ready");
}

void loop() {
  if (!bleConnected && wasConnected) {
    delay(250);
    bleServer->startAdvertising();
    wasConnected = false;
  } else if (bleConnected && !wasConnected) {
    wasConnected = true;
  }
  delay(20);
}
