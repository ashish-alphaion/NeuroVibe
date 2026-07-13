# NeuroVibe Doctor Portal

This is a separate static HTML/CSS/JavaScript site for doctors and administrators. It uses Supabase Auth and the existing RLS-protected PostgreSQL tables.

## Pages

- `index.html`: doctor/admin sign-in
- `dashboard.html`: live summary, schedules, device alerts and audit activity
- `patients.html`: searchable patient registry
- `patient.html?id=...`: patient, care-plan, device and session details

## Netlify

Create a separate Netlify site from the same repository and set:

```text
Base directory: doctor-portal
Publish directory: .
Build command: leave empty
```

The Supabase URL and publishable key are safe browser configuration. Never add a Supabase secret key to this directory.

Before sign-in works, create the administrator in Supabase Auth and ensure its `public.profiles` row has `role = 'admin'`, `status = 'active'`, and an organization.
