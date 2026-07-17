# NeuroVibe Android patient app

NeuroVibe is the patient-only Android client for NeuroSense.

It provides:

- persistent patient sign-in
- doctor-assigned Device ID verification
- BLE connection and assignment-lease installation
- 1–230 Hz control (`0 Hz` is stop)
- 1–90 minute duration control
- emergency stop
- appointment notifications
- BLE retrieval of queued device-usage records
- authenticated server upload through the phone
- encrypted app-private retry storage when phone internet is unavailable

NeuroSense never receives an SSID, network password, API URL or API token. The
phone is the only internet-facing component in the patient workflow.

Build:

```powershell
cd patient-app
.\gradlew.bat assembleDebug
```

Debug APK:

```text
patient-app/app/build/outputs/apk/debug/app-debug.apk
```

Required Android permissions are Bluetooth scan/connect, notifications, and
legacy location only on Android versions that require it for BLE discovery.
Internet permission is used solely by the app for Supabase/Netlify calls.
