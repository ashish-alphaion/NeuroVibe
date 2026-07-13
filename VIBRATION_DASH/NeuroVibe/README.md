# NeuroSense ESP32-C3 Firmware

This firmware turns the original motor test sketch into the local NeuroSense MVP device service.

## Hardware

- ESP32-C3 Super Mini
- DRV8833 motor driver
- Two ERM coin vibration motors
- Motor 1 pins: GPIO 2 and GPIO 3
- Motor 2 pins: GPIO 4 and GPIO 5

## Arduino setup

Install:

1. Espressif ESP32 board package 3.x
2. ArduinoJson 7.x

Select:

```text
Board: ESP32C3 Dev Module
USB CDC On Boot: Enabled
Upload Speed: 921600 or a stable lower value
Partition Scheme: a scheme containing a SPIFFS/LittleFS data partition
Serial Monitor: 115200 baud
```

The BLE implementation follows the Arduino-ESP32 BLE server pattern documented by Espressif. ArduinoJson 7 is used for command parsing and session serialization.

## BLE service

```text
Service UUID: 7b1e0001-7f34-4fd8-a912-6c38ef4a5201
Command/write: 7b1e0002-7f34-4fd8-a912-6c38ef4a5201
Response/read/notify: 7b1e0003-7f34-4fd8-a912-6c38ef4a5201
Requested MTU: 247
```

The device advertises as `NeuroSense-XXXXXX`, where the suffix comes from the ESP32 eFuse MAC.

## Provisioning sequence

The app should subscribe to the response characteristic, then write these commands separately.

Short responses arrive as one JSON notification. A JSON response longer than one BLE payload arrives as:

```text
#JSONBEGIN:<json_length>
<one or more raw JSON chunks>
#JSONEND
```

The app must concatenate the chunks before parsing the JSON.

### Read device information

```json
{"type":"get_info"}
```

### Configure Wi-Fi

```json
{"type":"set_wifi","ssid":"ClinicWiFi","password":"wifi-password"}
```

### Configure the NeuroVibe API

```json
{"type":"set_server","api_base_url":"https://neurovibeapi.netlify.app","api_token":"DEVICE_BEARER_TOKEN"}
```

The Netlify base URL is compiled as the default, but the app must still provision the device-specific token. Plain HTTP is accepted only for `10.x.x.x` and `192.168.x.x` private-LAN development addresses. Never use `localhost`, because it means the ESP32 itself.

Create the unique device credential from the local backend using `POST /api/devices/{device_id}/credential`, then provision the returned one-time token through this command. Do not provision a doctor login token to the device.

### Assign the patient and device assignment

```json
{"type":"set_assignment","patient_id":"PAT-...","assignment_id":"ASN-..."}
```

### Apply care-plan limits

```json
{
  "type":"set_limits",
  "min_hz":100,
  "target_hz":150,
  "max_hz":180,
  "max_duration_seconds":900,
  "manual_control_allowed":true
}
```

## Session commands

### Start

```json
{
  "type":"start_session",
  "schedule_id":"SCH-...",
  "target_hz":150,
  "duration_seconds":600
}
```

### Change frequency

```json
{"type":"set_frequency","hz":160}
```

Sending `0` stops and records the active session; a session cannot start at 0 Hz.

### Normal stop

```json
{"type":"stop_session","reason":"patient_stop"}
```

### Emergency stop

```json
{"type":"emergency_stop"}
```

### Read status

```json
{"type":"get_status"}
```

## Offline session transfer

Request pending records:

```json
{"type":"get_pending"}
```

Each record is sent through response notifications as:

```text
#BEGIN:<session_id>:<json_length>
<one or more raw JSON chunks>
#END:<session_id>
```

After the phone uploads the record and receives server acknowledgement, it must send:

```json
{"type":"ack_session","session_id":"SES-..."}
```

The device deletes the local event only after this acknowledgement or a successful direct API upload.

## Factory reset

```json
{"type":"factory_reset","confirmation":"RESET_NEUROSENSE"}
```

Factory reset clears Wi-Fi, server configuration, assignment, care-plan limits and queued session records, then restarts the device.

## Safety and prototype limitations

- The hard software limit is 230 Hz.
- The patient-specific care-plan range is enforced independently.
- The motors stop when the BLE app disconnects during an app-controlled session.
- Completed/interrupted sessions are written to LittleFS before synchronization.
- An active-session checkpoint is stored in NVS and recovered as interrupted after an unexpected reset or power loss.
- New sessions are blocked when 250 records are pending, preventing uncontrolled filesystem growth.
- ERM vibration frequency is estimated from PWM calibration, not measured.
- HTTPS validates the Netlify certificate chain with the embedded ISRG Root X1 CA; insecure TLS is never enabled. The CA must be maintained through firmware updates before expiry or certificate-chain changes.
- Wi-Fi credentials and API credentials are stored in ESP32 Preferences. Enable NVS encryption, flash encryption, secure boot and authenticated BLE enrollment before a production or clinical deployment.
