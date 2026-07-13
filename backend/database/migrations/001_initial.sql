PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS organizations (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  email TEXT NOT NULL COLLATE NOCASE UNIQUE,
  name TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('doctor', 'admin', 'patient')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled', 'closed')),
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS patients (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  doctor_id TEXT NOT NULL REFERENCES users(id),
  patient_code TEXT NOT NULL,
  full_name TEXT NOT NULL,
  date_of_birth TEXT,
  gender TEXT CHECK (gender IS NULL OR gender IN ('female', 'male', 'non_binary', 'prefer_not_to_say', 'other')),
  phone TEXT,
  email TEXT,
  program_status TEXT NOT NULL DEFAULT 'invited'
    CHECK (program_status IN ('invited', 'enrolled', 'active', 'paused', 'exit_requested', 'closed')),
  consent_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (consent_status IN ('pending', 'accepted', 'declined', 'withdrawn')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (organization_id, patient_code)
);

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  display_name TEXT NOT NULL,
  serial_number TEXT UNIQUE,
  lifecycle_status TEXT NOT NULL DEFAULT 'available'
    CHECK (lifecycle_status IN ('factory_new', 'available', 'claimed', 'assigned', 'active', 'faulty', 'return_pending', 'under_repair', 'sanitized', 'retired', 'lost')),
  firmware_version TEXT,
  last_seen_at TEXT,
  pending_record_count INTEGER NOT NULL DEFAULT 0 CHECK (pending_record_count >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device_assignments (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  patient_id TEXT NOT NULL REFERENCES patients(id),
  device_id TEXT NOT NULL REFERENCES devices(id),
  assigned_by TEXT NOT NULL REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'closed', 'cancelled')),
  starts_at TEXT NOT NULL,
  ends_at TEXT,
  closure_reason TEXT,
  created_at TEXT NOT NULL,
  CHECK ((status = 'active' AND ends_at IS NULL) OR status <> 'active')
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_device_per_patient
  ON device_assignments(patient_id)
  WHERE status = 'active';

CREATE UNIQUE INDEX IF NOT EXISTS one_active_patient_per_device
  ON device_assignments(device_id)
  WHERE status = 'active';

CREATE TABLE IF NOT EXISTS care_plans (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  patient_id TEXT NOT NULL REFERENCES patients(id),
  created_by TEXT NOT NULL REFERENCES users(id),
  version INTEGER NOT NULL,
  name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('draft', 'active', 'expired', 'replaced', 'cancelled')),
  min_hz REAL NOT NULL CHECK (min_hz >= 0 AND min_hz <= 230),
  target_hz REAL NOT NULL CHECK (target_hz >= 0 AND target_hz <= 230),
  max_hz REAL NOT NULL CHECK (max_hz >= 0 AND max_hz <= 230),
  duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
  max_duration_seconds INTEGER NOT NULL CHECK (max_duration_seconds >= duration_seconds),
  manual_control_allowed INTEGER NOT NULL DEFAULT 0 CHECK (manual_control_allowed IN (0, 1)),
  effective_from TEXT NOT NULL,
  effective_to TEXT,
  created_at TEXT NOT NULL,
  CHECK (min_hz <= target_hz AND target_hz <= max_hz),
  UNIQUE (patient_id, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_care_plan_per_patient
  ON care_plans(patient_id)
  WHERE status = 'active';

CREATE TABLE IF NOT EXISTS scheduled_sessions (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  patient_id TEXT NOT NULL REFERENCES patients(id),
  care_plan_id TEXT NOT NULL REFERENCES care_plans(id),
  created_by TEXT NOT NULL REFERENCES users(id),
  scheduled_for TEXT NOT NULL,
  target_hz REAL NOT NULL CHECK (target_hz >= 0 AND target_hz <= 230),
  duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
  status TEXT NOT NULL DEFAULT 'scheduled'
    CHECK (status IN ('scheduled', 'ready', 'running', 'completed', 'missed', 'cancelled')),
  reminder_minutes INTEGER NOT NULL DEFAULT 15 CHECK (reminder_minutes >= 0),
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS therapy_sessions (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  patient_id TEXT NOT NULL REFERENCES patients(id),
  device_id TEXT NOT NULL REFERENCES devices(id),
  assignment_id TEXT NOT NULL REFERENCES device_assignments(id),
  schedule_id TEXT REFERENCES scheduled_sessions(id),
  device_sequence INTEGER NOT NULL CHECK (device_sequence >= 0),
  started_at_utc TEXT NOT NULL,
  ended_at_utc TEXT NOT NULL,
  requested_hz REAL NOT NULL CHECK (requested_hz >= 0 AND requested_hz <= 230),
  estimated_hz REAL CHECK (estimated_hz IS NULL OR (estimated_hz >= 0 AND estimated_hz <= 230)),
  measured_hz REAL CHECK (measured_hz IS NULL OR (measured_hz >= 0 AND measured_hz <= 230)),
  pwm_value INTEGER NOT NULL CHECK (pwm_value >= 0 AND pwm_value <= 255),
  duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
  status TEXT NOT NULL CHECK (status IN ('completed', 'interrupted', 'failed')),
  sync_source TEXT NOT NULL CHECK (sync_source IN ('device_wifi', 'mobile_ble_relay')),
  received_at TEXT NOT NULL,
  UNIQUE (device_id, device_sequence)
);

CREATE TABLE IF NOT EXISTS sync_receipts (
  event_id TEXT PRIMARY KEY REFERENCES therapy_sessions(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL REFERENCES devices(id),
  route TEXT NOT NULL CHECK (route IN ('device_wifi', 'mobile_ble_relay')),
  acknowledged_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_events (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id),
  actor_id TEXT REFERENCES users(id),
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  summary TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_patients_doctor ON patients(doctor_id);
CREATE INDEX IF NOT EXISTS idx_assignments_patient ON device_assignments(patient_id, starts_at);
CREATE INDEX IF NOT EXISTS idx_schedules_patient ON scheduled_sessions(patient_id, scheduled_for);
CREATE INDEX IF NOT EXISTS idx_sessions_patient ON therapy_sessions(patient_id, started_at_utc);
CREATE INDEX IF NOT EXISTS idx_sessions_device ON therapy_sessions(device_id, device_sequence);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_events(entity_type, entity_id, created_at);

