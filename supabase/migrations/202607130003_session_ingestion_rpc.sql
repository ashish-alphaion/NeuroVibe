-- Atomic and idempotent ingestion used by the Netlify device API.
-- Only the trusted server role may execute this function.

create or replace function public.ingest_therapy_session(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  matched_assignment public.device_assignments%rowtype;
  existing_session public.therapy_sessions%rowtype;
begin
  select * into matched_assignment
  from public.device_assignments
  where id = (payload ->> 'assignment_id')::uuid
    and patient_id = (payload ->> 'patient_id')::uuid
    and device_id = payload ->> 'device_id'
    and status = 'active';

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
      (payload ->> 'started_at_utc')::timestamptz,
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
    jsonb_build_object('device_id', matched_assignment.device_id)
  );

  return jsonb_build_object('status', 'accepted', 'session_id', payload ->> 'session_id');
end;
$$;

revoke all on function public.ingest_therapy_session(jsonb) from public;
revoke all on function public.ingest_therapy_session(jsonb) from anon;
revoke all on function public.ingest_therapy_session(jsonb) from authenticated;
grant execute on function public.ingest_therapy_session(jsonb) to service_role;
