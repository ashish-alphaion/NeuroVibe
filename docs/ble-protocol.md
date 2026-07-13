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
| `set_assignment` | `patient_id`, `assignment_id` | Bind the device to an active assignment |
| `set_limits` | `min_hz`, `target_hz`, `max_hz`, `max_duration_seconds` | Apply care-plan limits |
| `start_session` | `target_hz`, `duration_seconds`; optional `schedule_id` | Start an approved session |
| `set_frequency` | `hz` | Adjust an active session if manual control is enabled |
| `stop_session` | optional `reason` | Stop and record an interrupted session |
| `emergency_stop` | none | Immediately stop the motors |
| `get_pending` | none | Begin chunked transfer of queued records |
| `ack_session` | `session_id` | Delete a locally queued record after server acknowledgement |
| `sync_now` | none | Attempt one direct Wi-Fi upload |
| `factory_reset` | confirmation `RESET_NEUROSENSE` | Erase configuration and queued records |

## App relay rule

The app must not acknowledge a transferred event merely because it received the BLE chunks. It acknowledges the event only after:

1. The full JSON record is reconstructed.
2. The record is saved to encrypted phone storage.
3. The Secure API accepts it and returns `acknowledged: true`.

This ensures that temporary Bluetooth transfer success cannot cause clinical-session data loss.
