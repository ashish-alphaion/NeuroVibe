import assert from 'node:assert/strict';
import http from 'node:http';
import { after, before, test } from 'node:test';
import { createApp } from '../src/app.js';
import { openDatabase, seedLocalData } from '../src/database.js';

let db;
let server;
let baseUrl;
let token;
let patient;
let assignment;
let carePlan;
let scheduledSession;
let deviceToken;

const testConfig = {
  corsOrigin: '*',
  tokenLifetimeHours: 1,
};

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const authToken = options.authToken === undefined ? token : options.authToken;
  if (authToken) headers.Authorization = `Bearer ${authToken}`;
  delete options.authToken;
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  const body = await response.json();
  return { response, body };
}

before(async () => {
  db = openDatabase(':memory:');
  seedLocalData(db, { doctorEmail: 'doctor@test.local', doctorPassword: 'TestPassword!123' });
  server = http.createServer(createApp({ db, config: testConfig }));
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve) => server.close(resolve));
  db.close();
});

test('health endpoint works', async () => {
  const { response, body } = await request('/health');
  assert.equal(response.status, 200);
  assert.equal(body.status, 'ok');
});

test('doctor can log in', async () => {
  const { response, body } = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email: 'doctor@test.local', password: 'TestPassword!123' }),
  });
  assert.equal(response.status, 200);
  assert.equal(body.user.role, 'doctor');
  token = body.token;
});

test('doctor can create a patient', async () => {
  const { response, body } = await request('/api/patients', {
    method: 'POST',
    body: JSON.stringify({
      patient_code: 'NV-P-0001',
      full_name: 'Alex Smith',
      date_of_birth: '1990-04-14',
      gender: 'male',
      program_status: 'active',
      consent_status: 'accepted',
    }),
  });
  assert.equal(response.status, 201);
  patient = body.patient;
});

test('doctor can assign an available device', async () => {
  const { response, body } = await request('/api/device-assignments', {
    method: 'POST',
    body: JSON.stringify({ patient_id: patient.id, device_id: 'DEV-NEUROSENSE-A7F3' }),
  });
  assert.equal(response.status, 201);
  assignment = body.assignment;
});

test('doctor can create a dedicated device credential', async () => {
  const { response, body } = await request('/api/devices/DEV-NEUROSENSE-A7F3/credential', {
    method: 'POST',
    body: '{}',
  });
  assert.equal(response.status, 201);
  assert.equal(body.device_id, 'DEV-NEUROSENSE-A7F3');
  assert.ok(body.token.length > 20);
  deviceToken = body.token;
});

test('invalid care plan above 230 Hz is rejected', async () => {
  const { response, body } = await request('/api/care-plans', {
    method: 'POST',
    body: JSON.stringify({
      patient_id: patient.id,
      name: 'Invalid Plan',
      min_hz: 100,
      target_hz: 150,
      max_hz: 231,
      duration_seconds: 600,
      max_duration_seconds: 900,
    }),
  });
  assert.equal(response.status, 400);
  assert.equal(body.error.code, 'invalid_frequency_range');
});

test('doctor can create a valid care plan', async () => {
  const { response, body } = await request('/api/care-plans', {
    method: 'POST',
    body: JSON.stringify({
      patient_id: patient.id,
      name: 'Morning Therapy',
      min_hz: 100,
      target_hz: 150,
      max_hz: 180,
      duration_seconds: 600,
      max_duration_seconds: 900,
      manual_control_allowed: true,
    }),
  });
  assert.equal(response.status, 201);
  carePlan = body.care_plan;
});

test('doctor can schedule a session within the care plan', async () => {
  const { response, body } = await request('/api/scheduled-sessions', {
    method: 'POST',
    body: JSON.stringify({
      patient_id: patient.id,
      care_plan_id: carePlan.id,
      scheduled_for: '2026-07-14T09:00:00.000Z',
      target_hz: 150,
      duration_seconds: 600,
    }),
  });
  assert.equal(response.status, 201);
  scheduledSession = body.scheduled_session;
});

test('session ingestion is idempotent', async () => {
  const event = {
    session_id: 'SES-TEST-0001',
    patient_id: patient.id,
    device_id: 'DEV-NEUROSENSE-A7F3',
    assignment_id: assignment.id,
    schedule_id: scheduledSession.id,
    device_sequence: 1,
    started_at_utc: '2026-07-14T09:00:00.000Z',
    ended_at_utc: '2026-07-14T09:10:00.000Z',
    requested_hz: 150,
    estimated_hz: 146,
    measured_hz: null,
    pwm_value: 168,
    duration_seconds: 600,
    status: 'completed',
    sync_source: 'mobile_ble_relay',
  };

  const first = await request('/api/therapy-sessions', { method: 'POST', body: JSON.stringify(event), authToken: deviceToken });
  assert.equal(first.response.status, 201);
  assert.equal(first.body.status, 'accepted');

  const second = await request('/api/therapy-sessions', { method: 'POST', body: JSON.stringify(event) });
  assert.equal(second.response.status, 200);
  assert.equal(second.body.status, 'already_accepted');

  const count = db.prepare('SELECT COUNT(*) count FROM therapy_sessions WHERE id = ?').get(event.session_id).count;
  assert.equal(count, 1);
});

test('patient session history and dashboard summary are available', async () => {
  const history = await request(`/api/patients/${patient.id}/therapy-sessions`);
  assert.equal(history.response.status, 200);
  assert.equal(history.body.items.length, 1);

  const dashboard = await request('/api/dashboard/summary');
  assert.equal(dashboard.response.status, 200);
  assert.equal(dashboard.body.patients, 1);
  assert.equal(dashboard.body.sessions, 1);
});
