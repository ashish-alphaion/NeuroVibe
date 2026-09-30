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

For safety, both motors stop when Bluetooth disconnects or NeuroSense
moves into the background.

## Bluetooth protocol

The Android app and firmware use the following custom BLE GATT values:

- Device name: `NeuroVibe`
- Service UUID: `7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- Motor command UUID: `7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- Command payload: one unsigned byte from `0` through `230`
