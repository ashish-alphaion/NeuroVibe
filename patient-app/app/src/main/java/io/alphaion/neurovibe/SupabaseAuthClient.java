package io.alphaion.neurovibe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SupabaseAuthClient {
    private static final String SUPABASE_URL = "https://immeobunbmxsicmixvpo.supabase.co";
    private static final String PUBLISHABLE_KEY = "sb_publishable_R-nFH-dDCKvHpIB_9X28JA_Q7zZsBCB";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    interface Callback {
        void onSuccess(PatientSession session);
        void onError(String message);
    }

    interface ProvisioningCallback {
        void onSuccess(DeviceProvisioning provisioning);
        void onError(String message);
    }

    interface SyncCallback {
        void onSuccess(String sessionId);
        void onError(String message);
    }

    static final class PatientSession {
        final String accessToken;
        final String refreshToken;
        final String userId;
        final String patientId;
        final String fullName;
        final String patientCode;
        final String assignmentId;
        final long assignmentValidUntilEpoch;
        final String assignedDeviceId;
        final String assignedDeviceName;
        final String carePlanName;
        final double minHz;
        final double targetHz;
        final double maxHz;
        final int durationSeconds;
        final int maxDurationSeconds;
        final boolean manualControlAllowed;
        final JSONArray assignmentHistory;
        final JSONArray appointments;
        final JSONArray deviceUsage;

        PatientSession(String accessToken, String refreshToken, String userId, String patientId, String fullName, String patientCode,
                       String assignmentId, long assignmentValidUntilEpoch, String assignedDeviceId, String assignedDeviceName,
                       String carePlanName, double minHz, double targetHz, double maxHz,
                       int durationSeconds, int maxDurationSeconds, boolean manualControlAllowed,
                       JSONArray assignmentHistory, JSONArray appointments, JSONArray deviceUsage) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.patientId = patientId;
            this.fullName = fullName;
            this.patientCode = patientCode;
            this.assignmentId = assignmentId;
            this.assignmentValidUntilEpoch = assignmentValidUntilEpoch;
            this.assignedDeviceId = assignedDeviceId;
            this.assignedDeviceName = assignedDeviceName;
            this.carePlanName = carePlanName;
            this.minHz = minHz;
            this.targetHz = targetHz;
            this.maxHz = maxHz;
            this.durationSeconds = durationSeconds;
            this.maxDurationSeconds = maxDurationSeconds;
            this.manualControlAllowed = manualControlAllowed;
            this.assignmentHistory = assignmentHistory;
            this.appointments = appointments;
            this.deviceUsage = deviceUsage;
        }
    }

    static final class DeviceProvisioning {
        final String apiBaseUrl;
        final String apiToken;
        final String patientId;
        final String assignmentId;
        final long assignmentValidUntilEpoch;
        final long serverTimeEpoch;
        final JSONObject carePlan;

        DeviceProvisioning(JSONObject value) throws Exception {
            apiBaseUrl = value.getString("api_base_url");
            apiToken = value.getString("api_token");
            patientId = value.getString("patient_id");
            assignmentId = value.getString("assignment_id");
            assignmentValidUntilEpoch = value.optLong("assignment_valid_until_epoch", 0);
            serverTimeEpoch = value.optLong("server_time_epoch", System.currentTimeMillis() / 1000L);
            carePlan = value.optJSONObject("care_plan");
        }
    }

    void signIn(String email, String password, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson("POST", "/auth/v1/token?grant_type=password", null,
                        new JSONObject().put("email", email).put("password", password));
                String token = response.getString("access_token");
                String userId = response.getJSONObject("user").getString("id");
                callback.onSuccess(loadPatient(token, response.optString("refresh_token", ""), userId));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    void restoreSession(String refreshToken, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson("POST", "/auth/v1/token?grant_type=refresh_token", null,
                        new JSONObject().put("refresh_token", refreshToken));
                callback.onSuccess(loadPatient(response.getString("access_token"),
                        response.optString("refresh_token", refreshToken),
                        response.getJSONObject("user").getString("id")));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    void acceptInvitation(String accessToken, String refreshToken, String password, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject user = requestJson("PUT", "/auth/v1/user", accessToken,
                        new JSONObject().put("password", password));
                callback.onSuccess(loadPatient(accessToken, refreshToken == null ? "" : refreshToken, user.getString("id")));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    void provisionDevice(PatientSession session, String deviceId, String hardwareId, ProvisioningCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson("POST", "https://neurovibeapi.netlify.app/api/device-provisioning",
                        session.accessToken, new JSONObject()
                                .put("device_id", deviceId)
                                .put("hardware_id", hardwareId)
                                .put("patient_code", session.patientCode));
                callback.onSuccess(new DeviceProvisioning(response));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    void uploadRelayedUsage(PatientSession session, JSONObject record, SyncCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson("POST", "https://neurovibeapi.netlify.app/api/patient-sync",
                        session.accessToken, record);
                if (!response.optBoolean("acknowledged", false)) throw new Exception("The server did not acknowledge this usage record.");
                callback.onSuccess(response.optString("session_id", record.optString("session_id")));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    private PatientSession loadPatient(String token, String refreshToken, String userId) throws Exception {
        String profileSelect = URLEncoder.encode("id,role,status", StandardCharsets.UTF_8.name());
        JSONArray profiles = requestArray("/rest/v1/profiles?select=" + profileSelect + "&id=eq." + userId, token);
        if (profiles.length() != 1) throw new Exception("Patient profile was not found.");
        JSONObject profile = profiles.getJSONObject(0);
        if (!"patient".equals(profile.optString("role"))) throw new Exception("This account is not a patient account.");
        if (!"active".equals(profile.optString("status"))) throw new Exception("This patient account is not active.");

        String patientSelect = URLEncoder.encode("id,full_name,patient_code,device_assignments(id,status,device_id,lease_expires_at,devices(id,hardware_id,display_name,lifecycle_status)),care_plans(id,name,status,min_hz,target_hz,max_hz,duration_seconds,max_duration_seconds,manual_control_allowed)", StandardCharsets.UTF_8.name());
        JSONArray patients = requestArray("/rest/v1/patients?select=" + patientSelect + "&user_id=eq." + userId, token);
        if (patients.length() != 1) throw new Exception("This login is not linked to a patient record.");
        JSONObject patient = patients.getJSONObject(0);
        JSONObject assignment = findByStatus(patient.optJSONArray("device_assignments"), "active");
        JSONObject plan = findByStatus(patient.optJSONArray("care_plans"), "active");
        JSONObject device = assignment == null ? null : assignment.optJSONObject("devices");
        String patientId = patient.getString("id");
        String appointmentSelect = URLEncoder.encode("id,title,appointment_type,scheduled_for,duration_minutes,location,notes,status", StandardCharsets.UTF_8.name());
        JSONArray appointments;
        try { appointments = requestArray("/rest/v1/appointments?select=" + appointmentSelect + "&patient_id=eq." + patientId + "&status=in.(scheduled,confirmed)&order=scheduled_for.asc", token); }
        catch (Exception unavailable) { appointments = new JSONArray(); }
        String usageSelect = URLEncoder.encode("id,started_at_utc,duration_seconds,requested_hz,measured_hz,estimated_hz,status,completion_reason,sync_source", StandardCharsets.UTF_8.name());
        JSONArray deviceUsage;
        try { deviceUsage = requestArray("/rest/v1/therapy_sessions?select=" + usageSelect + "&patient_id=eq." + patientId + "&order=started_at_utc.desc&limit=50", token); }
        catch (Exception unavailable) { deviceUsage = new JSONArray(); }
        return new PatientSession(token, refreshToken, userId, patient.getString("id"), patient.getString("full_name"), patient.getString("patient_code"),
                assignment == null ? null : assignment.optString("id", null),
                assignment == null ? 0 : parseEpoch(assignment.optString("lease_expires_at", "")),
                assignment == null ? null : assignment.optString("device_id", null),
                device == null ? null : device.optString("display_name", null),
                plan == null ? null : plan.optString("name", null),
                plan == null ? 0 : plan.optDouble("min_hz", 0),
                plan == null ? 0 : plan.optDouble("target_hz", 0),
                plan == null ? 0 : plan.optDouble("max_hz", 0),
                plan == null ? 0 : plan.optInt("duration_seconds", 0),
                plan == null ? 0 : plan.optInt("max_duration_seconds", 0),
                plan != null && plan.optBoolean("manual_control_allowed", false),
                patient.optJSONArray("device_assignments") == null ? new JSONArray() : patient.optJSONArray("device_assignments"),
                appointments, deviceUsage);
    }

    private long parseEpoch(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return java.time.Instant.parse(value).getEpochSecond();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private JSONObject findByStatus(JSONArray values, String status) {
        if (values == null) return null;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value != null && status.equals(value.optString("status"))) return value;
        }
        return null;
    }

    private JSONArray requestArray(String path, String token) throws Exception {
        return new JSONArray(request("GET", path, token, null));
    }

    private JSONObject requestJson(String method, String path, String token, JSONObject body) throws Exception {
        return new JSONObject(request(method, path, token, body));
    }

    private String request(String method, String path, String token, JSONObject body) throws Exception {
        String requestUrl = path.startsWith("https://") ? path : SUPABASE_URL + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("apikey", PUBLISHABLE_KEY);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
        }
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String message = "Authentication failed.";
            try {
                JSONObject error = new JSONObject(text.toString());
                message = error.optString("msg", error.optString("message", error.optString("error_description", message)));
            } catch (Exception ignored) { }
            throw new Exception(message);
        }
        return text.toString();
    }

    private String friendlyMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Unable to contact NeuroVibe. Check your internet connection.";
        if (message.toLowerCase().contains("invalid login credentials")) return "Incorrect email or password.";
        return message;
    }

    void close() {
        executor.shutdownNow();
    }
}
