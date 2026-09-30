#line 1 "C:\\Users\\user\\Documents\\NeuroVibe\\README.md"
# NeuroVibe + NeuroSense

Arduino firmware for an ESP32-C3, one DRV8833 dual H-bridge, and two
vibration motors. The included native Android application is called
**NeuroSense** and connects to the ESP32-C3 over Bluetooth Low Energy.

## Wiring

| ESP32-C3 | DRV8833 |
|---|---|
| GPIO 1 | AIN1 |
| GPIO 2 | AIN2 |
| GPIO 3 | BIN1 |
| GPIO 4 | BIN2 |
| GND | GND |

Connect motor 1 to AOUT1/AOUT2 and motor 2 to BOUT1/BOUT2. Connect the
motor supply to `VM`. If the breakout board has an `nSLEEP`/`SLP` pin,
pull it HIGH as required by that board.

The motor supply ground and ESP32-C3 ground must be connected. Do not
power the motors directly from an ESP32 GPIO pin.

## Upload the NeuroVibe firmware

1. Open `NeuroVibe.ino` in Arduino IDE.
2. Install the **esp32 by Espressif Systems** board package.
3. Select the appropriate ESP32-C3 board and port, then upload.
4. After uploading, the device advertises over Bluetooth as
   **NeuroVibe**.

Bluetooth control and Serial Monitor control work with the same
`0` through `230` range. To use Serial Monitor, select **115200 baud**
and a line ending such as **Newline**.

The firmware explicitly keeps Wi-Fi off; only Bluetooth Low Energy is
used. Firmware-generated Serial Monitor messages are delayed and spaced
one second apart without delaying Bluetooth motor commands. The initial
`ESP-ROM` boot text is printed by the ESP32-C3's built-in ROM and cannot
be delayed by the Arduino sketch.

Small vibration motors often cannot start at very low duty values. That
is a motor limitation, not a serial-input error.

## Install and use NeuroSense

The Android Studio project is in the `NeuroSense` folder. It supports
Android 8.0 and newer.

The native app uses a production-focused, iOS-inspired white-and-lavender
design system with clear connection, readiness, and safety states across five
pages. A three-step first-run introduction explains the device, private BLE
connection, and automatic stop behavior.

- **Device** — BLE connection, signal information, product visualization,
  and connection troubleshooting.
- **Control** — native circular `0–230` dial, calculated percentage, ±10 Hz
  fine adjustment, both-motor status, preset shortcut, and immediate stop
  control.
- **Presets** — Gentle, Balanced, Strong, and a locally stored custom
  preset. Starting a preset requires confirmation.
- **Help** — safety override, connection guidance, safety protocols, and
  hardware-care information.
- **Settings** — app/device information, privacy and permission guidance,
  replayable introduction, and safety information.

NeuroSense has no login, signup, cloud service, analytics, or database.
The custom preset is stored only in Android's local app preferences.

The Control screen uses a circular precision dial inspired by native iOS
hardware controls. Dragging around the ring updates the displayed Hz value and
derived percentage continuously, with haptic feedback at 10 Hz intervals; the
BLE command is committed once on release to avoid redundant writes. Dedicated
−10/+10 controls support repeatable fine adjustments. The whole dial is the
touch target and is exposed to accessibility services as a standard `0–230
hertz` slider.

Screen entrances, tab selection, and fine-adjustment feedback use AndroidX
DynamicAnimation spring physics for responsive native motion without adding a
heavy UI framework.

1. Copy `NeuroSense/app/build/outputs/apk/debug/app-debug.apk` to an
   Android phone and open it, or open the `NeuroSense` folder in Android
   Studio and run the app on a phone.
2. Allow the requested **Nearby devices** Bluetooth permission. On
   Android 11 or older, Android requires Location permission for BLE
   scanning.
3. Turn on the ESP32-C3 running the NeuroVibe firmware.
4. Open NeuroSense and tap **Connect NeuroVibe**. Pairing through the
   phone's Bluetooth settings is not required.
5. Move the slider between `0` and `230`.

`0` stops both motors. Values `1` through `230` set that PWM frequency
in hertz and increase the duty cycle proportionally. For example, `115`
uses 115 Hz and approximately 50% duty; `230` uses maximum duty.

### PWM calculation

For an input value `V` from `1` through `230`:

```text
frequency_hz = V
level_percent = V / 230 × 100
duty_count = round(V / 230 × 16382)
electrical_duty_percent = duty_count / 16384 × 100
```

The firmware uses 14-bit PWM, so one PWM period contains `16384` counts.
It intentionally limits the highest duty count to `16382`. The
Arduino-ESP32 core treats `16383` as a special full-on boundary and
internally changes it to `16384`; on this ESP32-C3 setup that boundary
can make the output turn off. A `230` command now produces `16382 /
16384 = 99.988%` electrical duty, which is effectively maximum motor
power without touching the failing boundary.

| Input | Frequency | Level | PWM counts | Electrical duty |
|---:|---:|---:|---:|---:|
| `0` | Off | 0% | 0 / 16384 | 0% |
| `1` | 1 Hz | 0.435% | 71 / 16384 | 0.433% |
| `115` | 115 Hz | 50% | 8191 / 16384 | 49.994% |
| `229` | 229 Hz | 99.565% | 16311 / 16384 | 99.554% |
| `230` | 230 Hz | 100% | 16382 / 16384 | 99.988% |

Frequency and duty are both controlled by the same slider value. At
high values the duty is nearly 100%, so the signal is almost constantly
HIGH even though the PWM timer is running at the selected frequency.

For safety, both motors stop when Bluetooth disconnects or NeuroSense
moves into the background.

## Bluetooth protocol

The Android app and firmware use the following custom BLE GATT values:

- Device name: `NeuroVibe`
- Service UUID: `7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- Motor command UUID: `7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- Command payload: one unsigned byte from `0` through `230`
