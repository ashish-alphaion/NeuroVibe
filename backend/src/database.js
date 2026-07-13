import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import { fileURLToPath } from 'node:url';
import { hashPassword } from './security.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const migrationsPath = path.resolve(here, '../database/migrations');

export function makeId(prefix) {
  return `${prefix}-${randomUUID()}`;
}

export function now() {
  return new Date().toISOString();
}

export function openDatabase(filename) {
  if (filename !== ':memory:') fs.mkdirSync(path.dirname(filename), { recursive: true });
  const db = new DatabaseSync(filename);
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec('PRAGMA journal_mode = WAL;');
  const migrations = fs.readdirSync(migrationsPath)
    .filter((name) => name.endsWith('.sql'))
    .sort();
  for (const migration of migrations) {
    db.exec(fs.readFileSync(path.join(migrationsPath, migration), 'utf8'));
  }
  return db;
}

export function audit(db, { organizationId, actorId, action, entityType, entityId, summary }) {
  db.prepare(`
    INSERT INTO audit_events (id, organization_id, actor_id, action, entity_type, entity_id, summary, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(makeId('AUD'), organizationId, actorId || null, action, entityType, entityId, summary, now());
}

export function seedLocalData(db, { doctorEmail, doctorPassword }) {
  const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(doctorEmail);
  if (existing) return existing.id;

  const timestamp = now();
  const organizationId = 'ORG-NEUROVIBE-LOCAL';
  const doctorId = 'USR-DOCTOR-LOCAL';

  db.exec('BEGIN');
  try {
    db.prepare('INSERT OR IGNORE INTO organizations (id, name, created_at) VALUES (?, ?, ?)')
      .run(organizationId, 'NeuroVibe Local Research Center', timestamp);
    db.prepare(`
      INSERT INTO users (id, organization_id, email, name, role, status, password_hash, created_at, updated_at)
      VALUES (?, ?, ?, ?, 'doctor', 'active', ?, ?, ?)
    `).run(doctorId, organizationId, doctorEmail, 'Dr. Maya Rao', hashPassword(doctorPassword), timestamp, timestamp);

    const addDevice = db.prepare(`
      INSERT INTO devices (id, organization_id, display_name, serial_number, lifecycle_status, firmware_version, created_at, updated_at)
      VALUES (?, ?, ?, ?, 'available', '0.1.0', ?, ?)
    `);
    addDevice.run('DEV-NEUROSENSE-A7F3', organizationId, 'NeuroSense-A7F3', 'NS-A7F3', timestamp, timestamp);
    addDevice.run('DEV-NEUROSENSE-D22F', organizationId, 'NeuroSense-D22F', 'NS-D22F', timestamp, timestamp);
    db.exec('COMMIT');
    return doctorId;
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}
