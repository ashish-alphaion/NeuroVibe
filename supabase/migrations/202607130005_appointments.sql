-- Separate clinical appointments from physical NeuroSense device usage.
-- `therapy_sessions` remains the device vibration history. Appointments are
-- doctor/patient visits and never carry frequency or PWM fields.

create table public.appointments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  patient_id uuid not null references public.patients(id),
  doctor_id uuid not null references public.profiles(id),
  created_by uuid not null references public.profiles(id),
  title text not null default 'Clinic appointment' check (length(trim(title)) between 2 and 120),
  appointment_type text not null default 'clinic'
    check (appointment_type in ('clinic', 'video', 'phone', 'home_visit')),
  scheduled_for timestamptz not null,
  duration_minutes integer not null default 30 check (duration_minutes between 5 and 480),
  location text,
  notes text,
  status text not null default 'scheduled'
    check (status in ('scheduled', 'confirmed', 'completed', 'cancelled', 'no_show')),
  reminder_minutes integer not null default 60 check (reminder_minutes between 0 and 10080),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index appointments_patient_time_idx on public.appointments (patient_id, scheduled_for desc);
create index appointments_doctor_time_idx on public.appointments (doctor_id, scheduled_for desc);
create index appointments_organization_time_idx on public.appointments (organization_id, scheduled_for desc);

create trigger appointments_set_updated_at before update on public.appointments
for each row execute function public.set_updated_at();

alter table public.notifications
  add column appointment_id uuid references public.appointments(id) on delete cascade;

alter table public.appointments enable row level security;

create policy "users read permitted appointments"
on public.appointments for select to authenticated
using (public.can_access_patient(patient_id));

create policy "staff create permitted appointments"
on public.appointments for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and created_by = auth.uid()
  and public.can_access_patient(patient_id)
  and public.is_clinical_staff()
);

create policy "staff update permitted appointments"
on public.appointments for update to authenticated
using (public.can_access_patient(patient_id) and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

grant select, insert, update on public.appointments to authenticated;

alter table public.notifications drop constraint if exists notifications_type_check;
alter table public.notifications add constraint notifications_type_check
  check (type in (
    'appointment_reminder', 'appointment_changed',
    'session_reminder', 'schedule_changed', 'device_status', 'general'
  ));
