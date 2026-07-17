-- Device-independent patient assignment and replacement.
-- The physical device remains immutable; only the active assignment changes.

alter table public.devices
  add column if not exists hardware_id text;

create unique index if not exists devices_hardware_id_unique
  on public.devices (hardware_id)
  where hardware_id is not null;

alter table public.device_assignments
  add column if not exists lease_expires_at timestamptz,
  add column if not exists last_renewed_at timestamptz,
  add column if not exists revoked_at timestamptz;

update public.device_assignments
set lease_expires_at = coalesce(lease_expires_at, now() + interval '7 days'),
    last_renewed_at = coalesce(last_renewed_at, now())
where status = 'active';

alter table public.device_credentials
  add column if not exists assignment_id uuid
    references public.device_assignments(id) on delete cascade;

alter table public.device_credentials
  drop constraint if exists device_credentials_status_check;

alter table public.device_credentials
  add constraint device_credentials_status_check
  check (status in ('active', 'sync_only', 'revoked'));

create index if not exists device_credentials_assignment_idx
  on public.device_credentials (assignment_id, status);

alter table public.notifications
  drop constraint if exists notifications_type_check;

alter table public.notifications
  add constraint notifications_type_check
  check (type in (
    'session_reminder', 'schedule_changed', 'device_status',
    'assignment_changed', 'general'
  ));

