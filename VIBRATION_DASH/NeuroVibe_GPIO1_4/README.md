# NeuroSense GPIO1-4 firmware

This is the GPIO1 through GPIO4 wiring variant of the NeuroSense BLE motor
controller for an ESP32-C3 Super Mini and a DRV8833-compatible dual motor
driver module.

## Connections

| ESP32-C3 | Driver input | Function |
|---|---|---|
| GPIO 1 | IN1 | Motor 1 PWM/control |
| GPIO 2 | IN2 | Motor 1 control |
| GPIO 3 | IN3 | Motor 2 PWM/control |
| GPIO 4 | IN4 | Motor 2 control |
| GND | GND | Common logic and motor-supply ground |

Connect motor 1 to `OUT1` and `OUT2`. Connect motor 2 to `OUT3` and `OUT4`.
Connect the regulated motor supply to the driver's motor-power input (`VM` or
the equivalent label on the selected module). Place at least 10 uF of ceramic
capacitance directly between the motor-power input and ground.

Do not power either motor from an ESP32 GPIO pin. The motor supply must support
the combined startup/stall current of both motors.

GPIO2 is an ESP32-C3 strapping pin. Test repeated power-up and reset cycles with
the exact driver module. If startup motor movement is unacceptable, hold the
driver's `nSLEEP`/enable input low until the ESP32 has finished booting or move
the driver input away from GPIO2.

## Build and upload

1. Open `NeuroVibe_GPIO1_4.ino` in Arduino IDE.
2. Select the ESP32-C3 board profile that matches the Super Mini.
3. Install ArduinoJson 7.x.
4. Compile and upload the sketch.

The BLE service and command protocol are unchanged from the original firmware.
