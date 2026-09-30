package com.neurosense.app;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Local-only audit trail for device use while Pass to Patient mode is active. */
final class PtpActivityStore {
    private static final String STORAGE_KEY = "ptp_activity_records_v1";

    static final class FrequencyMark {
        long timestamp;
        int frequency;

        FrequencyMark(long timestamp, int frequency) {
            this.timestamp = timestamp;
            this.frequency = frequency;
        }
    }

    static final class ActivityRecord {
        String id = UUID.randomUUID().toString();
        String patientName;
        String phoneNumber;
        long connectedAt;
        long disconnectedAt;
        final List<FrequencyMark> frequencyMarks = new ArrayList<>();

        long connectionDurationMillis() {
            long end = disconnectedAt > 0
                    ? disconnectedAt : System.currentTimeMillis();
            return Math.max(0L, end - connectedAt);
        }

        long activeDurationMillis() {
            long duration = 0L;
            for (int index = 0; index < frequencyMarks.size(); index++) {
                FrequencyMark mark = frequencyMarks.get(index);
                if (mark.frequency <= 0) {
                    continue;
                }
                long end = index + 1 < frequencyMarks.size()
                        ? frequencyMarks.get(index + 1).timestamp
                        : disconnectedAt > 0
                        ? disconnectedAt : System.currentTimeMillis();
                duration += Math.max(0L, end - mark.timestamp);
            }
            return duration;
        }

        long firstUsedAt() {
            for (FrequencyMark mark : frequencyMarks) {
                if (mark.frequency > 0) {
                    return mark.timestamp;
                }
            }
            return 0L;
        }

        long lastUsedAt() {
            for (int index = frequencyMarks.size() - 1; index >= 0; index--) {
                FrequencyMark mark = frequencyMarks.get(index);
                if (mark.frequency > 0) {
                    if (index + 1 < frequencyMarks.size()) {
                        return frequencyMarks.get(index + 1).timestamp;
                    }
                    return disconnectedAt > 0
                            ? disconnectedAt : System.currentTimeMillis();
                }
            }
            return 0L;
        }

        int lastActiveFrequency() {
            for (int index = frequencyMarks.size() - 1; index >= 0; index--) {
                int frequency = frequencyMarks.get(index).frequency;
                if (frequency > 0) {
                    return frequency;
                }
            }
            return 0;
        }

        int frequencyChangeCount() {
            int count = 0;
            for (FrequencyMark mark : frequencyMarks) {
                if (mark.frequency > 0) {
                    count++;
                }
            }
            return count;
        }

        int finalFrequency() {
            return frequencyMarks.isEmpty() ? 0
                    : frequencyMarks.get(frequencyMarks.size() - 1).frequency;
        }
    }

    private final SharedPreferences preferences;
    private final List<ActivityRecord> records = new ArrayList<>();
    private ActivityRecord activeRecord;

    PtpActivityStore(SharedPreferences preferences) {
        this.preferences = preferences;
        load();
    }

    List<ActivityRecord> recordsNewestFirst() {
        List<ActivityRecord> sorted = new ArrayList<>(records);
        Collections.sort(sorted, (left, right) ->
                Long.compare(right.connectedAt, left.connectedAt));
        return sorted;
    }

    int recordCount() {
        return records.size();
    }

    boolean hasActiveRecord() {
        return activeRecord != null;
    }

    void clearAll() {
        records.clear();
        activeRecord = null;
        preferences.edit().remove(STORAGE_KEY).apply();
    }

    long totalActiveDurationMillis() {
        long total = 0L;
        for (ActivityRecord record : records) {
            total += record.activeDurationMillis();
        }
        return total;
    }

    ActivityRecord startConnection(String patientName, String phoneNumber,
                                   long timestamp) {
        finishConnection(timestamp);
        ActivityRecord record = new ActivityRecord();
        record.patientName = patientName == null ? "" : patientName;
        record.phoneNumber = phoneNumber == null ? "" : phoneNumber;
        record.connectedAt = timestamp;
        records.add(record);
        activeRecord = record;
        save();
        return record;
    }

