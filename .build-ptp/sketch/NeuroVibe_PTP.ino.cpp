#line 1 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
/*
  NeuroVibe PTP - ESP32-C3 + DRV8833 dual vibration motor controller

  Startup sequence:
    1. Motor 1 vibrates briefly.
    2. Motor 2 vibrates briefly.
    3. Both motors vibrate briefly.
    4. Both motors stop and BLE advertising begins.

  After the self-test, output remains OFF until NeuroSense connects and sends
  one of its administrator-configured Low, Medium, or High quick-level values.
  NeuroSense sends the selected frequency as one unsigned byte from 1 to 230.
  A value of 0 always stops both motors.

  DRV8833 connections:
    GPIO 1 -> AIN1
    GPIO 2 -> AIN2
    GPIO 3 -> BIN1
    GPIO 4 -> BIN2
*/

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <esp_arduino_version.h>
#include <esp_wifi.h>
#include <stdarg.h>

constexpr uint8_t MOTOR_A_PWM_PIN = 1;
constexpr uint8_t MOTOR_A_DIR_PIN = 2;
constexpr uint8_t MOTOR_B_PWM_PIN = 3;
constexpr uint8_t MOTOR_B_DIR_PIN = 4;

constexpr uint8_t MOTOR_A_CHANNEL = 0;
constexpr uint8_t MOTOR_B_CHANNEL = 1;

constexpr uint8_t PWM_RESOLUTION_BITS = 14;
constexpr uint16_t INPUT_MAX_VALUE = 230;
constexpr uint16_t DEFAULT_PWM_FREQUENCY_HZ = 100;
constexpr uint16_t PWM_PERIOD_COUNTS = (1U << PWM_RESOLUTION_BITS);
constexpr uint16_t PWM_REGISTER_MAX = PWM_PERIOD_COUNTS - 1U;
constexpr uint16_t PWM_SAFE_MAX_DUTY = PWM_REGISTER_MAX - 1U;

// A moderate, clearly detectable self-test that avoids starting at full power.
constexpr uint16_t SELF_TEST_FREQUENCY_HZ = 100;
constexpr uint16_t SELF_TEST_DUTY =
    static_cast<uint16_t>((static_cast<uint32_t>(PWM_SAFE_MAX_DUTY) * 35U) /
                          100U);
constexpr uint16_t SELF_TEST_MOTOR_TIME_MS = 650;
constexpr uint16_t SELF_TEST_GAP_TIME_MS = 250;

constexpr uint16_t SERIAL_OUTPUT_DELAY_MS = 1000;
constexpr uint8_t SERIAL_LOG_QUEUE_LENGTH = 16;

constexpr char BLUETOOTH_DEVICE_NAME[] = "NeuroVibe";
constexpr char NEUROVIBE_SERVICE_UUID[] =
    "7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00";
constexpr char MOTOR_COMMAND_UUID[] =
    "7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00";

BLECharacteristic *motorCommandCharacteristic = nullptr;

struct SerialLogMessage {
  uint32_t notBefore;
  char text[112];
};

QueueHandle_t serialLogQueue = nullptr;
uint32_t nextSerialOutputAt = 0;

#line 72 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void queueSerialMessage(const char *format, ...);
#line 89 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void processSerialMessages();
#line 111 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void writeMotorADuty(uint16_t duty);
#line 119 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void writeMotorBDuty(uint16_t duty);
#line 127 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void writeBothMotorDuties(uint16_t motorADuty, uint16_t motorBDuty);
#line 132 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void setPwmFrequency(uint16_t frequencyHz);
#line 142 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void stopMotors();
#line 148 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void runStartupMotorTest();
#line 173 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void applyQuickLevelFrequency(uint16_t value);
#line 241 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void setupBluetooth();
#line 264 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void setupPwm();
#line 306 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void setup();
#line 323 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void loop();
#line 72 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe_PTP\\NeuroVibe_PTP.ino"
void queueSerialMessage(const char *format, ...) {
  if (serialLogQueue == nullptr) {
    return;
  }

  SerialLogMessage message = {};
  message.notBefore = millis() + SERIAL_OUTPUT_DELAY_MS;

  va_list arguments;
  va_start(arguments, format);
  vsnprintf(message.text, sizeof(message.text), format, arguments);
  va_end(arguments);

  // Diagnostics must never delay BLE commands or motor shutdown.
  xQueueSend(serialLogQueue, &message, 0);
}

