# NeuroSense Implementation Specification

## Product boundary

NeuroSense is a prototype vibration device using an ESP32-C3, one DRV8833
dual-channel motor driver and two 3 V ERM coin motors. NeuroVibe is the patient
Android application. The doctor uses the NeuroVibe web portal.

This repository is a prototype and is not cleared for clinical use. Exact
physical vibration frequency requires sensor feedback; PWM-only output is
recorded as estimated frequency.

## Non-negotiable invariants

1. A physical device has an immutable `hardware_id`.
2. An inventory `device_id` identifies the unit, never the patient.
3. A patient/device relationship exists only through `device_assignments`.
4. A patient and a device can each have at most one active assignment.
5. Replacement closes the old assignment and creates a new one atomically.
6. Historical usage remains linked to the device and assignment that produced it.
7. A replaced device cannot start new motor sessions.
8. A replaced device may synchronize records created during its former assignment.
9. The device stores records until the server acknowledges them.
10. NeuroSense has no Wi-Fi workflow. Bluetooth is its only data transport.

## System components

- Supabase PostgreSQL: identities, assignments, devices, usage and audit history.
- Netlify Functions: authenticated provisioning, assignment checks and ingestion.
- Doctor portal: inventory, assignment, replacement, usage and device status.
- NeuroVibe Android app: patient authentication, BLE control and data relay.
- NeuroSense firmware: identity, lease enforcement, motor safety and offline queue.

## Assignment lifecycle

`AVAILABLE -> ASSIGNED -> ACTIVE -> CLOSED`

The associated device lifecycle may continue as:

`FAULTY`, `RETURN_PENDING`, `UNDER_REPAIR`, `SANITIZED`, `RETIRED` or `LOST`.

Every active assignment has a renewable lease. The signed-in app renews the
lease and transfers it to the device over BLE before expiry.

## First-time patient flow

1. Doctor registers and assigns an available device.
2. Patient signs in; the app downloads the active assignment.
3. Patient connects the matching NeuroSense over BLE.
4. App verifies the immutable hardware identity or binds it on first secure setup.
5. Authenticated app obtains an assignment lease from the API.
6. App sends identity, assignment lease and care limits over BLE.
7. App sends `activate_assignment`.
8. Device is ready after the app installs the assignment lease over Bluetooth.

## Usage and synchronization

The ESP32 records assignment, device, timestamps, requested/estimated frequency,
PWM, duration, outcome and sync route. It first writes the record to LittleFS.

`NeuroSense -> BLE -> NeuroVibe -> HTTPS API -> PostgreSQL`

The app acknowledges the BLE record only after the API accepts it.

## Replacement flow

The doctor selects **Replace device**, a new available unit, old-device
condition and replacement reason. One database transaction:

1. closes the old assignment;
2. expires its lease;
3. revokes any historical device credential;
4. changes the old device lifecycle state;
5. creates the new assignment;
6. marks the new device assigned;
7. creates a patient notification;
8. records an audit event.

When NeuroVibe returns to the foreground it refreshes the assignment. It
disconnects the old unit and asks for the replacement. Connecting an old unit
offers stored-record recovery only; motor controls stay locked.

## Acceptance scenarios

### New assignment

- Doctor assigns available NS01 to Patient A.
- Patient A can provision NS01.
- Patient B cannot provision NS01.
- NS01 sessions appear under Patient A and assignment NS01/A.

### Replacement

- Doctor replaces NS01 with NS02.
- NS01 assignment becomes closed and NS02 assignment becomes active.
- Patient history is unchanged.
- Patient app requests NS02.
- NS01 cannot start a new session.
- A pre-replacement queued NS01 record can still synchronize.

### Offline operation

- Device can complete and retain a session with no internet.
- App can relay the record later.
- Records remain until acknowledged.
- Expired assignment lease blocks new motor starts.

## Deployment order

1. Apply Supabase migrations.
2. Deploy Netlify functions.
3. Deploy doctor portal.
4. Install the matching Android APK.
5. Flash the matching BLE-only firmware.
6. Execute the acceptance scenarios using fabricated patient data.
