# NeuroSense BLE Protocol v0.1

## Transport

The app writes one JSON command at a time and subscribes to the response characteristic.

```text
Service: 7b1e0001-7f34-4fd8-a912-6c38ef4a5201
Command: 7b1e0002-7f34-4fd8-a912-6c38ef4a5201
Response: 7b1e0003-7f34-4fd8-a912-6c38ef4a5201
```

Maximum command size is 512 bytes. Provisioning values are separated into multiple commands to remain below BLE packet limits.

Short response documents are sent as a single JSON notification. Longer response documents are framed as:

```text
#JSONBEGIN:<json_length>
<one or more raw JSON chunks>
#JSONEND
```

The app concatenates the raw chunks and parses JSON only after `#JSONEND`.

## Standard responses

Success:

```json
{"type":"ok","command":"set_limits","data":{}}
```

Error:

```json
{
  "type":"error",
  "command":"set_frequency",
  "code":"frequency_not_allowed",
  "message":"Frequency is outside the configured care plan."
}
```

## Commands

| Command | Required fields | Purpose |
|---|---|---|
| `get_info` | none | Read device, assignment, limits and sync state |
| `get_status` | none | Same status payload used during normal operation |
| `set_wifi` | `ssid`, `password` | Save Wi-Fi and attempt connection |
| `set_server` | `api_base_url`, `api_token` | Save local API connection settings |
| `set_assignment` | `patient_id`, `assignment_id`, `assignment_valid_until_epoch`, `server_time_epoch` | Bind the device to a renewable active assignment lease |
| `set_limits` | `min_hz`, `target_hz`, `max_hz`, `max_duration_seconds` | Apply care-plan limits |
| `activate_assignment` | none | Activate the fully provisioned assignment for BLE/offline operation |
| `start_session` | `target_hz`, `duration_seconds`; optional `schedule_id` | Start an approved session |
| `set_frequency` | `hz` | Adjust an active session if manual control is enabled |
| `stop_session` | optional `reason` | Stop and record an interrupted session |
| `emergency_stop` | none | Immediately stop the motors |
| `get_pending` | none | Begin chunked transfer of queued records |
| `ack_session` | `session_id` | Delete a locally queued record after server acknowledgement |
| `sync_now` | none | Attempt one direct Wi-Fi upload |
| `factory_reset` | confirmation `RESET_NEUROSENSE` | Erase configuration and queued records |

## First-time connection and assignment

The patient app must complete the setup in this order:

1. **Phase 1 — Bluetooth:** scan for the `NeuroSense-*` advertisement, connect,
   subscribe to notifications, and send `get_status`.
2. Reject a device whose non-empty `device_id` differs from the device assigned
   to the signed-in patient. A factory-empty device may continue.
3. **Phase 2 — Secure assignment:** the authenticated patient app requests a
   device-specific credential and active assignment lease from the API.
4. Send identity, server credential, assignment lease and care limits as
   separate BLE commands.
5. Send `activate_assignment` only after every required command succeeds.
6. The device is now usable through Bluetooth and can relay records through the
   app. Direct device Wi-Fi is optional.
7. If the patient enables direct Wi-Fi, send `set_wifi` and wait for the
   asynchronous `wifi_result`; `ok/set_wifi` only confirms the attempt was
   queued.

Successful Wi-Fi result:

```json
{
  "type": "wifi_result",
  "phase": 2,
  "connected": true,
  "message": "Wi-Fi connected",
  "ssid": "ClinicWiFi"
}
```

Failed Wi-Fi result:

```json
{
  "type": "wifi_result",
  "phase": 2,
  "connected": false,
  "message": "Wi-Fi unable to connect",
  "disconnect_reason_name": "authentication_failed_check_password"
}
```

The app must never save the Wi-Fi password in phone preferences or logs.

## Device-independent assignment lease

`hardware_id` is generated from the ESP32 eFuse identity and never changes.
`device_id` identifies the inventory unit. Patient and assignment values are
replaceable configuration.

```json
{
  "type": "set_assignment",
  "patient_id": "PATIENT_UUID",
  "assignment_id": "ASSIGNMENT_UUID",
  "assignment_valid_until_epoch": 1784890000,
  "server_time_epoch": 1784285200
}
```

The firmware rejects new motor sessions after the assignment lease expires.
An online device rechecks the server every 15 minutes. Replacing a device
changes the old credential to sync-only, allowing records created before the
replacement to upload while preventing assignment verification and motor use.

## App relay rule

The app must not acknowledge a transferred event merely because it received the BLE chunks. It acknowledges the event only after:

1. The full JSON record is reconstructed.
2. The record is saved to encrypted phone storage.
3. The Secure API accepts it and returns `acknowledged: true`.

This ensures that temporary Bluetooth transfer success cannot cause clinical-session data loss.
