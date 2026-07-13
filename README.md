# NeuroVibe

NeuroVibe is a connected vibration-session platform consisting of:

- NeuroSense ESP32-C3 firmware
- Local/backend API
- Doctor web portal
- Patient mobile app

## Current implementation

The local backend foundation is available in [`backend`](./backend/README.md).

Start it with:

```powershell
cd backend
npm start
```

Run its tests with:

```powershell
cd backend
npm test
```

Project architecture documents and diagrams are in [`docs`](./docs).

## Temporary Netlify gateway

A Netlify-ready HTTPS gateway is available at the repository root. It includes a public status page, protected session upload/fetch Functions, and temporary Netlify Blobs storage.

See [`docs/netlify-temporary-gateway.md`](./docs/netlify-temporary-gateway.md) for deployment and configuration instructions.

## Supabase database

The managed PostgreSQL schema and Row Level Security policies are in [`supabase/migrations`](./supabase/migrations). Follow [`supabase/README.md`](./supabase/README.md) to create the project, apply the schema, and bootstrap the first administrator.
