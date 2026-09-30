#line 1 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
/*
  NeuroVibe - ESP32-C3 + DRV8833 dual vibration motor controller

  Inputs:
    Bluetooth device name: NeuroVibe
    NeuroSense Android app: slider value 0..230
    Serial Monitor: whole number 0..230

  Motor command:
    0       -> both motors OFF
    1..230  -> PWM frequency in Hz; duty rises proportionally to the input

  DRV8833 connections:
    GPIO 1 -> AIN1
    GPIO 2 -> AIN2
    GPIO 3 -> BIN1
    GPIO 4 -> BIN2

  Both motors run in one direction. Swap a motor's two wires if its direction
  needs to be reversed.
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

#if ESP_ARDUINO_VERSION_MAJOR < 3
constexpr uint8_t MOTOR_A_CHANNEL = 0;
constexpr uint8_t MOTOR_B_CHANNEL = 1;
#endif
// ESP32-C3 does not expose LEDC's 1 MHz reference clock. Its approximately
// 8 MHz RC_FAST clock with 14-bit resolution supports the full 1..230 Hz range.
constexpr uint8_t PWM_RESOLUTION_BITS = 14;
constexpr uint16_t INPUT_MAX_VALUE = 230;
constexpr uint16_t DEFAULT_PWM_FREQUENCY_HZ = 100;
constexpr uint16_t PWM_PERIOD_COUNTS = (1U << PWM_RESOLUTION_BITS);
constexpr uint16_t PWM_REGISTER_MAX = PWM_PERIOD_COUNTS - 1U;
// Arduino-ESP32 converts PWM_REGISTER_MAX to PWM_PERIOD_COUNTS for "full on".
// Avoid that boundary because it can produce OFF on ESP32-C3 LEDC.
constexpr uint16_t PWM_SAFE_MAX_DUTY = PWM_REGISTER_MAX - 1U;
constexpr uint16_t SERIAL_OUTPUT_DELAY_MS = 1000;
constexpr uint8_t SERIAL_LOG_QUEUE_LENGTH = 16;

constexpr char BLUETOOTH_DEVICE_NAME[] = "NeuroVibe";
constexpr char NEUROVIBE_SERVICE_UUID[] =
    "7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00";
constexpr char MOTOR_COMMAND_UUID[] =
    "7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00";

String serialLine;
BLECharacteristic *motorCommandCharacteristic = nullptr;

struct SerialLogMessage {
  uint32_t notBefore;
  char text[112];
};

QueueHandle_t serialLogQueue = nullptr;
uint32_t nextSerialOutputAt = 0;

#line 70 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void queueSerialMessage(const char *format, ...);
#line 87 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void processSerialMessages();
#line 109 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void writeMotorDuty(uint16_t duty);
#line 120 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void setPwmFrequency(uint16_t frequencyHz);
#line 130 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void stopMotors();
#line 136 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void setMotorLevel(uint16_t value);
#line 167 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void processSerialLine(String line);
#line 236 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void setupBluetooth();
#line 259 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void setup();
#line 315 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
void loop();
#line 70 "C:\\Users\\user\\Documents\\NeuroVibe\\NeuroVibe.ino"
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

  // Never block Bluetooth motor commands just to print diagnostics.
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

void writeMotorDuty(uint16_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  // Arduino-ESP32 3.x identifies an attached LEDC output by its GPIO pin.
  ledcWrite(MOTOR_A_PWM_PIN, duty);
  ledcWrite(MOTOR_B_PWM_PIN, duty);
#else
  ledcWrite(MOTOR_A_CHANNEL, duty);
  ledcWrite(MOTOR_B_CHANNEL, duty);
#endif
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
  writeMotorDuty(0);
  digitalWrite(MOTOR_A_DIR_PIN, LOW);
  digitalWrite(MOTOR_B_DIR_PIN, LOW);
}

void setMotorLevel(uint16_t value) {
  if (value == 0) {
    stopMotors();
    queueSerialMessage("Motors OFF");
    return;
  }

  // The entered value controls both pulse frequency and motor power:
  //   frequency = value Hz
  //   duty count = round(value / 230 * 16382)
  const uint16_t frequencyHz = value;
  const uint16_t duty = static_cast<uint16_t>(
      (static_cast<uint32_t>(value) * PWM_SAFE_MAX_DUTY +
       (INPUT_MAX_VALUE / 2U)) /
      INPUT_MAX_VALUE);

  digitalWrite(MOTOR_A_DIR_PIN, LOW);
  digitalWrite(MOTOR_B_DIR_PIN, LOW);
  setPwmFrequency(frequencyHz);
  writeMotorDuty(duty);

  const float commandPercent =
      (100.0F * static_cast<float>(value)) / INPUT_MAX_VALUE;
  const float electricalDutyPercent =
      (100.0F * static_cast<float>(duty)) / PWM_PERIOD_COUNTS;
  queueSerialMessage(
      "Both motors: %u Hz, level %.1f%%, PWM %u/%u (%.3f%%)",
      frequencyHz, commandPercent, duty, PWM_PERIOD_COUNTS,
      electricalDutyPercent);
}

void processSerialLine(String line) {
  line.trim();
  if (line.isEmpty()) {
    return;
  }

  // Reject text, decimals, signs, and mixed input instead of treating it as 0.
  for (size_t i = 0; i < line.length(); ++i) {
    if (!isDigit(static_cast<unsigned char>(line[i]))) {
      queueSerialMessage(
          "Invalid input. Enter a whole number from 0 to 230.");
      return;
    }
  }

  const unsigned long value = line.toInt();
  if (value > INPUT_MAX_VALUE) {
    queueSerialMessage("Out of range. Enter a value from 0 to 230.");
    return;
  }

  setMotorLevel(static_cast<uint16_t>(value));
}

class MotorCommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    const String received = characteristic->getValue();
    if (received.isEmpty()) {
      return;
    }

    uint16_t value = 0;

    // NeuroSense sends one unsigned byte. ASCII is also accepted for testing
    // with generic BLE applications.
    if (received.length() == 1) {
      value = static_cast<uint8_t>(received[0]);
    } else {
      for (size_t i = 0; i < received.length(); ++i) {
        if (!isDigit(static_cast<unsigned char>(received[i]))) {
          queueSerialMessage("Ignored invalid Bluetooth command.");
          return;
        }
      }
      value = received.toInt();
    }

    if (value > INPUT_MAX_VALUE) {
      queueSerialMessage("Ignored Bluetooth value above 230.");
      return;
    }

    setMotorLevel(value);
  }
};

class NeuroVibeServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    queueSerialMessage("NeuroSense connected.");
  }

  void onDisconnect(BLEServer *server) override {
    // Safety behavior: a lost phone connection always stops both motors.
    stopMotors();
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

  uint8_t initialValue = 0;
  motorCommandCharacteristic->setValue(&initialValue, 1);
  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(NEUROVIBE_SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void setup() {
  Serial.begin(115200);
  serialLine.reserve(16);
  serialLogQueue =
      xQueueCreate(SERIAL_LOG_QUEUE_LENGTH, sizeof(SerialLogMessage));

  // NeuroVibe uses BLE only. Keep the Wi-Fi radio stack disabled.
  // Wi-Fi is not initialized by this firmware. This also stops it if a board
  // package or boot component happened to initialize it before setup().
  esp_wifi_stop();

  pinMode(MOTOR_A_DIR_PIN, OUTPUT);
  pinMode(MOTOR_B_DIR_PIN, OUTPUT);
  digitalWrite(MOTOR_A_DIR_PIN, LOW);
  digitalWrite(MOTOR_B_DIR_PIN, LOW);

#if ESP_ARDUINO_VERSION_MAJOR >= 3
  const bool clockReady = ledcSetClockSource(LEDC_USE_RC_FAST_CLK);

  // Let Arduino allocate valid LEDC channels for the ESP32-C3.
  const bool motorAReady =
      clockReady && ledcAttach(MOTOR_A_PWM_PIN, DEFAULT_PWM_FREQUENCY_HZ,
                               PWM_RESOLUTION_BITS);
  const bool motorBReady =
      clockReady && ledcAttach(MOTOR_B_PWM_PIN, DEFAULT_PWM_FREQUENCY_HZ,
                               PWM_RESOLUTION_BITS);

  if (!motorAReady || !motorBReady) {
    delay(SERIAL_OUTPUT_DELAY_MS);
    Serial.printf(
        "ERROR: PWM setup failed (clock=%s, motor A=%s, motor B=%s).\n",
                  clockReady ? "OK" : "FAILED",
                  motorAReady ? "OK" : "FAILED",
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
  setupBluetooth();

  queueSerialMessage("NeuroVibe ready. Wi-Fi is OFF.");
  queueSerialMessage("Bluetooth name: NeuroVibe");
  queueSerialMessage("Open NeuroSense and tap Connect.");
  queueSerialMessage(
      "Enter a whole number from 0 to 230, then press Enter.");
  queueSerialMessage("0 = OFF; 230 = maximum power.");
}

void loop() {
  processSerialMessages();

  while (Serial.available() > 0) {
    const char incoming = static_cast<char>(Serial.read());

    if (incoming == '\n' || incoming == '\r') {
      if (!serialLine.isEmpty()) {
        processSerialLine(serialLine);
        serialLine = "";
      }
    } else if (serialLine.length() < 10) {
      serialLine += incoming;
    }
  }
}

