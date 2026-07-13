import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export const config = {
  port: Number(process.env.PORT || 8080),
  databasePath: process.env.DATABASE_PATH || path.resolve(here, '../../.local/neurovibe.db'),
  corsOrigin: process.env.CORS_ORIGIN || '*',
  demoDoctorEmail: process.env.DEMO_DOCTOR_EMAIL || 'doctor@neurovibe.local',
  demoDoctorPassword: process.env.DEMO_DOCTOR_PASSWORD || 'ChangeMe!123',
  tokenLifetimeHours: Number(process.env.TOKEN_LIFETIME_HOURS || 12),
};

