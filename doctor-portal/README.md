# NeuroVibe Doctor Portal

This is a separate static HTML/CSS/JavaScript site for doctors and administrators. It uses Supabase Auth and the existing RLS-protected PostgreSQL tables.

## Pages

- `index.html`: doctor/admin sign-in
- `dashboard.html`: live summary, schedules, device alerts and audit activity
- `patients.html`: searchable patient registry
- `patient.html?id=...`: patient, care-plan, device and session details
- `devices.html`: device inventory, status, assignment and replacement
- `care-plans.html`: prescribed frequency and duration plans
- `schedules.html`: single or multi-patient scheduling and reminders
- `sessions.html`: synchronized device session history and export
- `reports.html`: adherence and frequency-use summaries
- `alerts.html`: device, missed-session and notification exceptions
- `audit.html`: organization activity history
- `settings.html`: clinic settings and staff access

## Required database update

Before using write actions, run
`supabase/migrations/202607130004_portal_workflows.sql` in the Supabase SQL
Editor. It adds organization-scoped write policies; without it, Supabase will
correctly reject create and update operations.

Staff invitations are sent through `https://neurovibeapi.netlify.app/api/staff`.
On the API Netlify site, set `ALLOWED_ORIGIN` to the final doctor portal origin,
for example `https://neurovibe-doctor-portal.netlify.app`.

## Optional fictional demo records

For a populated local dashboard, run `supabase/seeds/local_demo_data.sql` in
the Supabase SQL Editor after migration 004. It is repeatable and uses only
clearly fictional `.test` patient records and `DEMO` device identifiers.

## Run locally on Windows

Double-click `start-portal.cmd`, keep its terminal window open, and visit:

```text
http://127.0.0.1:4173
```

Alternatively, from PowerShell run `npm.cmd run dev`. Use `npm.cmd`, because
some Windows PowerShell installations block the `npm.ps1` wrapper.

Do not open `index.html` directly from File Explorer. Browser security blocks
the JavaScript modules and Supabase authentication on `file://` pages.

## Netlify

Create a separate Netlify site from the same repository and set:

```text
Base directory: doctor-portal
Publish directory: .
Build command: leave empty
```

The Supabase URL and publishable key are safe browser configuration. Never add a Supabase secret key to this directory.

Before sign-in works, create the administrator in Supabase Auth and ensure its `public.profiles` row has `role = 'admin'`, `status = 'active'`, and an organization.

If an existing Auth user cannot enter, run `supabase/repair-admin-login.sql` in Supabase SQL Editor after replacing both email placeholders.
