# NeuroVibe Netlify API gateway

This deployment provides one stable base URL for the ESP32-C3, patient app, and doctor portal:

```text
https://neurovibeapi.netlify.app
```

The HTML page is a status dashboard. Netlify Functions are the actual API, and Supabase PostgreSQL is the structured data store.

## Deploy

1. Push the repository to GitHub and import it as a new Netlify project.
2. Netlify reads `netlify.toml`; no build command is required.
3. In **Project configuration > Environment variables**, add:
   - `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and server-only `SUPABASE_SECRET_KEY`.
   - `DEVICE_TOKEN_PEPPER`: a long cryptographically random value.
   - `DEVICE_API_TOKEN`: a temporary token used until device-specific enrollment is active.
   - `DOCTOR_API_TOKEN`: a temporary token used until portal authentication is active.
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

The body format matches the current ESP32 firmware. `requested_hz` is rejected if it is outside 0-230 Hz. The `session_id` is used for idempotency, so a safe retry returns `already_accepted` instead of creating a duplicate.

### Read the latest sessions

```http
GET /api/therapy-sessions
Authorization: Bearer DOCTOR_API_TOKEN
```

This prototype endpoint returns at most 100 records from Supabase. It is intended for development with fabricated data, not clinical use.

## Database migration

Apply all SQL files in `supabase/migrations` in filename order. The third migration installs the atomic, idempotent session-ingestion function used by this gateway.
