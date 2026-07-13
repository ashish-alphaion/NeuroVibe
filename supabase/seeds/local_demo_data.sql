-- NeuroVibe local portal demo data
-- Run only in the prototype Supabase project after migrations 001-004.
-- All people and records below are fictional.

do $$
declare
  admin_id uuid;
  org_id uuid;
begin
  select p.id, p.organization_id into admin_id, org_id
  from public.profiles p
  where lower(p.email) = lower('ashish@alphaion.io')
    and p.role in ('admin', 'doctor')
    and p.status = 'active'
  limit 1;

  if admin_id is null or org_id is null then
    raise exception 'Active staff profile ashish@alphaion.io with an organization was not found';
  end if;

  insert into public.patients (
    id, organization_id, doctor_id, patient_code, full_name, date_of_birth,
    gender, phone, email, program_status, consent_status
  ) values
    ('10000000-0000-4000-8000-000000000001', org_id, admin_id, 'NV-DEMO-001', 'Aarav Demo', '1991-04-12', 'male', '+91 90000 00001', 'aarav.demo@example.test', 'active', 'accepted'),
    ('10000000-0000-4000-8000-000000000002', org_id, admin_id, 'NV-DEMO-002', 'Meera Demo', '1986-09-23', 'female', '+91 90000 00002', 'meera.demo@example.test', 'active', 'accepted'),
    ('10000000-0000-4000-8000-000000000003', org_id, admin_id, 'NV-DEMO-003', 'Kabir Demo', '1998-02-08', 'male', '+91 90000 00003', 'kabir.demo@example.test', 'enrolled', 'pending')
  on conflict (id) do update set
    organization_id = excluded.organization_id,
    doctor_id = excluded.doctor_id,
    full_name = excluded.full_name,
    program_status = excluded.program_status,
    consent_status = excluded.consent_status;

  insert into public.devices (
    id, organization_id, display_name, serial_number, lifecycle_status,
    firmware_version, hardware_revision, last_seen_at, pending_record_count
  ) values
    ('NEUROSENSE-DEMO-001', org_id, 'NeuroSense Demo 01', 'NS-DEMO-0001', 'active', '0.4.0-demo', 'ESP32-C3-R1', now() - interval '4 minutes', 0),
    ('NEUROSENSE-DEMO-002', org_id, 'NeuroSense Demo 02', 'NS-DEMO-0002', 'assigned', '0.4.0-demo', 'ESP32-C3-R1', now() - interval '18 minutes', 2),
    ('NEUROSENSE-DEMO-003', org_id, 'NeuroSense Demo 03', 'NS-DEMO-0003', 'available', '0.4.0-demo', 'ESP32-C3-R1', now() - interval '1 day', 0),
    ('NEUROSENSE-DEMO-004', org_id, 'NeuroSense Demo 04', 'NS-DEMO-0004', 'faulty', '0.3.2-demo', 'ESP32-C3-R1', now() - interval '3 days', 5)
  on conflict (id) do update set
    organization_id = excluded.organization_id,
    display_name = excluded.display_name,
    lifecycle_status = excluded.lifecycle_status,
    firmware_version = excluded.firmware_version,
    last_seen_at = excluded.last_seen_at,
    pending_record_count = excluded.pending_record_count;

  insert into public.device_assignments (
    id, organization_id, patient_id, device_id, assigned_by, status, starts_at
  ) values
    ('a0000000-0000-4000-8000-000000000001', org_id, '10000000-0000-4000-8000-000000000001', 'NEUROSENSE-DEMO-001', admin_id, 'active', now() - interval '21 days'),
    ('a0000000-0000-4000-8000-000000000002', org_id, '10000000-0000-4000-8000-000000000002', 'NEUROSENSE-DEMO-002', admin_id, 'active', now() - interval '10 days')
  on conflict (id) do update set
    organization_id = excluded.organization_id,
    patient_id = excluded.patient_id,
    device_id = excluded.device_id,
    assigned_by = excluded.assigned_by;

  insert into public.care_plans (
    id, organization_id, patient_id, created_by, version, name, status,
    min_hz, target_hz, max_hz, duration_seconds, max_duration_seconds,
    manual_control_allowed, effective_from
  ) values
    ('c0000000-0000-4000-8000-000000000001', org_id, '10000000-0000-4000-8000-000000000001', admin_id, 1, 'Morning sensory protocol', 'active', 20, 85, 120, 600, 900, true, now() - interval '21 days'),
    ('c0000000-0000-4000-8000-000000000002', org_id, '10000000-0000-4000-8000-000000000002', admin_id, 1, 'Evening vibration protocol', 'active', 40, 110, 150, 480, 600, false, now() - interval '10 days')
  on conflict (id) do update set
    name = excluded.name,
    min_hz = excluded.min_hz,
    target_hz = excluded.target_hz,
    max_hz = excluded.max_hz,
    duration_seconds = excluded.duration_seconds,
    max_duration_seconds = excluded.max_duration_seconds,
    manual_control_allowed = excluded.manual_control_allowed;

  insert into public.scheduled_sessions (
    id, organization_id, patient_id, care_plan_id, created_by, scheduled_for,
    target_hz, duration_seconds, status, reminder_minutes, notes
  ) values
    ('d0000000-0000-4000-8000-000000000001', org_id, '10000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', admin_id, date_trunc('day', now()) + interval '10 hours', 85, 600, 'scheduled', 15, 'Fictional demonstration schedule'),
    ('d0000000-0000-4000-8000-000000000002', org_id, '10000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000002', admin_id, date_trunc('day', now()) + interval '17 hours 30 minutes', 110, 480, 'scheduled', 30, 'Fictional demonstration schedule'),
    ('d0000000-0000-4000-8000-000000000003', org_id, '10000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000002', admin_id, now() - interval '1 day', 110, 480, 'missed', 15, 'Demo missed-session alert')
  on conflict (id) do update set
    scheduled_for = excluded.scheduled_for,
    status = excluded.status,
    notes = excluded.notes;

  insert into public.therapy_sessions (
    id, organization_id, patient_id, device_id, assignment_id, schedule_id,
    device_sequence, started_at_utc, ended_at_utc, requested_hz, estimated_hz,
    measured_hz, pwm_value, duration_seconds, status, completion_reason,
    sync_source, timestamp_source
  ) values
    ('demo-session-001', org_id, '10000000-0000-4000-8000-000000000001', 'NEUROSENSE-DEMO-001', 'a0000000-0000-4000-8000-000000000001', null, 1001, now() - interval '3 days 10 minutes', now() - interval '3 days', 80, 79.4, 79.8, 89, 600, 'completed', 'timer_complete', 'device_wifi', 'ntp'),
    ('demo-session-002', org_id, '10000000-0000-4000-8000-000000000001', 'NEUROSENSE-DEMO-001', 'a0000000-0000-4000-8000-000000000001', null, 1002, now() - interval '2 days 10 minutes', now() - interval '2 days', 85, 84.1, 84.6, 94, 600, 'completed', 'timer_complete', 'mobile_ble_relay', 'phone'),
    ('demo-session-003', org_id, '10000000-0000-4000-8000-000000000001', 'NEUROSENSE-DEMO-001', 'a0000000-0000-4000-8000-000000000001', null, 1003, now() - interval '1 day 5 minutes', now() - interval '1 day', 90, 88.9, 89.2, 100, 300, 'interrupted', 'patient_stopped', 'device_wifi', 'ntp'),
    ('demo-session-004', org_id, '10000000-0000-4000-8000-000000000002', 'NEUROSENSE-DEMO-002', 'a0000000-0000-4000-8000-000000000002', null, 2001, now() - interval '2 days 8 minutes', now() - interval '2 days', 110, 108.7, 109.4, 122, 480, 'completed', 'timer_complete', 'device_wifi', 'ntp'),
    ('demo-session-005', org_id, '10000000-0000-4000-8000-000000000002', 'NEUROSENSE-DEMO-002', 'a0000000-0000-4000-8000-000000000002', null, 2002, now() - interval '1 day 8 minutes', now() - interval '1 day', 115, 113.6, 114.1, 127, 480, 'completed', 'timer_complete', 'mobile_ble_relay', 'phone')
  on conflict (id) do nothing;

  insert into public.device_heartbeats (
    device_id, organization_id, firmware_version, wifi_rssi,
    pending_record_count, free_heap_bytes, status, recorded_at
  )
  select 'NEUROSENSE-DEMO-001', org_id, '0.4.0-demo', -51, 0, 182400, 'idle', now() - interval '4 minutes'
  where not exists (
    select 1 from public.device_heartbeats
    where device_id = 'NEUROSENSE-DEMO-001' and recorded_at > now() - interval '1 hour'
  );

  insert into public.notifications (
    id, organization_id, recipient_id, type, title, message, status, deliver_at
  ) values
    ('f0000000-0000-4000-8000-000000000001', org_id, admin_id, 'device_status', 'Demo device requires attention', 'NeuroSense Demo 04 is marked faulty.', 'pending', now() - interval '2 hours')
  on conflict (id) do update set status = 'pending', deliver_at = excluded.deliver_at;

  insert into public.audit_events (
    id, organization_id, actor_id, action, entity_type, entity_id, summary, metadata, created_at
  ) values
    ('e0000000-0000-4000-8000-000000000001', org_id, admin_id, 'demo.seeded', 'organization', org_id::text, 'Fictional local demonstration data created', '{"demo":true}'::jsonb, now() - interval '4 days'),
    ('e0000000-0000-4000-8000-000000000002', org_id, admin_id, 'device.assigned', 'device', 'NEUROSENSE-DEMO-001', 'NeuroSense Demo 01 assigned to Aarav Demo', '{"demo":true}'::jsonb, now() - interval '21 days'),
    ('e0000000-0000-4000-8000-000000000003', org_id, admin_id, 'care_plan.created', 'care_plan', 'c0000000-0000-4000-8000-000000000002', 'Evening vibration protocol activated', '{"demo":true}'::jsonb, now() - interval '10 days')
  on conflict (id) do nothing;
end
$$;

select 'Demo data ready' as result,
       (select count(*) from public.patients where patient_code like 'NV-DEMO-%') as demo_patients,
       (select count(*) from public.devices where id like 'NEUROSENSE-DEMO-%') as demo_devices,
       (select count(*) from public.therapy_sessions where id like 'demo-session-%') as demo_sessions;
