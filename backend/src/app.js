import { audit, makeId, now } from './database.js';
import { authenticate, createDeviceCredential, login, logout, requireDoctor } from './auth.js';
import { asNumber, HttpError, readJson, requireFields, sendJson } from './http.js';

const patientColumns = `id, organization_id, doctor_id, patient_code, full_name, date_of_birth,
  gender, phone, email, program_status, consent_status, created_at, updated_at`;

export function createApp({ db, config }) {
  return async function handler(request, response) {
    const headers = {
      'Access-Control-Allow-Origin': config.corsOrigin,
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Methods': 'GET, POST, PATCH, OPTIONS',
    };
    if (request.method === 'OPTIONS') return sendJson(response, 204, {}, headers);

    try {
      const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
      const path = url.pathname;

      if (request.method === 'GET' && path === '/health') {
        return sendJson(response, 200, { status: 'ok', service: 'neurovibe-local-api', time: now() }, headers);
      }

      if (request.method === 'POST' && path === '/api/auth/login') {
        const body = await readJson(request);
        requireFields(body, ['email', 'password']);
        return sendJson(response, 200, login(db, body.email, body.password, config.tokenLifetimeHours), headers);
      }

      const user = authenticate(db, request);

      if (request.method === 'GET' && path === '/api/auth/me') {
        const { rawToken, ...safeUser } = user;
        return sendJson(response, 200, { user: safeUser }, headers);
      }

      if (request.method === 'POST' && path === '/api/auth/logout') {
        logout(db, user.rawToken);
        return sendJson(response, 200, { status: 'signed_out' }, headers);
      }

      const isDeviceSessionUpload = request.method === 'POST' && path === '/api/therapy-sessions' && user.role === 'device';
      if (!isDeviceSessionUpload) requireDoctor(user);

      if (request.method === 'GET' && path === '/api/dashboard/summary') {
        const patientCount = db.prepare('SELECT COUNT(*) count FROM patients WHERE organization_id = ?').get(user.organization_id).count;
        const deviceCounts = db.prepare(`
          SELECT lifecycle_status status, COUNT(*) count FROM devices
          WHERE organization_id = ? GROUP BY lifecycle_status
        `).all(user.organization_id);
        const sessionCount = db.prepare('SELECT COUNT(*) count FROM therapy_sessions WHERE organization_id = ?').get(user.organization_id).count;
        return sendJson(response, 200, { patients: patientCount, sessions: sessionCount, devices: deviceCounts }, headers);
      }

      if (request.method === 'GET' && path === '/api/patients') {
        const rows = db.prepare(`SELECT ${patientColumns} FROM patients WHERE organization_id = ? ORDER BY created_at DESC`)
          .all(user.organization_id);
        return sendJson(response, 200, { items: rows }, headers);
      }

      if (request.method === 'POST' && path === '/api/patients') {
        const body = await readJson(request);
        requireFields(body, ['patient_code', 'full_name']);
        const timestamp = now();
        const id = makeId('PAT');
        db.prepare(`
          INSERT INTO patients (${patientColumns})
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          id, user.organization_id, user.id, String(body.patient_code).trim(), String(body.full_name).trim(),
          body.date_of_birth || null, body.gender || null, body.phone || null, body.email || null,
          body.program_status || 'invited', body.consent_status || 'pending', timestamp, timestamp,
        );
        audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'patient.created', entityType: 'patient', entityId: id, summary: `Created patient ${body.patient_code}` });
        const patient = db.prepare(`SELECT ${patientColumns} FROM patients WHERE id = ?`).get(id);
        return sendJson(response, 201, { patient }, headers);
      }

      const patientMatch = /^\/api\/patients\/([^/]+)$/.exec(path);
      if (request.method === 'GET' && patientMatch) {
        const patient = db.prepare(`SELECT ${patientColumns} FROM patients WHERE id = ? AND organization_id = ?`)
          .get(decodeURIComponent(patientMatch[1]), user.organization_id);
        if (!patient) throw new HttpError(404, 'not_found', 'Patient was not found.');
        return sendJson(response, 200, { patient }, headers);
      }

      if (request.method === 'GET' && path === '/api/devices') {
        const rows = db.prepare('SELECT * FROM devices WHERE organization_id = ? ORDER BY created_at DESC').all(user.organization_id);
        return sendJson(response, 200, { items: rows }, headers);
      }

      if (request.method === 'POST' && path === '/api/devices') {
        const body = await readJson(request);
        requireFields(body, ['display_name']);
        const timestamp = now();
        const id = body.id || makeId('DEV');
        db.prepare(`
          INSERT INTO devices (id, organization_id, display_name, serial_number, lifecycle_status, firmware_version, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).run(id, user.organization_id, body.display_name, body.serial_number || null, body.lifecycle_status || 'available', body.firmware_version || null, timestamp, timestamp);
        audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'device.registered', entityType: 'device', entityId: id, summary: `Registered ${body.display_name}` });
        return sendJson(response, 201, { device: db.prepare('SELECT * FROM devices WHERE id = ?').get(id) }, headers);
      }

      const credentialMatch = /^\/api\/devices\/([^/]+)\/credential$/.exec(path);
      if (request.method === 'POST' && credentialMatch) {
        requireDoctor(user);
        const deviceId = decodeURIComponent(credentialMatch[1]);
        const device = db.prepare('SELECT * FROM devices WHERE id = ? AND organization_id = ?').get(deviceId, user.organization_id);
        if (!device) throw new HttpError(404, 'not_found', 'Device was not found.');
        const deviceToken = createDeviceCredential(db, device.id, user.id);
        audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'device.credential_rotated', entityType: 'device', entityId: device.id, summary: `Created a new credential for ${device.display_name}` });
        return sendJson(response, 201, {
          device_id: device.id,
          token: deviceToken,
          warning: 'This token is shown once. Provision it to the assigned NeuroSense device securely.',
        }, headers);
      }

      if (request.method === 'POST' && path === '/api/device-assignments') {
        const body = await readJson(request);
        requireFields(body, ['patient_id', 'device_id']);
        const patient = db.prepare('SELECT * FROM patients WHERE id = ? AND organization_id = ?').get(body.patient_id, user.organization_id);
        const device = db.prepare('SELECT * FROM devices WHERE id = ? AND organization_id = ?').get(body.device_id, user.organization_id);
        if (!patient) throw new HttpError(404, 'not_found', 'Patient was not found.');
        if (!device) throw new HttpError(404, 'not_found', 'Device was not found.');
        if (!['available', 'claimed', 'sanitized'].includes(device.lifecycle_status)) {
          throw new HttpError(409, 'device_not_available', 'The device is not available for assignment.');
        }
        const id = makeId('ASN');
        const timestamp = now();
        db.exec('BEGIN');
        try {
          db.prepare(`
            INSERT INTO device_assignments (id, organization_id, patient_id, device_id, assigned_by, status, starts_at, created_at)
            VALUES (?, ?, ?, ?, ?, 'active', ?, ?)
          `).run(id, user.organization_id, patient.id, device.id, user.id, body.starts_at || timestamp, timestamp);
          db.prepare("UPDATE devices SET lifecycle_status = 'assigned', updated_at = ? WHERE id = ?").run(timestamp, device.id);
          db.prepare("UPDATE patients SET program_status = CASE WHEN program_status = 'invited' THEN 'enrolled' ELSE program_status END, updated_at = ? WHERE id = ?")
            .run(timestamp, patient.id);
          audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'device.assigned', entityType: 'device_assignment', entityId: id, summary: `Assigned ${device.display_name} to ${patient.patient_code}` });
          db.exec('COMMIT');
        } catch (error) {
          db.exec('ROLLBACK');
          if (String(error.message).includes('UNIQUE constraint failed')) {
            throw new HttpError(409, 'active_assignment_exists', 'The patient or device already has an active assignment.');
          }
          throw error;
        }
        return sendJson(response, 201, { assignment: db.prepare('SELECT * FROM device_assignments WHERE id = ?').get(id) }, headers);
      }

      if (request.method === 'POST' && path === '/api/care-plans') {
        const body = await readJson(request);
        requireFields(body, ['patient_id', 'name', 'min_hz', 'target_hz', 'max_hz', 'duration_seconds', 'max_duration_seconds']);
        const minHz = asNumber(body.min_hz, 'min_hz');
        const targetHz = asNumber(body.target_hz, 'target_hz');
        const maxHz = asNumber(body.max_hz, 'max_hz');
        if (!(0 <= minHz && minHz <= targetHz && targetHz <= maxHz && maxHz <= 230)) {
          throw new HttpError(400, 'invalid_frequency_range', 'Frequency must satisfy 0 <= min_hz <= target_hz <= max_hz <= 230.');
        }
        const patient = db.prepare('SELECT * FROM patients WHERE id = ? AND organization_id = ?').get(body.patient_id, user.organization_id);
        if (!patient) throw new HttpError(404, 'not_found', 'Patient was not found.');
        const currentVersion = db.prepare('SELECT COALESCE(MAX(version), 0) version FROM care_plans WHERE patient_id = ?').get(patient.id).version;
        const id = makeId('PLAN');
        const timestamp = now();
        db.exec('BEGIN');
        try {
          if ((body.status || 'active') === 'active') {
            db.prepare("UPDATE care_plans SET status = 'replaced', effective_to = ? WHERE patient_id = ? AND status = 'active'")
              .run(timestamp, patient.id);
          }
          db.prepare(`
            INSERT INTO care_plans
              (id, organization_id, patient_id, created_by, version, name, status, min_hz, target_hz, max_hz,
               duration_seconds, max_duration_seconds, manual_control_allowed, effective_from, effective_to, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `).run(
            id, user.organization_id, patient.id, user.id, currentVersion + 1, body.name, body.status || 'active',
            minHz, targetHz, maxHz, Number(body.duration_seconds), Number(body.max_duration_seconds),
            body.manual_control_allowed ? 1 : 0, body.effective_from || timestamp, body.effective_to || null, timestamp,
          );
          audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'care_plan.created', entityType: 'care_plan', entityId: id, summary: `Created care plan version ${currentVersion + 1}` });
          db.exec('COMMIT');
        } catch (error) {
          db.exec('ROLLBACK');
          throw error;
        }
        return sendJson(response, 201, { care_plan: db.prepare('SELECT * FROM care_plans WHERE id = ?').get(id) }, headers);
      }

      if (request.method === 'POST' && path === '/api/scheduled-sessions') {
        const body = await readJson(request);
        requireFields(body, ['patient_id', 'care_plan_id', 'scheduled_for', 'target_hz', 'duration_seconds']);
        const plan = db.prepare(`SELECT * FROM care_plans WHERE id = ? AND patient_id = ? AND organization_id = ? AND status = 'active'`)
          .get(body.care_plan_id, body.patient_id, user.organization_id);
        if (!plan) throw new HttpError(409, 'active_care_plan_required', 'An active care plan is required.');
        const targetHz = asNumber(body.target_hz, 'target_hz');
        const duration = Number(body.duration_seconds);
        if (targetHz < plan.min_hz || targetHz > plan.max_hz) throw new HttpError(400, 'outside_care_plan', 'Target frequency is outside the active care plan.');
        if (duration <= 0 || duration > plan.max_duration_seconds) throw new HttpError(400, 'outside_care_plan', 'Duration is outside the active care plan.');
        const id = makeId('SCH');
        const timestamp = now();
        db.prepare(`
          INSERT INTO scheduled_sessions
            (id, organization_id, patient_id, care_plan_id, created_by, scheduled_for, target_hz, duration_seconds, status, reminder_minutes, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'scheduled', ?, ?)
        `).run(id, user.organization_id, body.patient_id, plan.id, user.id, body.scheduled_for, targetHz, duration, Number(body.reminder_minutes ?? 15), timestamp);
        audit(db, { organizationId: user.organization_id, actorId: user.id, action: 'session.scheduled', entityType: 'scheduled_session', entityId: id, summary: `Scheduled ${targetHz} Hz session` });
        return sendJson(response, 201, { scheduled_session: db.prepare('SELECT * FROM scheduled_sessions WHERE id = ?').get(id) }, headers);
      }

      if (request.method === 'POST' && path === '/api/therapy-sessions') {
        const body = await readJson(request);
        requireFields(body, [
          'session_id', 'patient_id', 'device_id', 'assignment_id', 'device_sequence', 'started_at_utc',
          'ended_at_utc', 'requested_hz', 'pwm_value', 'duration_seconds', 'status', 'sync_source',
        ]);
        if (user.role === 'device' && user.id !== body.device_id) {
          throw new HttpError(403, 'device_identity_mismatch', 'A device can upload only its own session records.');
        }
        const existing = db.prepare('SELECT * FROM therapy_sessions WHERE id = ? AND organization_id = ?')
          .get(body.session_id, user.organization_id);
        if (existing) {
          return sendJson(response, 200, { status: 'already_accepted', acknowledged: true, session: existing }, headers);
        }
        const assignment = db.prepare(`
          SELECT * FROM device_assignments WHERE id = ? AND patient_id = ? AND device_id = ? AND organization_id = ?
        `).get(body.assignment_id, body.patient_id, body.device_id, user.organization_id);
        if (!assignment) throw new HttpError(409, 'invalid_assignment', 'The device assignment does not match this session.');
        if (body.requested_hz < 0 || body.requested_hz > 230) throw new HttpError(400, 'invalid_frequency', 'requested_hz must be between 0 and 230.');
        const receivedAt = now();
        db.exec('BEGIN');
        try {
          db.prepare(`
            INSERT INTO therapy_sessions
              (id, organization_id, patient_id, device_id, assignment_id, schedule_id, device_sequence,
               started_at_utc, ended_at_utc, requested_hz, estimated_hz, measured_hz, pwm_value,
               duration_seconds, status, sync_source, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `).run(
            body.session_id, user.organization_id, body.patient_id, body.device_id, body.assignment_id,
            body.schedule_id || null, Number(body.device_sequence), body.started_at_utc, body.ended_at_utc,
            Number(body.requested_hz), body.estimated_hz ?? null, body.measured_hz ?? null, Number(body.pwm_value),
            Number(body.duration_seconds), body.status, body.sync_source, receivedAt,
          );
          db.prepare('INSERT INTO sync_receipts (event_id, device_id, route, acknowledged_at) VALUES (?, ?, ?, ?)')
            .run(body.session_id, body.device_id, body.sync_source, receivedAt);
          if (body.schedule_id) db.prepare("UPDATE scheduled_sessions SET status = 'completed' WHERE id = ?").run(body.schedule_id);
          audit(db, { organizationId: user.organization_id, actorId: user.role === 'device' ? null : user.id, action: 'therapy_session.accepted', entityType: 'therapy_session', entityId: body.session_id, summary: `Accepted via ${body.sync_source}` });
          db.exec('COMMIT');
        } catch (error) {
          db.exec('ROLLBACK');
          if (String(error.message).includes('UNIQUE constraint failed')) {
            throw new HttpError(409, 'sequence_conflict', 'This device sequence number has already been used by another session.');
          }
          throw error;
        }
        return sendJson(response, 201, {
          status: 'accepted',
          acknowledged: true,
          receipt: db.prepare('SELECT * FROM sync_receipts WHERE event_id = ?').get(body.session_id),
        }, headers);
      }

      const sessionsMatch = /^\/api\/patients\/([^/]+)\/therapy-sessions$/.exec(path);
      if (request.method === 'GET' && sessionsMatch) {
        const rows = db.prepare(`
          SELECT * FROM therapy_sessions WHERE patient_id = ? AND organization_id = ? ORDER BY started_at_utc DESC
        `).all(decodeURIComponent(sessionsMatch[1]), user.organization_id);
        return sendJson(response, 200, { items: rows }, headers);
      }

      throw new HttpError(404, 'not_found', 'Route was not found.');
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 500;
      const payload = error instanceof HttpError
        ? { error: { code: error.code, message: error.message, details: error.details } }
        : { error: { code: 'internal_error', message: 'An unexpected server error occurred.' } };
      if (!(error instanceof HttpError)) console.error(error);
      return sendJson(response, status, payload, headers);
    }
  };
}
