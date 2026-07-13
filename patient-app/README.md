# NeuroVibe Patient Android App

Native Android prototype for patients only. It includes the supplied welcome,
dashboard and active-session visual direction plus schedule, history, profile,
device connection, Wi-Fi provisioning, symptom notes and safety guidance.

## Run

Open `patient-app` in Android Studio, or build from a terminal with:

```text
gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## NeuroSense BLE

The app scans only for devices advertised as `NeuroSense-*` and communicates
using the service and characteristics documented in `docs/ble-protocol.md`.
It implements device information/status, Wi-Fi provisioning, session start,
frequency updates, normal stop and emergency stop.

## Prototype data

The current access form opens a fictional patient experience so the UI and BLE
device workflow can be tested before patient Supabase accounts and invitation
redemption endpoints are finalized. No doctor/admin features are included.

Demo sign-in:

```text
Email: patient.demo@neurovibe.app
Password: Neuro@1234
Invitation code: NV-DEMO-001
```
