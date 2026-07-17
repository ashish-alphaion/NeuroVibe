# NeuroVibe simple Android app

The app performs only local Bluetooth motor control:

1. Scan for `NeuroSense`.
2. Connect through BLE.
3. Select a value from `0–230 Hz`.
4. Start, adjust, or stop both motors.

There is no login, patient information, device assignment, Wi-Fi setup, server,
database, usage history, or internet permission.

Build:

```powershell
cd patient-app
.\gradlew.bat clean assembleDebug
```

APK:

```text
patient-app/app/build/outputs/apk/debug/app-debug.apk
```
