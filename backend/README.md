# NeuroVibe Local Backend

This is the first local backend foundation for NeuroVibe. It uses only Node.js 24 built-in modules:

- `node:http` for the API server
- `node:sqlite` for the local database
- `node:crypto` for password hashing and login tokens
- `node:test` for end-to-end API tests

No package installation is required.

## Requirements

- Node.js 24 or later

## Start locally

From the repository root:

```powershell
cd backend
npm start
```

The API starts at:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/health
```

The SQLite database is created at:

```text
.local/neurovibe.db
```

## Local development login

```text
Email: doctor@neurovibe.local
Password: ChangeMe!123
```

These credentials are for local dummy data only. Set `DEMO_DOCTOR_EMAIL` and `DEMO_DOCTOR_PASSWORD` before using a shared development environment.

## Run tests

```powershell
cd backend
npm test
```

The test suite verifies the initial vertical workflow:

1. Health check
2. Doctor login
3. Patient creation
4. Device assignment
5. Care-plan validation
6. Session scheduling
7. Duplicate-safe session upload
8. Patient history and dashboard summary

## Current API

```text
GET  /health
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
GET  /api/dashboard/summary
GET  /api/patients
POST /api/patients
GET  /api/patients/{patient_id}
GET  /api/devices
POST /api/devices
POST /api/devices/{device_id}/credential
POST /api/device-assignments
POST /api/care-plans
POST /api/scheduled-sessions
POST /api/therapy-sessions
GET  /api/patients/{patient_id}/therapy-sessions
```

All `/api/*` routes except login require:

```text
Authorization: Bearer <token>
```

The doctor creates or rotates a unique device credential with:

```text
POST /api/devices/{device_id}/credential
```

The returned token is shown once and should be provisioned to that NeuroSense device through the authenticated BLE setup flow. The device uses its own token for `POST /api/therapy-sessions`; it cannot access doctor or patient-management routes.

## Important limitations

- This is a local MVP foundation, not a production medical backend.
- SQLite will be replaced by PostgreSQL before production deployment.
- Patient-app authentication and production device enrollment are later milestones.
- Use only dummy patient data until privacy, security, consent, and retention requirements are formally approved.
