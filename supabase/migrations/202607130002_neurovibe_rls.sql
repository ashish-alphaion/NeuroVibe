-- NeuroVibe least-privilege client access.
-- Netlify Functions use a Supabase secret key for all mutations. Browser/mobile
-- clients receive read access only, constrained by these RLS policies.

create or replace function public.current_organization_id()
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
  select organization_id from public.profiles where id = auth.uid()
$$;

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select role from public.profiles where id = auth.uid() and status = 'active'
$$;

create or replace function public.can_access_patient(target_patient_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.patients p
    where p.id = target_patient_id
      and p.organization_id = public.current_organization_id()
      and (
        (public.current_app_role() = 'admin')
        or (public.current_app_role() = 'doctor' and p.doctor_id = auth.uid())
        or (public.current_app_role() = 'patient' and p.user_id = auth.uid())
      )
  )
$$;

revoke all on function public.current_organization_id() from public;
revoke all on function public.current_app_role() from public;
revoke all on function public.can_access_patient(uuid) from public;
grant execute on function public.current_organization_id() to authenticated;
grant execute on function public.current_app_role() to authenticated;
grant execute on function public.can_access_patient(uuid) to authenticated;

alter table public.organizations enable row level security;
alter table public.profiles enable row level security;
alter table public.patients enable row level security;
alter table public.devices enable row level security;
alter table public.device_credentials enable row level security;
alter table public.device_enrollments enable row level security;
alter table public.device_assignments enable row level security;
alter table public.care_plans enable row level security;
alter table public.scheduled_sessions enable row level security;
alter table public.therapy_sessions enable row level security;
alter table public.sync_receipts enable row level security;
alter table public.device_heartbeats enable row level security;
alter table public.notifications enable row level security;
alter table public.audit_events enable row level security;

create policy "members read their organization"
on public.organizations for select to authenticated
using (id = public.current_organization_id());

create policy "members read permitted profiles"
on public.profiles for select to authenticated
using (
  id = auth.uid()
  or (
    organization_id = public.current_organization_id()
    and public.current_app_role() in ('admin', 'doctor')
  )
);

create policy "users read permitted patient records"
on public.patients for select to authenticated
using (public.can_access_patient(id));

create policy "users read permitted devices"
on public.devices for select to authenticated
using (
  organization_id = public.current_organization_id()
  and (
    public.current_app_role() in ('admin', 'doctor')
    or exists (
      select 1
      from public.device_assignments da
      join public.patients p on p.id = da.patient_id
      where da.device_id = devices.id
        and da.status = 'active'
        and p.user_id = auth.uid()
    )
  )
);

create policy "users read permitted assignments"
on public.device_assignments for select to authenticated
using (public.can_access_patient(patient_id));

create policy "users read permitted care plans"
on public.care_plans for select to authenticated
using (public.can_access_patient(patient_id));

create policy "users read permitted schedules"
on public.scheduled_sessions for select to authenticated
using (public.can_access_patient(patient_id));

create policy "users read permitted therapy sessions"
on public.therapy_sessions for select to authenticated
using (public.can_access_patient(patient_id));

create policy "users read permitted sync receipts"
on public.sync_receipts for select to authenticated
using (
  exists (
    select 1 from public.therapy_sessions ts
    where ts.id = sync_receipts.event_id
      and public.can_access_patient(ts.patient_id)
  )
);

create policy "users read permitted heartbeats"
on public.device_heartbeats for select to authenticated
using (
  exists (
    select 1 from public.devices d
    where d.id = device_heartbeats.device_id
      and d.organization_id = public.current_organization_id()
      and (
        public.current_app_role() in ('admin', 'doctor')
        or exists (
          select 1
          from public.device_assignments da
          join public.patients p on p.id = da.patient_id
          where da.device_id = d.id and da.status = 'active' and p.user_id = auth.uid()
        )
      )
  )
);

create policy "users read their notifications"
on public.notifications for select to authenticated
using (recipient_id = auth.uid());

create policy "staff read organization audit trail"
on public.audit_events for select to authenticated
using (
  organization_id = public.current_organization_id()
  and public.current_app_role() in ('admin', 'doctor')
);

-- Lock all anonymous access, and permit authenticated users to read only the
-- tables that have policies above. Server-side service/secret keys bypass RLS.
revoke all on all tables in schema public from anon;
revoke all on all tables in schema public from authenticated;

grant select on public.organizations to authenticated;
grant select on public.profiles to authenticated;
grant select on public.patients to authenticated;
grant select on public.devices to authenticated;
grant select on public.device_assignments to authenticated;
grant select on public.care_plans to authenticated;
grant select on public.scheduled_sessions to authenticated;
grant select on public.therapy_sessions to authenticated;
grant select on public.sync_receipts to authenticated;
grant select on public.device_heartbeats to authenticated;
grant select on public.notifications to authenticated;
grant select on public.audit_events to authenticated;

-- No client grants are intentionally given for credentials or enrollment codes.
revoke all on public.device_credentials from authenticated;
revoke all on public.device_enrollments from authenticated;

