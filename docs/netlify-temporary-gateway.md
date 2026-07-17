# NeuroVibe Netlify gateway

Netlify hosts the doctor portal and server-side functions. Supabase stores
identity, assignments, care plans, appointments, audit events, and device usage.

## Required Netlify environment

```text
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_SECRET_KEY=YOUR_SERVER_ONLY_SECRET
DOCTOR_API_TOKEN=OPTIONAL_LEGACY_READ_TOKEN
```

Do not place the Supabase secret or database password in HTML, Android code,
ESP32 firmware, or Git.

## Patient record path

```text
NeuroSense --BLE--> NeuroVibe Android app --HTTPS--> /api/patient-sync --> Supabase
```

The Android app authenticates as the patient. The backend verifies that the
record's device and assignment belong to that patient before ingestion. The app
sends `ack_session` over BLE only after the server acknowledges the record.

`POST /api/therapy-sessions` no longer accepts device uploads and returns
`410 mobile_relay_required`. NeuroSense has no HTTP client, network credentials,
API URL, or device API token.

## Deployment checks

- `GET /api/health` confirms the function runtime and Supabase connection.
- Apply all migrations in timestamp order, including the BLE-only transport
  cleanup migration.
- Configure Android's Supabase URL and publishable key; keep the secret key
  server-side.
- Use fabricated data only until the full privacy, security, clinical, and
  regulatory controls are implemented.
