package com.neurosense.app;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local-only patient and session records for the password-protected admin workspace. */
final class AdminDataStore {
    private static final String STORAGE_KEY = "admin_patient_records_v1";
    private static final String EXISTING_PATIENTS_CLEARED_KEY =
            "admin_existing_patients_cleared_v1";

    static final class FrequencyMark {
        long timestamp;
        int frequency;

        FrequencyMark(long timestamp, int frequency) {
            this.timestamp = timestamp;
            this.frequency = frequency;
        }
    }

    static final class SessionRecord {
        String id = UUID.randomUUID().toString();
        String sessionNumber;
        String objective;
        String review;
        long startedAt;
        long endedAt;
        final List<FrequencyMark> frequencyMarks = new ArrayList<>();

        int finalFrequency() {
            return frequencyMarks.isEmpty() ? 0
                    : frequencyMarks.get(frequencyMarks.size() - 1).frequency;
        }

        int lastActiveFrequency() {
            for (int index = frequencyMarks.size() - 1; index >= 0; index--) {
                if (frequencyMarks.get(index).frequency > 0) {
                    return frequencyMarks.get(index).frequency;
                }
            }
            return 0;
        }

        long durationMillis() {
            long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
            return Math.max(0L, end - startedAt);
        }
    }

    static final class PatientRecord {
        String id = UUID.randomUUID().toString();
        String name;
        int age;
        String sex;
        String phoneNumber;
        String aadhaarId;
        String abhaId;
        String review;
        final List<ReportAttachment> previousReports = new ArrayList<>();
        final List<SessionRecord> sessions = new ArrayList<>();
    }

    static final class ReportAttachment {
        String name;
        String localPath;
        String mimeType;

        ReportAttachment(String name, String localPath, String mimeType) {
            this.name = name;
            this.localPath = localPath;
            this.mimeType = mimeType;
        }
    }

    private final SharedPreferences preferences;
    private final List<PatientRecord> patients = new ArrayList<>();

    AdminDataStore(SharedPreferences preferences) {
        this.preferences = preferences;
        load();
        clearExistingPatientsOnce();
    }

    private void clearExistingPatientsOnce() {
        if (preferences.getBoolean(EXISTING_PATIENTS_CLEARED_KEY, false)) {
            return;
        }
        for (PatientRecord patient : patients) {
            for (ReportAttachment report : patient.previousReports) {
                if (report.localPath != null && !report.localPath.isEmpty()) {
                    new File(report.localPath).delete();
                }
            }
        }
        patients.clear();
        save();
        preferences.edit().putBoolean(
                EXISTING_PATIENTS_CLEARED_KEY, true).apply();
    }

    List<PatientRecord> patients() {
        return patients;
    }

    PatientRecord findPatient(String id) {
        if (id == null) {
            return null;
        }
        for (PatientRecord patient : patients) {
            if (id.equals(patient.id)) {
                return patient;
            }
        }
        return null;
    }

    PatientRecord addPatient(String name, int age, String sex,
                             String phoneNumber, String aadhaarId,
                             String abhaId, String review,
                             List<ReportAttachment> reports) {
        PatientRecord patient = new PatientRecord();
        patient.name = name;
        patient.age = age;
        patient.sex = sex;
        patient.phoneNumber = phoneNumber;
        patient.aadhaarId = aadhaarId;
        patient.abhaId = abhaId;
        patient.review = review;
        if (reports != null) {
            patient.previousReports.addAll(reports);
        }
        patients.add(patient);
        save();
        return patient;
    }

    void updatePatient(PatientRecord patient, String name, int age, String sex,
                       String phoneNumber, String aadhaarId, String abhaId,
                       String review) {
        patient.name = name;
        patient.age = age;
        patient.sex = sex;
        patient.phoneNumber = phoneNumber;
        patient.aadhaarId = aadhaarId;
        patient.abhaId = abhaId;
        patient.review = review;
        save();
    }

    void deletePatient(String patientId) {
        PatientRecord patient = findPatient(patientId);
        if (patient != null) {
            for (ReportAttachment report : patient.previousReports) {
                if (report.localPath != null && !report.localPath.isEmpty()) {
                    new File(report.localPath).delete();
                }
            }
            patients.remove(patient);
        }
        save();
    }

    void deleteSessionHistory(PatientRecord patient) {
        patient.sessions.clear();
        save();
    }

    int totalSessions() {
        int total = 0;
        for (PatientRecord patient : patients) {
            total += patient.sessions.size();
        }
        return total;
    }

