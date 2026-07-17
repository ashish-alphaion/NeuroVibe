# NeuroSense ESP32-C3 firmware

Firmware `0.9.0-ble-only` controls two ERM coin motors through a DRV8833. The
ESP32-C3 does not join a network, store network credentials, call an HTTP API,
or hold a server token. Bluetooth Low Energy is its only data transport.

## Hardware

- ESP32-C3 Super Mini
- DRV8833 dual H-bridge
- Two 3 V ERM coin vibration motors
- 10 µF / 25 V supply capacitor

Default motor pins:

| Signal | GPIO |
|---|---:|
| Motor 1 IN1 | 2 |
| Motor 1 IN2 | 3 |
| Motor 2 IN1 | 4 |
| Motor 2 IN2 | 5 |

Use a common ground between the ESP32-C3, driver and motor supply. Do not drive
a motor directly from an ESP32 GPIO.

## Patient workflow

1. The doctor assigns an inventory Device ID to the patient.
2. The signed-in NeuroVibe app connects to `NeuroSense-*` over BLE.
3. The app validates the assignment with the server.
4. The app sends Device ID, patient/assignment lease and care limits over BLE.
5. The app controls motor runs over BLE.
6. NeuroSense stores each run in LittleFS.
7. The app requests queued records with `get_pending`.
8. The app uploads each record using the patient's authenticated internet
   session.
9. Only after server acknowledgement, the app sends `ack_session`, allowing
   NeuroSense to delete its queued copy.

If the phone is offline, the app keeps an additional local copy and retries.
The original remains on NeuroSense until acknowledged.

## BLE service

```text
Service:  7b1e0001-7f34-4fd8-a912-6c38ef4a5201
Command:  7b1e0002-7f34-4fd8-a912-6c38ef4a5201
Response: 7b1e0003-7f34-4fd8-a912-6c38ef4a5201
```

Core commands are `get_status`, `set_identity`, `set_assignment`, `set_limits`,
`activate_assignment`, `start_session`, `set_frequency`, `stop_session`,
`emergency_stop`, `get_pending`, `ack_session`, and `factory_reset`.

See [BLE protocol](../../docs/ble-protocol.md) for payloads.

## Frequency and duration

- System input range: `0–230 Hz`
- `0 Hz`: motors stopped
- Patient duration range: `1–90 minutes`, further restricted by the active
  care plan
- PWM carrier: 20 kHz, 8-bit

The current frequency is an estimate derived from calibrated PWM. ERM motor
speed varies with motor, load, voltage and mounting. A medical claim of exact
mechanical vibration frequency requires an accelerometer, calibration data and
closed-loop control.

## Build and reset

Required:

- Espressif Arduino-ESP32 3.x
- ArduinoJson 7.x
- LittleFS and BLE libraries included with the ESP32 core

Compile for the ESP32-C3 Super Mini target. Factory reset is available through:

```json
{"type":"factory_reset","confirmation":"RESET_NEUROSENSE"}
```

Factory reset erases assignment, care-plan limits and queued records, then
restarts the device. It does not erase the immutable eFuse-derived hardware ID.

## Prototype safety

This is prototype firmware, not certified medical-device software. Production
work requires authenticated/encrypted BLE, secure boot, flash/NVS encryption,
hardware fail-safe review, validated motor output, risk management, audit
controls, privacy review and applicable medical-device verification.
