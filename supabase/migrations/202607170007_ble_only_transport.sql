-- NeuroSense 0.9+ has no direct network stack. All records are transferred
-- over BLE and uploaded by an authenticated NeuroVibe patient app.

update public.device_credentials
set status = 'revoked',
    revoked_at = coalesce(revoked_at, now())
where status = 'sync_only';

alter table public.device_credentials
  drop constraint if exists device_credentials_status_check;

alter table public.device_credentials
  add constraint device_credentials_status_check
  check (status in ('active', 'revoked'));
