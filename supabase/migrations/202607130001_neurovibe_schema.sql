-- NeuroVibe managed PostgreSQL schema for Supabase.
-- All timestamps are stored in UTC as timestamptz. All frequency limits are
-- enforced by PostgreSQL as a final safety boundary.

create extension if not exists pgcrypto;

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) between 2 and 120),
  status text not null default 'active' check (status in ('active', 'suspended', 'closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  organization_id uuid references public.organizations(id),
  email text not null,
  full_name text not null default '',
  role text not null default 'patient' check (role in ('admin', 'doctor', 'patient')),
  status text not null default 'invited' check (status in ('invited', 'active', 'disabled', 'closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index profiles_email_lower_unique on public.profiles (lower(email));
create index profiles_organization_role_idx on public.profiles (organization_id, role);

create table public.patients (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  user_id uuid unique references public.profiles(id) on delete set null,
  doctor_id uuid not null references public.profiles(id),
  patient_code text not null,
  full_name text not null,
  date_of_birth date,
  gender text check (gender is null or gender in ('female', 'male', 'non_binary', 'prefer_not_to_say', 'other')),
  phone text,
  email text,
  program_status text not null default 'invited'
    check (program_status in ('invited', 'enrolled', 'active', 'paused', 'exit_requested', 'closed')),
  consent_status text not null default 'pending'
    check (consent_status in ('pending', 'accepted', 'declined', 'withdrawn')),
  exit_requested_at timestamptz,
  exited_at timestamptz,
  exit_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, patient_code)
);

create index patients_doctor_idx on public.patients (doctor_id, program_status);
create index patients_organization_idx on public.patients (organization_id);

create table public.devices (
  id text primary key check (id ~ '^[A-Za-z0-9._:-]{3,128}$'),
  organization_id uuid references public.organizations(id),
  display_name text not null,
  serial_number text unique,
  lifecycle_status text not null default 'factory_new'
    check (lifecycle_status in (
      'factory_new', 'available', 'claimed', 'assigned', 'active', 'faulty',
      'return_pending', 'under_repair', 'sanitized', 'retired', 'lost'
    )),
  firmware_version text,
  hardware_revision text,
  last_seen_at timestamptz,
  last_ip inet,
  pending_record_count integer not null default 0 check (pending_record_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index devices_organization_status_idx on public.devices (organization_id, lifecycle_status);
create index devices_last_seen_idx on public.devices (last_seen_at desc);

-- Only the Netlify server accesses credentials. Store a SHA-256/HMAC hash,
-- never the raw device token.
create table public.device_credentials (
  id uuid primary key default gen_random_uuid(),
  device_id text not null references public.devices(id) on delete cascade,
  token_hash text not null unique,
  status text not null default 'active' check (status in ('active', 'revoked')),
  created_by uuid references public.profiles(id),
  last_used_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

create unique index one_active_credential_per_device
  on public.device_credentials (device_id) where status = 'active';

-- A short-lived, one-use claim code joins a physical device to an organization.
create table public.device_enrollments (
  id uuid primary key default gen_random_uuid(),
  device_id text not null references public.devices(id) on delete cascade,
  organization_id uuid not null references public.organizations(id),
  claim_code_hash text not null unique,
  created_by uuid not null references public.profiles(id),
  status text not null default 'pending' check (status in ('pending', 'claimed', 'expired', 'cancelled')),
  expires_at timestamptz not null,
  claimed_at timestamptz,
  created_at timestamptz not null default now()
);

create index device_enrollments_device_status_idx
  on public.device_enrollments (device_id, status, expires_at);

create table public.device_assignments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  patient_id uuid not null references public.patients(id),
  device_id text not null references public.devices(id),
  assigned_by uuid not null references public.profiles(id),
  status text not null default 'active' check (status in ('active', 'closed', 'cancelled')),
  starts_at timestamptz not null default now(),
  ends_at timestamptz,
  closure_reason text,
  replacement_for_assignment_id uuid references public.device_assignments(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((status = 'active' and ends_at is null) or status <> 'active')
);

create unique index one_active_device_per_patient
  on public.device_assignments (patient_id) where status = 'active';
create unique index one_active_patient_per_device
  on public.device_assignments (device_id) where status = 'active';
create index assignments_patient_history_idx
  on public.device_assignments (patient_id, starts_at desc);

create table public.care_plans (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  patient_id uuid not null references public.patients(id),
  created_by uuid not null references public.profiles(id),
  version integer not null check (version > 0),
  name text not null,
  status text not null default 'draft'
    check (status in ('draft', 'active', 'expired', 'replaced', 'cancelled')),
  min_hz numeric(6,2) not null check (min_hz between 0 and 230),
  target_hz numeric(6,2) not null check (target_hz between 0 and 230),
  max_hz numeric(6,2) not null check (max_hz between 0 and 230),
  duration_seconds integer not null check (duration_seconds > 0),
  max_duration_seconds integer not null check (max_duration_seconds >= duration_seconds),
  manual_control_allowed boolean not null default false,
  effective_from timestamptz not null default now(),
  effective_to timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (min_hz <= target_hz and target_hz <= max_hz),
  unique (patient_id, version)
);

create unique index one_active_care_plan_per_patient
  on public.care_plans (patient_id) where status = 'active';

create table public.scheduled_sessions (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  patient_id uuid not null references public.patients(id),
  care_plan_id uuid not null references public.care_plans(id),
  created_by uuid not null references public.profiles(id),
  scheduled_for timestamptz not null,
  target_hz numeric(6,2) not null check (target_hz between 0 and 230),
  duration_seconds integer not null check (duration_seconds > 0),
  status text not null default 'scheduled'
    check (status in ('scheduled', 'ready', 'running', 'completed', 'missed', 'cancelled')),
  reminder_minutes integer not null default 15 check (reminder_minutes >= 0),
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index schedules_patient_time_idx
  on public.scheduled_sessions (patient_id, scheduled_for desc);
create index schedules_due_idx
  on public.scheduled_sessions (status, scheduled_for);

create table public.therapy_sessions (
  id text primary key check (id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  organization_id uuid not null references public.organizations(id),
  patient_id uuid not null references public.patients(id),
  device_id text not null references public.devices(id),
  assignment_id uuid not null references public.device_assignments(id),
  schedule_id uuid references public.scheduled_sessions(id),
  device_sequence bigint not null check (device_sequence >= 0),
  started_at_utc timestamptz not null,
  ended_at_utc timestamptz not null,
  requested_hz numeric(6,2) not null check (requested_hz between 0 and 230),
  estimated_hz numeric(6,2) check (estimated_hz is null or estimated_hz between 0 and 230),
  measured_hz numeric(6,2) check (measured_hz is null or measured_hz between 0 and 230),
  pwm_value integer not null check (pwm_value between 0 and 255),
  duration_seconds integer not null check (duration_seconds >= 0),
  status text not null check (status in ('completed', 'interrupted', 'failed')),
  completion_reason text,
  sync_source text not null check (sync_source in ('device_wifi', 'mobile_ble_relay')),
  timestamp_source text check (timestamp_source is null or timestamp_source in ('ntp', 'phone', 'uptime_fallback')),
  received_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (device_id, device_sequence),
  check (ended_at_utc >= started_at_utc)
);

create index sessions_patient_time_idx on public.therapy_sessions (patient_id, started_at_utc desc);
create index sessions_device_sequence_idx on public.therapy_sessions (device_id, device_sequence desc);
create index sessions_received_idx on public.therapy_sessions (received_at desc);

create table public.sync_receipts (
  event_id text primary key references public.therapy_sessions(id) on delete cascade,
  device_id text not null references public.devices(id),
  route text not null check (route in ('device_wifi', 'mobile_ble_relay')),
  acknowledged_at timestamptz not null default now()
);

create table public.device_heartbeats (
  id bigint generated always as identity primary key,
  device_id text not null references public.devices(id) on delete cascade,
  organization_id uuid references public.organizations(id),
  firmware_version text,
  wifi_rssi integer,
  pending_record_count integer not null default 0 check (pending_record_count >= 0),
  free_heap_bytes integer,
  status text not null default 'online' check (status in ('online', 'idle', 'running', 'fault', 'offline')),
  recorded_at timestamptz not null default now()
);

create index heartbeats_device_time_idx on public.device_heartbeats (device_id, recorded_at desc);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  scheduled_session_id uuid references public.scheduled_sessions(id) on delete cascade,
  type text not null check (type in ('session_reminder', 'schedule_changed', 'device_status', 'general')),
  title text not null,
  message text not null,
  status text not null default 'pending' check (status in ('pending', 'sent', 'failed', 'read')),
  deliver_at timestamptz not null default now(),
  sent_at timestamptz,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create index notifications_recipient_idx on public.notifications (recipient_id, status, deliver_at desc);

create table public.audit_events (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  actor_id uuid references public.profiles(id),
  action text not null,
  entity_type text not null,
  entity_id text not null,
  summary text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index audit_entity_idx on public.audit_events (entity_type, entity_id, created_at desc);
create index audit_organization_time_idx on public.audit_events (organization_id, created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger organizations_set_updated_at before update on public.organizations
for each row execute function public.set_updated_at();
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();
create trigger patients_set_updated_at before update on public.patients
for each row execute function public.set_updated_at();
create trigger devices_set_updated_at before update on public.devices
for each row execute function public.set_updated_at();
create trigger assignments_set_updated_at before update on public.device_assignments
for each row execute function public.set_updated_at();
create trigger care_plans_set_updated_at before update on public.care_plans
for each row execute function public.set_updated_at();
create trigger schedules_set_updated_at before update on public.scheduled_sessions
for each row execute function public.set_updated_at();

-- New public sign-ups are always patients. Never trust user-supplied metadata to
-- grant doctor/admin access; an administrator must promote invited staff.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, email, full_name, role, status)
  values (
    new.id,
    coalesce(new.email, new.id::text || '@no-email.local'),
    coalesce(new.raw_user_meta_data ->> 'full_name', ''),
    'patient',
    'invited'
  );
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_auth_user();
