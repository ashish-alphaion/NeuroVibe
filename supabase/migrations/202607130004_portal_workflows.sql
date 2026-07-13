-- Doctor portal write access. Apply after migrations 001-003.
-- Every policy is constrained to the signed-in user's organization.

create or replace function public.is_clinical_staff()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select public.current_app_role() in ('admin', 'doctor')
$$;

revoke all on function public.is_clinical_staff() from public;
grant execute on function public.is_clinical_staff() to authenticated;

create policy "admins update their organization"
on public.organizations for update to authenticated
using (id = public.current_organization_id() and public.current_app_role() = 'admin')
with check (id = public.current_organization_id() and public.current_app_role() = 'admin');

create policy "admins update organization profiles"
on public.profiles for update to authenticated
using (organization_id = public.current_organization_id() and public.current_app_role() = 'admin')
with check (organization_id = public.current_organization_id() and role in ('admin', 'doctor', 'patient'));

create policy "staff create permitted patients"
on public.patients for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and public.is_clinical_staff()
  and (public.current_app_role() = 'admin' or doctor_id = auth.uid())
);

create policy "staff update permitted patients"
on public.patients for update to authenticated
using (public.can_access_patient(id) and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff create organization devices"
on public.devices for insert to authenticated
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff update organization devices"
on public.devices for update to authenticated
using (organization_id = public.current_organization_id() and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff create permitted assignments"
on public.device_assignments for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and assigned_by = auth.uid()
  and public.can_access_patient(patient_id)
);

create policy "staff update permitted assignments"
on public.device_assignments for update to authenticated
using (public.can_access_patient(patient_id) and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff create permitted care plans"
on public.care_plans for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and created_by = auth.uid()
  and public.can_access_patient(patient_id)
);

create policy "staff update permitted care plans"
on public.care_plans for update to authenticated
using (public.can_access_patient(patient_id) and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff create permitted schedules"
on public.scheduled_sessions for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and created_by = auth.uid()
  and public.can_access_patient(patient_id)
);

create policy "staff update permitted schedules"
on public.scheduled_sessions for update to authenticated
using (public.can_access_patient(patient_id) and public.is_clinical_staff())
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff create organization notifications"
on public.notifications for insert to authenticated
with check (organization_id = public.current_organization_id() and public.is_clinical_staff());

create policy "staff update organization notifications"
on public.notifications for update to authenticated
using (
  organization_id = public.current_organization_id()
  and (recipient_id = auth.uid() or public.is_clinical_staff())
)
with check (organization_id = public.current_organization_id());

create policy "staff record organization audit events"
on public.audit_events for insert to authenticated
with check (
  organization_id = public.current_organization_id()
  and actor_id = auth.uid()
  and public.is_clinical_staff()
);

grant insert, update on public.patients to authenticated;
grant insert, update on public.devices to authenticated;
grant insert, update on public.device_assignments to authenticated;
grant insert, update on public.care_plans to authenticated;
grant insert, update on public.scheduled_sessions to authenticated;
grant insert, update on public.notifications to authenticated;
grant insert on public.audit_events to authenticated;
grant update on public.organizations to authenticated;
grant update on public.profiles to authenticated;
