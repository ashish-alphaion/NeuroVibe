import http from 'node:http';
import { config } from './config.js';
import { createApp } from './app.js';
import { openDatabase, seedLocalData } from './database.js';

const db = openDatabase(config.databasePath);
seedLocalData(db, {
  doctorEmail: config.demoDoctorEmail,
  doctorPassword: config.demoDoctorPassword,
});

const server = http.createServer(createApp({ db, config }));

server.listen(config.port, () => {
  console.log(`NeuroVibe local API: http://localhost:${config.port}`);
  console.log(`Health check: http://localhost:${config.port}/health`);
  console.log(`Local doctor: ${config.demoDoctorEmail}`);
});

function shutdown() {
  server.close(() => {
    db.close();
    process.exit(0);
  });
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