    void save() {
        JSONArray patientArray = new JSONArray();
        try {
            for (PatientRecord patient : patients) {
                JSONObject patientJson = new JSONObject();
                patientJson.put("id", patient.id);
                patientJson.put("name", patient.name);
                patientJson.put("age", patient.age);
                patientJson.put("sex", patient.sex);
                patientJson.put("phone", patient.phoneNumber);
                patientJson.put("aadhaarId", patient.aadhaarId);
                patientJson.put("abhaId", patient.abhaId);
                patientJson.put("review", patient.review);
                JSONArray reportArray = new JSONArray();
                for (ReportAttachment report : patient.previousReports) {
                    JSONObject reportJson = new JSONObject();
                    reportJson.put("name", report.name);
                    reportJson.put("localPath", report.localPath);
                    reportJson.put("mimeType", report.mimeType);
                    reportArray.put(reportJson);
                }
                patientJson.put("previousReports", reportArray);
                JSONArray sessionArray = new JSONArray();
                for (SessionRecord session : patient.sessions) {
                    JSONObject sessionJson = new JSONObject();
                    sessionJson.put("id", session.id);
                    sessionJson.put("number", session.sessionNumber);
                    sessionJson.put("objective", session.objective);
                    sessionJson.put("review", session.review);
                    sessionJson.put("startedAt", session.startedAt);
                    sessionJson.put("endedAt", session.endedAt);
                    JSONArray markArray = new JSONArray();
                    for (FrequencyMark mark : session.frequencyMarks) {
                        JSONObject markJson = new JSONObject();
                        markJson.put("timestamp", mark.timestamp);
                        markJson.put("frequency", mark.frequency);
                        markArray.put(markJson);
                    }
                    sessionJson.put("marks", markArray);
                    sessionArray.put(sessionJson);
                }
                patientJson.put("sessions", sessionArray);
                patientArray.put(patientJson);
            }
        } catch (JSONException ignored) {
            return;
        }
        preferences.edit().putString(STORAGE_KEY, patientArray.toString()).apply();
    }

    private void load() {
        patients.clear();
        String raw = preferences.getString(STORAGE_KEY, "[]");
        boolean repairedInterruptedSession = false;
        try {
            JSONArray patientArray = new JSONArray(raw == null ? "[]" : raw);
            for (int patientIndex = 0; patientIndex < patientArray.length(); patientIndex++) {
                JSONObject patientJson = patientArray.getJSONObject(patientIndex);
                PatientRecord patient = new PatientRecord();
                patient.id = patientJson.optString("id", patient.id);
                patient.name = patientJson.optString("name", "Unnamed patient");
                patient.age = patientJson.optInt("age", 0);
                patient.sex = patientJson.optString("sex", "Not recorded");
                patient.phoneNumber = patientJson.optString("phone", "");
                patient.aadhaarId = patientJson.optString("aadhaarId", "");
                patient.abhaId = patientJson.optString("abhaId", "");
                patient.review = patientJson.optString("review", "");
                JSONArray reportArray = patientJson.optJSONArray("previousReports");
                if (reportArray != null) {
                    for (int reportIndex = 0;
                         reportIndex < reportArray.length(); reportIndex++) {
                        JSONObject reportJson = reportArray.getJSONObject(reportIndex);
                        patient.previousReports.add(new ReportAttachment(
                                reportJson.optString("name", "Previous report"),
                                reportJson.optString("localPath", ""),
                                reportJson.optString("mimeType", "application/octet-stream")));
                    }
                }
                JSONArray sessionArray = patientJson.optJSONArray("sessions");
                if (sessionArray != null) {
                    for (int sessionIndex = 0; sessionIndex < sessionArray.length(); sessionIndex++) {
                        JSONObject sessionJson = sessionArray.getJSONObject(sessionIndex);
                        SessionRecord session = new SessionRecord();
                        session.id = sessionJson.optString("id", session.id);
                        session.sessionNumber = sessionJson.optString("number", "");
                        session.objective = sessionJson.optString("objective", "");
                        session.review = sessionJson.optString("review", "");
                        session.startedAt = sessionJson.optLong("startedAt", 0L);
                        session.endedAt = sessionJson.optLong("endedAt", 0L);
                        JSONArray markArray = sessionJson.optJSONArray("marks");
                        if (markArray != null) {
                            for (int markIndex = 0; markIndex < markArray.length(); markIndex++) {
                                JSONObject markJson = markArray.getJSONObject(markIndex);
                                session.frequencyMarks.add(new FrequencyMark(
                                        markJson.optLong("timestamp", session.startedAt),
                                        markJson.optInt("frequency", 0)));
                            }
                        }
                        if (session.startedAt > 0 && session.endedAt == 0) {
                            long safeEnd = session.frequencyMarks.isEmpty()
                                    ? session.startedAt
                                    : session.frequencyMarks.get(
                                    session.frequencyMarks.size() - 1).timestamp;
                            if (session.finalFrequency() != 0) {
                                session.frequencyMarks.add(
                                        new FrequencyMark(safeEnd, 0));
                            }
                            session.endedAt = safeEnd;
                            String existingReview = session.review == null
                                    ? "" : session.review.trim();
                            session.review = (existingReview +
                                    "\nRecovered after an interrupted app session.").trim();
                            repairedInterruptedSession = true;
                        }
                        patient.sessions.add(session);
                    }
                }
                patients.add(patient);
            }
        } catch (JSONException ignored) {
            patients.clear();
        }
        if (repairedInterruptedSession) {
            save();
        }
    }
}
