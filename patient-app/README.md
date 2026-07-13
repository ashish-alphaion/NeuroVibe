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

## Patient authentication

Patient accounts are created from the NeuroVibe doctor portal. The enrollment
endpoint invites the patient through Supabase Auth and links the Auth UUID to
`profiles.id` and `patients.user_id`.

The patient opens the invitation email on the Android device. The callback
`neurovibe://auth/callback` opens the app, where the patient creates a private
password. Subsequent sign-ins use that same email and password. The app rejects
staff accounts and Auth users that are not linked to a patient record.

Add `neurovibe://auth/callback` to the Supabase Authentication redirect URL
allow list before testing invitations.