void processSerialMessages() {
  if (serialLogQueue == nullptr) {
    return;
  }

  SerialLogMessage message;
  if (xQueuePeek(serialLogQueue, &message, 0) != pdTRUE) {
    return;
  }

  const uint32_t now = millis();
  if (static_cast<int32_t>(now - message.notBefore) < 0 ||
      static_cast<int32_t>(now - nextSerialOutputAt) < 0) {
    return;
  }

  if (xQueueReceive(serialLogQueue, &message, 0) == pdTRUE) {
    Serial.println(message.text);
    nextSerialOutputAt = now + SERIAL_OUTPUT_DELAY_MS;
  }
}

void writeMotorADuty(uint16_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWriteChannel(MOTOR_A_CHANNEL, duty);
#else
  ledcWrite(MOTOR_A_CHANNEL, duty);
#endif
}

void writeMotorBDuty(uint16_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWriteChannel(MOTOR_B_CHANNEL, duty);
#else
  ledcWrite(MOTOR_B_CHANNEL, duty);
#endif
}

void writeBothMotorDuties(uint16_t motorADuty, uint16_t motorBDuty) {
  writeMotorADuty(motorADuty);
  writeMotorBDuty(motorBDuty);
}

void setPwmFrequency(uint16_t frequencyHz) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcChangeFrequency(MOTOR_A_PWM_PIN, frequencyHz, PWM_RESOLUTION_BITS);
  ledcChangeFrequency(MOTOR_B_PWM_PIN, frequencyHz, PWM_RESOLUTION_BITS);
#else
  ledcSetup(MOTOR_A_CHANNEL, frequencyHz, PWM_RESOLUTION_BITS);
  ledcSetup(MOTOR_B_CHANNEL, frequencyHz, PWM_RESOLUTION_BITS);
#endif
}

void stopMotors() {
  writeBothMotorDuties(0, 0);
  digitalWrite(MOTOR_A_DIR_PIN, LOW);
  digitalWrite(MOTOR_B_DIR_PIN, LOW);
}

void runStartupMotorTest() {
  stopMotors();
  setPwmFrequency(SELF_TEST_FREQUENCY_HZ);

  Serial.println("SELF-TEST 1/3: Motor 1");
  writeBothMotorDuties(SELF_TEST_DUTY, 0);
  delay(SELF_TEST_MOTOR_TIME_MS);
  stopMotors();
  delay(SELF_TEST_GAP_TIME_MS);

  Serial.println("SELF-TEST 2/3: Motor 2");
  writeBothMotorDuties(0, SELF_TEST_DUTY);
  delay(SELF_TEST_MOTOR_TIME_MS);
  stopMotors();
  delay(SELF_TEST_GAP_TIME_MS);

  Serial.println("SELF-TEST 3/3: Both motors");
  writeBothMotorDuties(SELF_TEST_DUTY, SELF_TEST_DUTY);
  delay(SELF_TEST_MOTOR_TIME_MS);
  stopMotors();
  delay(SELF_TEST_GAP_TIME_MS);

  Serial.println("SELF-TEST COMPLETE: Both motors OFF");
}

void applyQuickLevelFrequency(uint16_t value) {
  if (value == 0) {
    stopMotors();
    queueSerialMessage("Quick level stopped; both motors OFF.");
  } else {
    // The app's quick-level value controls frequency and proportional power.
    const uint16_t duty = static_cast<uint16_t>(
        (static_cast<uint32_t>(value) * PWM_SAFE_MAX_DUTY +
         (INPUT_MAX_VALUE / 2U)) /
        INPUT_MAX_VALUE);

    digitalWrite(MOTOR_A_DIR_PIN, LOW);
    digitalWrite(MOTOR_B_DIR_PIN, LOW);
    setPwmFrequency(value);
    writeBothMotorDuties(duty, duty);

    const float levelPercent =
        (100.0F * static_cast<float>(value)) / INPUT_MAX_VALUE;
    queueSerialMessage("Quick level applied: %u Hz (%.1f%%).",
                       value, levelPercent);
  }

  // Keep the readable characteristic synchronized with the applied output.
  if (motorCommandCharacteristic != nullptr) {
    const uint8_t appliedValue = static_cast<uint8_t>(value);
    motorCommandCharacteristic->setValue(&appliedValue, 1);
  }
}

class MotorCommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    const String received = characteristic->getValue();

    // NeuroSense sends exactly one unsigned byte for Stop/Low/Medium/High.
    if (received.length() != 1) {
      queueSerialMessage("Ignored invalid quick-level packet.");
      return;
    }

    const uint16_t value = static_cast<uint8_t>(received[0]);
    if (value > INPUT_MAX_VALUE) {
      queueSerialMessage("Ignored quick-level value above 230.");
      return;
    }

    applyQuickLevelFrequency(value);
  }
};

class NeuroVibeServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    stopMotors();
    queueSerialMessage(
        "NeuroSense connected; waiting for a quick-level selection.");
  }

  void onDisconnect(BLEServer *server) override {
    // Any lost app connection immediately returns both outputs to zero.
    stopMotors();
    if (motorCommandCharacteristic != nullptr) {
      const uint8_t stoppedValue = 0;
      motorCommandCharacteristic->setValue(&stoppedValue, 1);
    }
    queueSerialMessage("Bluetooth disconnected; motors stopped.");
    BLEDevice::startAdvertising();
  }
};

void setupBluetooth() {
  BLEDevice::init(BLUETOOTH_DEVICE_NAME);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new NeuroVibeServerCallbacks());

  BLEService *service = server->createService(NEUROVIBE_SERVICE_UUID);
  motorCommandCharacteristic = service->createCharacteristic(
      MOTOR_COMMAND_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_WRITE_NR);
  motorCommandCharacteristic->setCallbacks(new MotorCommandCallbacks());

  const uint8_t initialValue = 0;
  motorCommandCharacteristic->setValue(&initialValue, 1);
  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(NEUROVIBE_SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void setupPwm() {
  // Hold every DRV8833 input LOW before attaching PWM peripherals.
  pinMode(MOTOR_A_PWM_PIN, OUTPUT);
  pinMode(MOTOR_A_DIR_PIN, OUTPUT);
  pinMode(MOTOR_B_PWM_PIN, OUTPUT);
  pinMode(MOTOR_B_DIR_PIN, OUTPUT);
  digitalWrite(MOTOR_A_PWM_PIN, LOW);
  digitalWrite(MOTOR_A_DIR_PIN, LOW);
  digitalWrite(MOTOR_B_PWM_PIN, LOW);
  digitalWrite(MOTOR_B_DIR_PIN, LOW);

#if ESP_ARDUINO_VERSION_MAJOR >= 3
  const bool clockReady = ledcSetClockSource(LEDC_USE_RC_FAST_CLK);
  // Explicit channels prevent automatic LEDC allocation from mapping both
  // motor outputs unpredictably on different ESP32-C3 board variants.
  const bool motorAReady = clockReady && ledcAttachChannel(
      MOTOR_A_PWM_PIN, DEFAULT_PWM_FREQUENCY_HZ, PWM_RESOLUTION_BITS,
      MOTOR_A_CHANNEL);
  const bool motorBReady = clockReady && ledcAttachChannel(
      MOTOR_B_PWM_PIN, DEFAULT_PWM_FREQUENCY_HZ, PWM_RESOLUTION_BITS,
      MOTOR_B_CHANNEL);

  if (!motorAReady || !motorBReady) {
    stopMotors();
    Serial.printf(
        "ERROR: PWM setup failed (clock=%s, motor A=%s, motor B=%s).\n",
        clockReady ? "OK" : "FAILED", motorAReady ? "OK" : "FAILED",
        motorBReady ? "OK" : "FAILED");
    while (true) {
      delay(1000);
    }
  }
#else
  ledcSetup(MOTOR_A_CHANNEL, DEFAULT_PWM_FREQUENCY_HZ, PWM_RESOLUTION_BITS);
  ledcSetup(MOTOR_B_CHANNEL, DEFAULT_PWM_FREQUENCY_HZ, PWM_RESOLUTION_BITS);
  ledcAttachPin(MOTOR_A_PWM_PIN, MOTOR_A_CHANNEL);
  ledcAttachPin(MOTOR_B_PWM_PIN, MOTOR_B_CHANNEL);
#endif

  stopMotors();
}

void setup() {
  Serial.begin(115200);
  serialLogQueue =
      xQueueCreate(SERIAL_LOG_QUEUE_LENGTH, sizeof(SerialLogMessage));

  // This firmware uses BLE only; Wi-Fi remains disabled.
  esp_wifi_stop();

  setupPwm();
  runStartupMotorTest();

  // Advertising starts only after the three-part motor test is complete.
  setupBluetooth();
  queueSerialMessage("NeuroVibe ready; waiting for NeuroSense.");
  queueSerialMessage("Use the app's Low, Medium, High, or Stop control.");
}

void loop() {
  processSerialMessages();
}