    void recordFrequency(int frequency, long timestamp) {
        if (activeRecord == null) {
            return;
        }
        int constrained = Math.max(0, frequency);
        if (activeRecord.finalFrequency() == constrained &&
                !activeRecord.frequencyMarks.isEmpty()) {
            return;
        }
        activeRecord.frequencyMarks.add(
                new FrequencyMark(timestamp, constrained));
        save();
    }

    void finishConnection(long timestamp) {
        if (activeRecord == null) {
            return;
        }
        long safeTimestamp = Math.max(timestamp, activeRecord.connectedAt);
        if (activeRecord.finalFrequency() != 0) {
            activeRecord.frequencyMarks.add(
                    new FrequencyMark(safeTimestamp, 0));
        }
        activeRecord.disconnectedAt = safeTimestamp;
        activeRecord = null;
        save();
    }

    void save() {
        JSONArray recordArray = new JSONArray();
        try {
            for (ActivityRecord record : records) {
                JSONObject recordJson = new JSONObject();
                recordJson.put("id", record.id);
                recordJson.put("patientName", record.patientName);
                recordJson.put("phoneNumber", record.phoneNumber);
                recordJson.put("connectedAt", record.connectedAt);
                recordJson.put("disconnectedAt", record.disconnectedAt);
                JSONArray markArray = new JSONArray();
                for (FrequencyMark mark : record.frequencyMarks) {
                    JSONObject markJson = new JSONObject();
                    markJson.put("timestamp", mark.timestamp);
                    markJson.put("frequency", mark.frequency);
                    markArray.put(markJson);
                }
                recordJson.put("marks", markArray);
                recordArray.put(recordJson);
            }
        } catch (JSONException ignored) {
            return;
        }
        preferences.edit().putString(STORAGE_KEY, recordArray.toString()).apply();
    }

    private void load() {
        records.clear();
        String raw = preferences.getString(STORAGE_KEY, "[]");
        boolean repaired = false;
        try {
            JSONArray recordArray = new JSONArray(raw == null ? "[]" : raw);
            for (int recordIndex = 0;
                 recordIndex < recordArray.length(); recordIndex++) {
                JSONObject recordJson = recordArray.getJSONObject(recordIndex);
                ActivityRecord record = new ActivityRecord();
                record.id = recordJson.optString("id", record.id);
                record.patientName = recordJson.optString(
                        "patientName", "Unnamed patient");
                record.phoneNumber = recordJson.optString("phoneNumber", "");
                record.connectedAt = recordJson.optLong("connectedAt", 0L);
                record.disconnectedAt = recordJson.optLong("disconnectedAt", 0L);
                JSONArray markArray = recordJson.optJSONArray("marks");
                if (markArray != null) {
                    for (int markIndex = 0;
                         markIndex < markArray.length(); markIndex++) {
                        JSONObject markJson = markArray.getJSONObject(markIndex);
                        record.frequencyMarks.add(new FrequencyMark(
                                markJson.optLong("timestamp", record.connectedAt),
                                markJson.optInt("frequency", 0)));
                    }
                }
                if (record.connectedAt > 0 && record.disconnectedAt == 0) {
                    long safeEnd = record.frequencyMarks.isEmpty()
                            ? record.connectedAt
                            : record.frequencyMarks.get(
                            record.frequencyMarks.size() - 1).timestamp;
                    if (record.finalFrequency() != 0) {
                        record.frequencyMarks.add(new FrequencyMark(safeEnd, 0));
                    }
                    record.disconnectedAt = safeEnd;
                    repaired = true;
                }
                if (record.connectedAt > 0) {
                    records.add(record);
                }
            }
        } catch (JSONException ignored) {
            records.clear();
        }
        if (repaired) {
            save();
        }
    }
}
