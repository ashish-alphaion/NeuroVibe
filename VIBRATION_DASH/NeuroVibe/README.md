# NeuroSense simple BLE firmware

This firmware is a local Bluetooth controller for:

- ESP32-C3 Super Mini
- DRV8833 dual motor driver
- two 3 V ERM coin vibration motors

There is no Wi-Fi, server, database, login, assignment, session recording, or
cloud synchronization.

## Connections

| DRV8833 input | ESP32-C3 |
|---|---:|
| Motor 1 IN1 | GPIO 2 |
| Motor 1 IN2 | GPIO 3 |
| Motor 2 IN1 | GPIO 4 |
| Motor 2 IN2 | GPIO 5 |

Use a common ground. Connect a suitable motor power supply to the DRV8833 and
place the 10 µF capacitor across its motor supply and ground.

## Bluetooth commands

```json
{"type":"get_status"}
{"type":"set_frequency","hz":120}
{"type":"stop"}
```

The accepted range is `0–230`. A value of `0` stops both motors. Disconnecting
Bluetooth also stops both motors automatically.

The value is mapped to motor PWM. ERM motor vibration frequency is not exactly
equal to the requested number; accurate physical Hz requires an accelerometer,
calibration, and closed-loop control.
