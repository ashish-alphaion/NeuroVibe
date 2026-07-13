# NeuroVibe temporary Netlify gateway

This deployment provides one stable base URL for the ESP32-C3, patient app, and doctor portal:

```text
https://neurovibeapi.netlify.app
```

The HTML page is a status dashboard. Netlify Functions are the actual API, and Netlify Blobs is the temporary data store.

## Deploy

1. Push the repository to GitHub and import it as a new Netlify project.
2. Netlify reads `netlify.toml`; no build command is required.
3. In **Project configuration > Environment variables**, add:
   - `DEVICE_API_TOKEN`: a long random token used by the prototype device/app uploader.
   - `DOCTOR_API_TOKEN`: a different long random token used by the doctor portal.
   - `ALLOWED_ORIGIN`: `https://neurovibeapi.netlify.app`.
4. Deploy and visit `/`; the page should report that `/api/health` is online.

Do not place either token in `public/index.html` or commit it to GitHub.

## Configure the ESP32

Send the firmware's BLE `set_server` command using:

```json
{
  "type": "set_server",
  "api_base_url": "https://neurovibeapi.netlify.app",
  "api_token": "THE_DEVICE_API_TOKEN"
}
```

The firmware already posts queued records to `/api/therapy-sessions` with a bearer token. Before real deployment, configure `WiFiClientSecure` with certificate verification; do not disable TLS verification for patient data.

## API

### Health

```http
GET /api/health
```

### Upload a session

```http
POST /api/therapy-sessions
Authorization: Bearer DEVICE_API_TOKEN
Content-Type: application/json
```

The body format matches the current ESP32 firmware. `requested_hz` is rejected if it is outside 0–230 Hz. The `session_id` is used for idempotency, so a safe retry returns `already_accepted` instead of creating a duplicate.

### Read the latest sessions

```http
GET /api/therapy-sessions
Authorization: Bearer DOCTOR_API_TOKEN
```

This prototype endpoint returns at most 100 records. It is intended for development with fabricated data, not clinical use.

## Moving to a database

Keep the same `/api` URLs and replace only the storage code inside the functions. PostgreSQL is the appropriate next step because patients, assignments, devices, schedules, and sessions are relational data. The existing local SQLite schema can guide that migration.
