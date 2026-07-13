import { hashToken, issueToken, verifyPassword } from './security.js';
import { HttpError } from './http.js';
import { now } from './database.js';

export function login(db, email, password, lifetimeHours) {
  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(String(email).trim().toLowerCase());
  if (!user || user.status !== 'active' || !verifyPassword(password, user.password_hash)) {
    throw new HttpError(401, 'invalid_credentials', 'Email or password is incorrect.');
  }
  const token = issueToken();
  const createdAt = now();
  const expiresAt = new Date(Date.now() + lifetimeHours * 60 * 60 * 1000).toISOString();
  db.prepare('INSERT INTO auth_sessions (token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)')
    .run(hashToken(token), user.id, expiresAt, createdAt);
  return { token, expires_at: expiresAt, user: publicUser(user) };
}

export function authenticate(db, request) {
  const header = request.headers.authorization || '';
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) throw new HttpError(401, 'authentication_required', 'A bearer token is required.');
  const tokenHash = hashToken(match[1]);
  const row = db.prepare(`
    SELECT u.* FROM auth_sessions s
    JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ? AND s.expires_at > ? AND u.status = 'active'
  `).get(tokenHash, now());
  if (row) return { ...publicUser(row), principal_type: 'user', rawToken: match[1] };

  const device = db.prepare(`
    SELECT d.id, d.organization_id, d.display_name, d.lifecycle_status
    FROM device_credentials c
    JOIN devices d ON d.id = c.device_id
    WHERE c.token_hash = ? AND c.status = 'active' AND d.lifecycle_status NOT IN ('retired', 'lost')
  `).get(tokenHash);
  if (device) {
    return {
      id: device.id,
      organization_id: device.organization_id,
      name: device.display_name,
      role: 'device',
      status: device.lifecycle_status,
      principal_type: 'device',
      rawToken: match[1],
    };
  }
  throw new HttpError(401, 'invalid_token', 'The session or device credential is invalid or expired.');
}

export function logout(db, token) {
  db.prepare('DELETE FROM auth_sessions WHERE token_hash = ?').run(hashToken(token));
}

export function requireDoctor(user) {
  if (!['doctor', 'admin'].includes(user.role)) throw new HttpError(403, 'forbidden', 'Doctor or admin access is required.');
}

export function createDeviceCredential(db, deviceId, createdBy) {
  const token = issueToken();
  const timestamp = now();
  db.prepare(`
    INSERT INTO device_credentials (device_id, token_hash, status, created_by, created_at, rotated_at)
    VALUES (?, ?, 'active', ?, ?, ?)
    ON CONFLICT(device_id) DO UPDATE SET
      token_hash = excluded.token_hash,
      status = 'active',
      created_by = excluded.created_by,
      rotated_at = excluded.rotated_at
  `).run(deviceId, hashToken(token), createdBy, timestamp, timestamp);
  return token;
}

function publicUser(user) {
  return {
    id: user.id,
    organization_id: user.organization_id,
    email: user.email,
    name: user.name,
    role: user.role,
    status: user.status,
  };
}