create or replace function public.assign_or_replace_patient_device(
  p_patient_id uuid,
  p_new_device_id text,
  p_old_device_status text default 'return_pending',
  p_reason text default 'device_assignment'
)
returns table (
  assignment_id uuid,
  previous_assignment_id uuid,
  previous_device_id text,
  new_device_id text,
  lease_expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  actor_org uuid;
  patient_record public.patients%rowtype;
  new_device public.devices%rowtype;
  current_assignment public.device_assignments%rowtype;
  created_assignment public.device_assignments%rowtype;
  lease_until timestamptz := now() + interval '7 days';
begin
  select organization_id into actor_org
  from public.profiles
  where id = actor_id
    and role in ('admin', 'doctor')
    and status = 'active';

  if actor_org is null then
    raise exception 'Only active clinical staff can assign devices.'
      using errcode = '42501';
  end if;

  select * into patient_record
  from public.patients
  where id = p_patient_id
    and organization_id = actor_org
  for update;

  if not found then
    raise exception 'Patient is unavailable or outside your organization.'
      using errcode = 'P0002';
  end if;

  if patient_record.doctor_id <> actor_id
     and public.current_app_role() <> 'admin' then
    raise exception 'This patient is outside your permitted caseload.'
      using errcode = '42501';
  end if;

  select * into new_device
  from public.devices
  where id = p_new_device_id
    and organization_id = actor_org
  for update;

  if not found then
    raise exception 'Replacement device was not found.'
      using errcode = 'P0002';
  end if;

  if new_device.lifecycle_status not in ('factory_new', 'available', 'sanitized') then
    raise exception 'Replacement device is not available.'
      using errcode = '23514';
  end if;

  select * into current_assignment
  from public.device_assignments
  where patient_id = p_patient_id
    and status = 'active'
  for update;

  if found and current_assignment.device_id = p_new_device_id then
    raise exception 'This device is already assigned to the patient.'
      using errcode = '23514';
  end if;

  if found then
    update public.device_assignments
    set status = 'closed',
        ends_at = now(),
        revoked_at = now(),
        lease_expires_at = now(),
        closure_reason = coalesce(nullif(trim(p_reason), ''), 'device_replacement')
    where id = current_assignment.id;

    update public.device_credentials
    set status = 'sync_only',
        revoked_at = null
    where device_id = current_assignment.device_id
      and status = 'active';

    if p_old_device_status not in (
      'available', 'faulty', 'return_pending', 'under_repair',
      'sanitized', 'retired', 'lost'
    ) then
      raise exception 'Invalid previous device status.'
        using errcode = '23514';
    end if;

    update public.devices
    set lifecycle_status = p_old_device_status
    where id = current_assignment.device_id;
  end if;

  insert into public.device_assignments (
    organization_id,
    patient_id,
    device_id,
    assigned_by,
    status,
    starts_at,
    lease_expires_at,
    last_renewed_at,
    replacement_for_assignment_id
  )
  values (
    actor_org,
    p_patient_id,
    p_new_device_id,
    actor_id,
    'active',
    now(),
    lease_until,
    now(),
    case when current_assignment.id is null then null else current_assignment.id end
  )
  returning * into created_assignment;

  update public.devices
  set lifecycle_status = 'assigned'
  where id = p_new_device_id;

  update public.patients
  set program_status = 'active'
  where id = p_patient_id;

  if patient_record.user_id is not null then
    insert into public.notifications (
      organization_id,
      recipient_id,
      type,
      title,
      message,
      status,
      deliver_at
    )
    values (
      actor_org,
      patient_record.user_id,
      'assignment_changed',
      case when current_assignment.id is null
        then 'NeuroSense assigned'
        else 'Replacement NeuroSense assigned'
      end,
      case when current_assignment.id is null
        then 'Your doctor assigned NeuroSense ' || p_new_device_id || '.'
        else 'Your assigned NeuroSense changed from ' ||
          current_assignment.device_id || ' to ' || p_new_device_id || '.'
      end,
      'pending',
      now()
    );
  end if;

  insert into public.audit_events (
    organization_id,
    actor_id,
    action,
    entity_type,
    entity_id,
    summary,
    metadata
  )
  values (
    actor_org,
    actor_id,
    case when current_assignment.id is null
      then 'device.assigned'
      else 'device.replaced'
    end,
    'device_assignment',
    created_assignment.id::text,
    case when current_assignment.id is null
      then 'Device assigned to patient'
      else 'Patient device replaced and previous credential revoked'
    end,
    jsonb_build_object(
      'patient_id', p_patient_id,
      'previous_device_id', current_assignment.device_id,
      'new_device_id', p_new_device_id,
      'lease_expires_at', lease_until
    )
  );

  return query
  select
    created_assignment.id,
    current_assignment.id,
    current_assignment.device_id,
    created_assignment.device_id,
    created_assignment.lease_expires_at;
end;
$$;

revoke all on function public.assign_or_replace_patient_device(uuid, text, text, text)
  from public;
grant execute on function public.assign_or_replace_patient_device(uuid, text, text, text)
  to authenticated;

-- Preserve delayed records from a replaced device when the session genuinely
-- occurred during that device's assignment window. New sessions from an
-- expired or closed assignment remain rejected.
create or replace function public.ingest_therapy_session(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  matched_assignment public.device_assignments%rowtype;
  existing_session public.therapy_sessions%rowtype;
  session_started timestamptz := (payload ->> 'started_at_utc')::timestamptz;
begin
  select * into matched_assignment
  from public.device_assignments
  where id = (payload ->> 'assignment_id')::uuid
    and patient_id = (payload ->> 'patient_id')::uuid
    and device_id = payload ->> 'device_id'
    and (
      (
        status = 'active'
        and session_started >= starts_at
        and (lease_expires_at is null or session_started <= lease_expires_at)
      )
      or
      (
        status = 'closed'
        and ends_at is not null
        and session_started between starts_at and ends_at
      )
    );

  if not found then
    raise exception 'invalid_assignment' using errcode = 'P0001';
  end if;

  select * into existing_session
  from public.therapy_sessions
  where id = payload ->> 'session_id';

  if found then
    if existing_session.device_id <> payload ->> 'device_id' then
      raise exception 'session_identity_conflict' using errcode = 'P0001';
    end if;
    return jsonb_build_object('status', 'already_accepted', 'session_id', existing_session.id);
  end if;

  begin
    insert into public.therapy_sessions (
      id, organization_id, patient_id, device_id, assignment_id, schedule_id,
      device_sequence, started_at_utc, ended_at_utc, requested_hz,
      estimated_hz, measured_hz, pwm_value, duration_seconds, status,
      completion_reason, sync_source, timestamp_source
    ) values (
      payload ->> 'session_id',
      matched_assignment.organization_id,
      matched_assignment.patient_id,
      matched_assignment.device_id,
      matched_assignment.id,
      nullif(payload ->> 'schedule_id', '')::uuid,
      (payload ->> 'device_sequence')::bigint,
      session_started,
      (payload ->> 'ended_at_utc')::timestamptz,
      (payload ->> 'requested_hz')::numeric,
      nullif(payload ->> 'estimated_hz', '')::numeric,
      nullif(payload ->> 'measured_hz', '')::numeric,
      (payload ->> 'pwm_value')::integer,
      (payload ->> 'duration_seconds')::integer,
      payload ->> 'status',
      nullif(payload ->> 'completion_reason', ''),
      payload ->> 'sync_source',
      nullif(payload ->> 'timestamp_source', '')
    );
  exception when unique_violation then
    select * into existing_session
    from public.therapy_sessions
    where id = payload ->> 'session_id';
    if found and existing_session.device_id = payload ->> 'device_id' then
      return jsonb_build_object('status', 'already_accepted', 'session_id', existing_session.id);
    end if;
    raise exception 'device_sequence_conflict' using errcode = 'P0001';
  end;

  insert into public.sync_receipts (event_id, device_id, route)
  values (payload ->> 'session_id', matched_assignment.device_id, payload ->> 'sync_source')
  on conflict (event_id) do nothing;

  if nullif(payload ->> 'schedule_id', '') is not null then
    update public.scheduled_sessions
    set status = 'completed'
    where id = (payload ->> 'schedule_id')::uuid
      and patient_id = matched_assignment.patient_id;
  end if;

  update public.devices
  set last_seen_at = now(),
      pending_record_count = greatest(pending_record_count - 1, 0)
  where id = matched_assignment.device_id;

  insert into public.audit_events (
    organization_id, actor_id, action, entity_type, entity_id, summary, metadata
  ) values (
    matched_assignment.organization_id,
    null,
    'therapy_session.accepted',
    'therapy_session',
    payload ->> 'session_id',
    'Accepted therapy session from ' || (payload ->> 'sync_source'),
    jsonb_build_object(
      'device_id', matched_assignment.device_id,
      'assignment_status', matched_assignment.status
    )
  );

  return jsonb_build_object('status', 'accepted', 'session_id', payload ->> 'session_id');
end;
$$;

revoke all on function public.ingest_therapy_session(jsonb) from public;
revoke all on function public.ingest_therapy_session(jsonb) from anon;
revoke all on function public.ingest_therapy_session(jsonb) from authenticated;
grant execute on function public.ingest_therapy_session(jsonb) to service_role;
