# NeuroVibe Supabase setup

## Project choices

When creating the project:

- Plan: **Free** for prototype data only.
- Region: choose the nearest available region to the intended users.
- Database password: generate a strong password and store it in a password manager.
- Framework quickstart: **JavaScript** for the current HTML/CSS/JS portal.
- API style: use `supabase-js` for authenticated web/mobile reads and Netlify Functions for all writes.

The framework selection does not change PostgreSQL. A future Flutter patient app can use the same project and database.

## Apply the schema

The preferred repeatable workflow uses the Supabase CLI and the files in `supabase/migrations`:

```powershell
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REF
npx supabase db push
```

For a first one-off prototype, the migration files can instead be executed in filename order from **Supabase Dashboard > SQL Editor**. Existing projects adding device replacement must apply `202607170006_device_independence.sql` before deploying the matching portal, API, app, or firmware changes. Do not combine the two approaches afterward without repairing migration history.

## Bootstrap the first organization and administrator

1. In **Authentication > Users**, create the first administrator account.
2. Copy its user UUID.
3. Run this once in SQL Editor, replacing the example values:

```sql
with new_org as (
  insert into public.organizations (name)
  values ('NeuroVibe Pilot Clinic')
  returning id
)
update public.profiles
set organization_id = (select id from new_org),
    full_name = 'Primary Administrator',
    role = 'admin',
    status = 'active'
where id = 'REPLACE-WITH-AUTH-USER-UUID'::uuid;
```

Verify that exactly one profile row was updated. Public sign-up can never grant doctor/admin access; new accounts always begin as invited patients.

## Netlify environment variables

Find the URL and keys in **Project Settings > API** (the exact dashboard label may vary):

```text
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key
SUPABASE_SECRET_KEY=your-server-secret-key
DEVICE_TOKEN_PEPPER=another-long-random-secret
```

Put these in Netlify environment variables. Never put `SUPABASE_SECRET_KEY`, a legacy `service_role` key, the database password, or `DEVICE_TOKEN_PEPPER` in `public/index.html`, mobile code, ESP32 firmware, or Git.

## Tables and workflows covered

- Organization and doctor/patient profiles
- Patient enrollment, consent and exit lifecycle
- Device registry, one-time enrollment and revocable credentials
- Device assignment and faulty-device replacement history
- Versioned care plans constrained to 0–230 Hz
- Individual and batch-created schedules
- Therapy records uploaded through device Wi-Fi or mobile BLE relay
- Idempotent sync receipts
- Device heartbeat/online status
- Patient notifications
- Administrative audit trail

## Prototype boundary

Use fabricated patient information on the Free plan. Before real patient or clinical data, complete a formal security/privacy review, use an appropriate paid plan and agreements, verify backups and retention, add monitoring, rotate credentials, and test recovery.
