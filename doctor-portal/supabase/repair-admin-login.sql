-- Run in Supabase SQL Editor only after replacing the email and display name.
-- This repairs the common case where auth.users exists but public.profiles is
-- missing, inactive, has the patient role, or has no organization.

do $$
declare
  target_email text := 'ashish@alphaion.io';
  target_name text := 'Primary Administrator';
  target_user_id uuid;
  target_organization_id uuid;
begin
  select id into target_user_id
  from auth.users
  where lower(email) = lower(target_email)
  limit 1;

  if target_user_id is null then
    raise exception 'No Supabase Auth user exists for %', target_email;
  end if;

  select id into target_organization_id
  from public.organizations
  where status = 'active'
  order by created_at
  limit 1;

  if target_organization_id is null then
    insert into public.organizations (name)
    values ('NeuroVibe Pilot Clinic')
    returning id into target_organization_id;
  end if;

  insert into public.profiles (
    id, organization_id, email, full_name, role, status
  ) values (
    target_user_id, target_organization_id, target_email,
    target_name, 'admin', 'active'
  )
  on conflict (id) do update
  set organization_id = excluded.organization_id,
      email = excluded.email,
      full_name = excluded.full_name,
      role = 'admin',
      status = 'active',
      updated_at = now();
end
$$;

-- The result must show role=admin, status=active, and a non-null organization.
select p.id, p.email, p.full_name, p.role, p.status, p.organization_id
from public.profiles p
where lower(p.email) = lower('ashish@alphaion.io');
