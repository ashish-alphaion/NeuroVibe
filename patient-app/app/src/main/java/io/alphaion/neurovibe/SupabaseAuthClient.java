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

    static final class PatientSession {
        final String accessToken;
        final String userId;
        final String patientId;
        final String fullName;
        final String patientCode;

        PatientSession(String accessToken, String userId, String patientId, String fullName, String patientCode) {
            this.accessToken = accessToken;
            this.userId = userId;
            this.patientId = patientId;
            this.fullName = fullName;
            this.patientCode = patientCode;
        }
    }

    void signIn(String email, String password, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson("POST", "/auth/v1/token?grant_type=password", null,
                        new JSONObject().put("email", email).put("password", password));
                String token = response.getString("access_token");
                String userId = response.getJSONObject("user").getString("id");
                callback.onSuccess(loadPatient(token, userId));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    void acceptInvitation(String accessToken, String password, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject user = requestJson("PUT", "/auth/v1/user", accessToken,
                        new JSONObject().put("password", password));
                callback.onSuccess(loadPatient(accessToken, user.getString("id")));
            } catch (Exception error) {
                callback.onError(friendlyMessage(error));
            }
        });
    }

    private PatientSession loadPatient(String token, String userId) throws Exception {
        String profileSelect = URLEncoder.encode("id,role,status", StandardCharsets.UTF_8.name());
        JSONArray profiles = requestArray("/rest/v1/profiles?select=" + profileSelect + "&id=eq." + userId, token);
        if (profiles.length() != 1) throw new Exception("Patient profile was not found.");
        JSONObject profile = profiles.getJSONObject(0);
        if (!"patient".equals(profile.optString("role"))) throw new Exception("This account is not a patient account.");
        if (!"active".equals(profile.optString("status"))) throw new Exception("This patient account is not active.");

        String patientSelect = URLEncoder.encode("id,full_name,patient_code", StandardCharsets.UTF_8.name());
        JSONArray patients = requestArray("/rest/v1/patients?select=" + patientSelect + "&user_id=eq." + userId, token);
        if (patients.length() != 1) throw new Exception("This login is not linked to a patient record.");
        JSONObject patient = patients.getJSONObject(0);
        return new PatientSession(token, userId, patient.getString("id"), patient.getString("full_name"), patient.getString("patient_code"));
    }

    private JSONArray requestArray(String path, String token) throws Exception {
        return new JSONArray(request("GET", path, token, null));
    }

    private JSONObject requestJson(String method, String path, String token, JSONObject body) throws Exception {
        return new JSONObject(request(method, path, token, body));
    }

    private String request(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(SUPABASE_URL + path).openConnection();
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
