create table if not exists public.device_hz_telemetry (
  device_id text primary key,
  requested_hz numeric(6, 2) not null
    check (requested_hz >= 0 and requested_hz <= 230),
  estimated_hz numeric(6, 2)
    check (estimated_hz is null or (estimated_hz >= 0 and estimated_hz <= 230)),
  pwm_value smallint not null
    check (pwm_value >= 0 and pwm_value <= 255),
  running boolean not null,
  uptime_ms bigint not null
    check (uptime_ms >= 0),
  received_at_utc timestamptz not null default now()
);

alter table public.device_hz_telemetry enable row level security;

comment on table public.device_hz_telemetry is
  'Latest non-patient prototype Hz telemetry for each ESP32-C3 device.';
