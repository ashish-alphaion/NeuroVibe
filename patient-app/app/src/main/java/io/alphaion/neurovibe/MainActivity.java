package io.alphaion.neurovibe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class MainActivity extends Activity implements NeuroSenseBleManager.Listener {
    private static final int NAVY = Color.rgb(0, 29, 50);
    private static final int NAVY_CARD = Color.rgb(18, 50, 75);
    private static final int BLUE = Color.rgb(5, 100, 147);
    private static final int SKY = Color.rgb(210, 236, 255);
    private static final int PALE = Color.rgb(245, 250, 255);
    private static final int MUTED = Color.rgb(67, 71, 77);
    private static final int LINE = Color.rgb(210, 224, 231);
    private static final int RED = Color.rgb(186, 26, 26);
    private static final int GREEN = Color.rgb(24, 121, 78);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothDevice> scanResults = new LinkedHashMap<>();
    private NeuroSenseBleManager ble;
    private SupabaseAuthClient authClient;
    private SharedPreferences preferences;
    private FrameLayout root;
    private LinearLayout deviceList;
    private TextView connectionLabel;
    private String connectedName = "Not connected";
    private boolean connected;
    private boolean deviceVerified;
    private String physicalDeviceId = "";
    private String hardwareDeviceId = "Unknown hardware";
    private boolean factoryDeviceEmpty;
    private SupabaseAuthClient.PatientSession patientSession;
    private SupabaseAuthClient.DeviceProvisioning pendingProvisioning;
    private boolean setupInProgress;
    private boolean setupComplete;
    private String savedWifiSsid = "";
    private boolean wifiOnline;
    private boolean wifiAttemptPending;
    private String lastWifiDiagnostic = "";
    private boolean serverVerified;
    private int pendingRecords;
    private String syncMessage = "Waiting for device";
    private boolean identificationMode;
    private CountDownTimer sessionTimer;
    private int selectedHz = 85;
    private int remainingSeconds = 600;
    private int selectedDurationMinutes = 15;
    private String patientName = "Patient";
    private String patientCode = "Not linked";
    private long lastForegroundRefreshMillis;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(PALE); window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root = new FrameLayout(this); root.setBackgroundColor(PALE); setContentView(root);
        ble = new NeuroSenseBleManager(this, this);
        authClient = new SupabaseAuthClient();
        preferences = getSharedPreferences("neurovibe_private", MODE_PRIVATE);
        savedWifiSsid = preferences.getString("last_wifi_ssid", "");
        createNotificationChannel();
        if (!handleAuthIntent(getIntent())) restoreOrWelcome();
    }

    private void restoreOrWelcome() {
        showSplash();
        String refreshToken = preferences.getString("refresh_token", "");
        if (refreshToken.isEmpty()) { handler.postDelayed(this::showWelcome, 500); return; }
        authClient.restoreSession(refreshToken, new SupabaseAuthClient.Callback() {
            public void onSuccess(SupabaseAuthClient.PatientSession session) { runOnUiThread(() -> { applyPatientSession(session); requestAppPermissions(); routeAfterLogin(); }); }
            public void onError(String message) { runOnUiThread(() -> { preferences.edit().remove("refresh_token").apply(); showWelcome(); toast("Please sign in again once to restore this device."); }); }
        });
    }

    private void showSplash() {
        root.removeAllViews();
        LinearLayout box = column(Gravity.CENTER, 0); box.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView mark = brandTitle(30); mark.setTextColor(NAVY); box.addView(mark);
        ProgressBar progress = new ProgressBar(this); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42)); pp.topMargin = dp(28); box.addView(progress, pp);
        root.addView(box, match());
    }

    private void showWelcome() {
        LinearLayout page = page(false); page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView brand = brandTitle(24); brand.setTextColor(NAVY); page.addView(brand, margin(-1, -2, 0, 10, 0, 26));
        FrameLayout hero = new FrameLayout(this); hero.setBackground(round(SKY, 140)); hero.setClipToOutline(true);
        ImageView device = new ImageView(this); device.setImageResource(io.alphaion.neurovibe.R.drawable.neurosense_hero); device.setScaleType(ImageView.ScaleType.CENTER_CROP); hero.addView(device, match());
        TextView badge = label("NEUROSENSE", 12); badge.setTextColor(Color.WHITE); badge.setGravity(Gravity.CENTER); badge.setBackground(round(NAVY_CARD, 20)); FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(150), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); bp.bottomMargin = dp(28); hero.addView(badge, bp);
        page.addView(hero, margin(dp(280), dp(280), 0, 0, 0, 30));
        TextView h = title("Welcome to NeuroVibe", 28); h.setGravity(Gravity.CENTER); page.addView(h);
        TextView p = body("View clinic appointments, set up your assigned NeuroSense, and review vibration usage in one place.", 16); p.setGravity(Gravity.CENTER); page.addView(p, margin(-1, -2, 18, 14, 18, 32));
        Button invite = primary("I Have an Invitation"); invite.setOnClickListener(v -> showAccess(true)); page.addView(invite, margin(-1, 52, 0, 0, 0, 12));
        Button signIn = outline("Sign In"); signIn.setOnClickListener(v -> showAccess(false)); page.addView(signIn, margin(-1, 52, 0, 0, 0, 12));
        Button help = textButton("?  Get Help"); help.setOnClickListener(v -> helpDialog()); page.addView(help);
        setScrollable(page);
    }

    private void showAccess(boolean invitation) {
        if (invitation) {
            showInvitationInstructions();
            return;
        }
        LinearLayout page = page(false);
        Button back = textButton("‹ Back"); back.setOnClickListener(v -> showWelcome()); page.addView(back, wrap());
        page.addView(title("Patient sign in", 28), margin(-1, -2, 0, 24, 0, 8));
        page.addView(body("Use the email invited by your clinic and the password you created.", 15), margin(-1, -2, 0, 0, 0, 24));
        EditText email = input("Email address"); page.addView(email, margin(-1, 54, 0, 0, 0, 12));
        EditText secret = input("Password"); secret.setInputType(0x00000081); page.addView(secret, margin(-1, 54, 0, 0, 0, 18));
        Button continueButton = primary("Sign In");
        continueButton.setOnClickListener(v -> {
            String enteredEmail = email.getText().toString().trim(); String enteredSecret = secret.getText().toString();
            if (enteredEmail.isEmpty() || enteredSecret.isEmpty()) { toast("Enter your email and password."); return; }
            continueButton.setEnabled(false); continueButton.setText("Signing in…");
            authClient.signIn(enteredEmail, enteredSecret, new SupabaseAuthClient.Callback() {
                public void onSuccess(SupabaseAuthClient.PatientSession session) { runOnUiThread(() -> { applyPatientSession(session); requestAppPermissions(); routeAfterLogin(); }); }
                public void onError(String message) { runOnUiThread(() -> { continueButton.setEnabled(true); continueButton.setText("Sign In"); toast(message); }); }
            });
        });
        page.addView(continueButton, margin(-1, 54, 0, 0, 0, 14));
        setScrollable(page);
    }

    private void showInvitationInstructions() {
        LinearLayout page = page(false);
        Button back = textButton("‹ Back"); back.setOnClickListener(v -> showWelcome()); page.addView(back, wrap());
        page.addView(title("Accept your invitation", 28), margin(-1, -2, 0, 24, 0, 8));
        page.addView(body("Open the invitation email sent by your clinic and tap Accept invitation. NeuroVibe will open automatically so you can create your private password.", 15), margin(-1, -2, 0, 0, 0, 24));
        LinearLayout note = card(Color.WHITE); note.addView(title("No invitation code is needed", 18)); note.addView(body("Your email invitation securely links this app to your patient record. If it has expired, ask your doctor to send a new invitation.", 14)); page.addView(note);
        Button signIn = outline("I already created my password"); signIn.setOnClickListener(v -> showAccess(false)); page.addView(signIn, margin(-1, 52, 0, 18, 0, 0));
        setScrollable(page);
    }

    private boolean handleAuthIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"neurovibe".equals(data.getScheme()) || !"auth".equals(data.getHost())) return false;
        String fragment = data.getFragment();
        if (fragment == null) { showWelcome(); toast("The invitation link is incomplete."); return true; }
        Uri parameters = Uri.parse("https://callback.invalid/?" + fragment);
        String accessToken = parameters.getQueryParameter("access_token");
        if (accessToken == null || accessToken.isEmpty()) { showWelcome(); toast("The invitation link is invalid or expired."); return true; }
        showSetPassword(accessToken, parameters.getQueryParameter("refresh_token"));
        return true;
    }

    private void showSetPassword(String accessToken, String refreshToken) {
        LinearLayout page = page(false);
        page.addView(title("Create your password", 28), margin(-1, -2, 0, 24, 0, 8));
        page.addView(body("Use at least eight characters. Keep this password private; your doctor cannot see it.", 15), margin(-1, -2, 0, 0, 0, 24));
        EditText password = input("New password"); password.setInputType(0x00000081); page.addView(password, margin(-1,54,0,0,0,12));
        EditText confirmation = input("Confirm password"); confirmation.setInputType(0x00000081); page.addView(confirmation, margin(-1,54,0,0,0,18));
        Button save = primary("Activate account"); page.addView(save, margin(-1,54,0,0,0,14));
        save.setOnClickListener(v -> {
            String value = password.getText().toString();
            if (value.length() < 8) { toast("Password must contain at least eight characters."); return; }
            if (!value.equals(confirmation.getText().toString())) { toast("Passwords do not match."); return; }
            save.setEnabled(false); save.setText("Activating…");
            authClient.acceptInvitation(accessToken, refreshToken, value, new SupabaseAuthClient.Callback() {
                public void onSuccess(SupabaseAuthClient.PatientSession session) { runOnUiThread(() -> { applyPatientSession(session); requestAppPermissions(); routeAfterLogin(); }); }
                public void onError(String message) { runOnUiThread(() -> { save.setEnabled(true); save.setText("Activate account"); toast(message); }); }
            });
        });
        setScrollable(page);
    }

    private void applyPatientSession(SupabaseAuthClient.PatientSession session) {
        patientSession = session;
        patientName = session.fullName;
        patientCode = session.patientCode;
        selectedHz = (int) Math.round(session.targetHz > 0 ? session.targetHz : 0);
        remainingSeconds = session.durationSeconds;
        if (session.refreshToken != null && !session.refreshToken.isEmpty()) preferences.edit().putString("refresh_token", session.refreshToken).apply();
        selectedDurationMinutes = Math.max(1, Math.min(90, preferences.getInt("duration_minutes", 15)));
    }

    private void routeAfterLogin() {
        retryLocalRecords();
        if (patientSession == null || patientSession.assignmentId == null || patientSession.assignedDeviceId == null) showAssignmentRequired();
        else if (!patientSession.assignedDeviceId.equals(preferences.getString("enrolled_device_id", "")) ||
                !patientSession.assignmentId.equals(preferences.getString("enrolled_assignment_id", ""))) showConnect();
        else showHome();
    }

    private void showAssignmentRequired() {
        connected = false; deviceVerified = false;
        LinearLayout page = page(false);
        page.addView(title("Device assignment required", 28), margin(-1, -2, 0, 24, 0, 8));
        page.addView(body(patientName + "'s account is working, but the clinic database has no NeuroSense device assigned to this patient.", 15), margin(-1, -2, 0, 0, 0, 22));
        LinearLayout details = card(Color.WHITE); details.addView(title(patientName, 20)); details.addView(body("Patient code · " + patientCode, 14)); details.addView(body("Patient record · " + (patientSession == null ? "Unavailable" : patientSession.patientId), 12)); page.addView(details);
        TextView steps = body("Doctor action required:\n1. Register the physical device ID in the portal.\n2. Assign it to this patient.\n3. Create an active care plan.\n4. Sign in again to refresh this app.", 15); steps.setBackground(round(SKY, 16)); steps.setPadding(dp(16),dp(16),dp(16),dp(16)); page.addView(steps, margin(-1,-2,0,18,0,0));
        Button identify = outline("Identify nearby NeuroSense"); identify.setOnClickListener(v -> { identificationMode = true; showConnect(); }); page.addView(identify, margin(-1,54,0,18,0,0));
        Button retry = primary("Sign in again after assignment"); retry.setOnClickListener(v -> showAccess(false)); page.addView(retry, margin(-1,54,0,18,0,0));
        setScrollable(page);
    }

    private void showDeviceIdSetup() {
        LinearLayout page = page(false);
        TextView step = label("FIRST-TIME SETUP  ·  1 OF 3", 11); step.setTextColor(BLUE); page.addView(step);
        page.addView(title("Confirm your NeuroSense", 28), margin(-1,-2,0,12,0,8));
        page.addView(body("Enter the Device ID from your doctor's assignment email or the label on the device.", 15), margin(-1,-2,0,0,0,24));
        LinearLayout assigned = card(SKY); assigned.addView(label("ASSIGNED PATIENT", 11)); assigned.addView(title(patientName, 21)); assigned.addView(body("Patient ID · " + patientCode, 13)); page.addView(assigned);
        EditText enteredPatientId = input("Patient ID"); page.addView(enteredPatientId, margin(-1,54,0,20,0,12));
        EditText deviceId = input("Assigned Device ID (for example NS01)"); page.addView(deviceId, margin(-1,54,0,0,0,12));
        page.addView(body("NeuroVibe continues only when this ID matches the device assigned by your doctor.", 13), margin(-1,-2,0,0,0,20));
        Button verify = primary("Verify Device ID"); verify.setOnClickListener(v -> {
            String enteredPatient = enteredPatientId.getText().toString().trim();
            String entered = deviceId.getText().toString().trim();
            if (!patientCode.equalsIgnoreCase(enteredPatient)) { toast("This Patient ID does not match the signed-in patient."); return; }
            if (!patientSession.assignedDeviceId.equalsIgnoreCase(entered)) { toast("This Device ID does not match your doctor's assignment."); return; }
            preferences.edit().putString("setup_device_id", patientSession.assignedDeviceId).apply();
            requestAppPermissions(); showConnect();
        }); page.addView(verify, margin(-1,54,0,0,0,12));
        Button later = textButton("Finish setup later"); later.setOnClickListener(v -> showHome()); page.addView(later);
        setScrollable(page);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (patientSession == null) return;
        long now = System.currentTimeMillis();
        if (now - lastForegroundRefreshMillis < 30_000) return;
        lastForegroundRefreshMillis = now;
        String refreshToken = preferences.getString("refresh_token", "");
        if (refreshToken.isEmpty()) return;
        String previousDeviceId = patientSession.assignedDeviceId;
        String previousAssignmentId = patientSession.assignmentId;
        authClient.restoreSession(refreshToken, new SupabaseAuthClient.Callback() {
            public void onSuccess(SupabaseAuthClient.PatientSession session) { runOnUiThread(() -> {
                boolean assignmentChanged =
                        !java.util.Objects.equals(previousDeviceId, session.assignedDeviceId) ||
                        !java.util.Objects.equals(previousAssignmentId, session.assignmentId);
                applyPatientSession(session);
                if (assignmentChanged) {
                    ble.disconnect();
                    connected = false;
                    deviceVerified = false;
                    serverVerified = false;
                    setupComplete = false;
                    toast("Your doctor assigned a different NeuroSense. Connect the replacement device.");
                    routeAfterLogin();
                }
            }); }
            public void onError(String message) { /* Keep the current offline session and retry later. */ }
        });
    }

    private void showHome() {
        if (patientSession == null || patientSession.assignmentId == null) { showAssignmentRequired(); return; }
        LinearLayout page = page(true); addHeader(page, "Welcome, " + patientName);
        page.addView(body("Choose frequency and duration. Every device run is recorded for your doctor.", 15), margin(-1,-2,0,2,0,18));
        JSONObject nextAppointment = nextAppointment();
        if (nextAppointment != null) {
            LinearLayout appointment = card(Color.WHITE);
            TextView appointmentLabel = label("NEXT DOCTOR APPOINTMENT", 11); appointmentLabel.setTextColor(BLUE); appointment.addView(appointmentLabel);
            appointment.addView(title(nextAppointment.optString("title", "Clinic appointment"), 20), margin(-1,-2,0,8,0,4));
            appointment.addView(body(formatAppointment(nextAppointment), 14)); appointment.setOnClickListener(v -> showSchedule());
            page.addView(appointment, margin(-1,-2,0,0,0,14));
        }
        LinearLayout control = card(NAVY_CARD);
        TextView eyebrow = label("VIBRATION CONTROL", 11); eyebrow.setTextColor(Color.rgb(142,205,255)); control.addView(eyebrow);
        TextView choice = title(selectedHz + " Hz  ·  " + selectedDurationMinutes + " minutes", 24); choice.setTextColor(Color.WHITE); control.addView(choice, margin(-1,-2,0,10,0,10));
        TextView hzText = body("Frequency · 1–230 Hz (0 means stopped)", 13); hzText.setTextColor(Color.rgb(203,230,255)); control.addView(hzText);
        SeekBar hz = new SeekBar(this); hz.setMax(229); hz.setProgress(Math.max(0, selectedHz - 1)); control.addView(hz, margin(-1,44,0,6,0,6));
        TextView durationText = body("Duration · 1–90 minutes", 13); durationText.setTextColor(Color.rgb(203,230,255)); control.addView(durationText);
        SeekBar duration = new SeekBar(this); duration.setMax(89); duration.setProgress(selectedDurationMinutes - 1); control.addView(duration, margin(-1,44,0,6,0,2));
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) { selectedHz = hz.getProgress() + 1; selectedDurationMinutes = duration.getProgress() + 1; choice.setText(selectedHz + " Hz  ·  " + selectedDurationMinutes + " minutes"); preferences.edit().putInt("duration_minutes", selectedDurationMinutes).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {} public void onStopTrackingTouch(SeekBar bar) {}
        }; hz.setOnSeekBarChangeListener(listener); duration.setOnSeekBarChangeListener(listener);
        Button start = accent(isDeviceReady() ? "Start vibration" : "Complete device setup"); start.setOnClickListener(v -> { if (isDeviceReady()) startSession(); else showConnect(); }); control.addView(start, margin(-1,50,0,16,0,0)); page.addView(control);
        connectionLabel = label(isDeviceReady() ? "●  " + connectedName + " verified" : connected ? "●  Connected · setup incomplete" : "○  Assigned device disconnected", 12); connectionLabel.setTextColor(isDeviceReady() ? GREEN : MUTED); connectionLabel.setOnClickListener(v -> showConnect()); page.addView(connectionLabel, margin(-1,-2,2,12,0,16));
        LinearLayout sync = card(Color.WHITE); sync.addView(title("Data synchronization", 18)); sync.addView(body((wifiOnline ? "Wi-Fi connected" : "Wi-Fi offline") + " · " + pendingRecords + " pending\n" + syncMessage, 13)); page.addView(sync);
        LinearLayout actions = row(); Button device = outline(connected ? "Device settings" : "Connect device"); device.setOnClickListener(v -> showConnect()); actions.addView(device, weight()); Button usage = outline("View usage"); usage.setOnClickListener(v -> showHistory()); actions.addView(usage, weightWithLeft()); page.addView(actions, margin(-1,54,0,18,0,0));
        setScrollable(page); addBottomNav("home");
    }

    private void showSchedule() {
        LinearLayout page = page(true); addHeader(page, "My Appointments");
        page.addView(body("Visits scheduled by your doctor. Using NeuroSense is recorded separately under Device Usage.", 15), margin(-1,-2,0,2,0,18));
        JSONArray appointments = patientSession == null ? null : patientSession.appointments;
        if (appointments == null || appointments.length() == 0) {
            page.addView(emptyCard("No upcoming appointments", "New clinic, video, phone, or home visits assigned by your doctor will appear here."));
        } else {
            for (int index = 0; index < appointments.length(); index++) {
                JSONObject appointment = appointments.optJSONObject(index);
                if (appointment == null) continue;
                LinearLayout card = card(Color.WHITE);
                TextView type = label(appointment.optString("appointment_type", "clinic").replace('_', ' ').toUpperCase(Locale.US), 11); type.setTextColor(BLUE); card.addView(type);
                card.addView(title(appointment.optString("title", "Doctor appointment"), 20), margin(-1,-2,0,8,0,5));
                card.addView(body(formatAppointment(appointment), 14));
                String notes = appointment.optString("notes", "");
                if (!notes.isEmpty()) card.addView(body(notes, 13), margin(-1,-2,0,10,0,0));
                page.addView(card, margin(-1,-2,0,0,0,12));
            }
        }
        setScrollable(page); addBottomNav("schedule");
    }

    private void showHistory() {
        LinearLayout page = page(true); addHeader(page, "Device Usage");
        page.addView(body("Vibration runs recorded by NeuroSense. These are not doctor appointments.", 15), margin(-1,-2,0,2,0,18));
        JSONArray usage = patientSession == null ? null : patientSession.deviceUsage;
        if (usage == null || usage.length() == 0) {
            page.addView(emptyCard("No device usage recorded", "Completed vibration runs will appear after the device or app synchronizes them."));
        } else {
            for (int index = 0; index < usage.length(); index++) {
                JSONObject run = usage.optJSONObject(index);
                if (run == null) continue;
                LinearLayout item = card(Color.WHITE);
                item.addView(title(formatIso(run.optString("started_at_utc")), 18));
                double measured = run.has("measured_hz") && !run.isNull("measured_hz") ? run.optDouble("measured_hz") : run.optDouble("estimated_hz", run.optDouble("requested_hz"));
                item.addView(body(formatHz(measured) + " Hz · " + Math.round(run.optInt("duration_seconds") / 60.0) + " min · " + run.optString("status", "recorded"), 14));
                page.addView(item, margin(-1,-2,0,0,0,10));
            }
        }
        setScrollable(page); addBottomNav("history");
    }

    private void showMore() {
        LinearLayout page = page(true); addHeader(page, "My NeuroVibe");
        LinearLayout profile = card(Color.WHITE); profile.addView(title(patientName, 22)); profile.addView(body("Patient ID · " + patientCode, 13)); page.addView(profile);
        page.addView(menuButton("NeuroSense device", patientSession == null || patientSession.assignedDeviceId == null ? "No database assignment" : (isDeviceReady() ? connectedName + " verified" : patientSession.assignedDeviceName + " · setup required"), this::showConnect), margin(-1,-2,0,16,0,0));
        page.addView(menuButton("Care plan", patientSession == null || patientSession.carePlanName == null ? "No active care plan" : patientSession.carePlanName + " · " + formatHz(patientSession.minHz) + "–" + formatHz(patientSession.maxHz) + " Hz", () -> carePlanDialog()), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Optional direct Wi-Fi", wifiOnline ? "Connected to " + savedWifiSsid : "The app can synchronize without device Wi-Fi", this::wifiDialog), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Help and safety", "Emergency guidance and support", this::helpDialog), margin(-1,-2,0,12,0,0));
        Button signOut = outline("Sign out"); signOut.setOnClickListener(v -> showSignOut()); page.addView(signOut, margin(-1,52,0,22,0,0));
        setScrollable(page); addBottomNav("more");
    }

    @SuppressLint("MissingPermission")
    private void showConnect() {
        boolean hasAssignment = patientSession != null && patientSession.assignedDeviceId != null;
        if (!hasAssignment && !identificationMode) { showAssignmentRequired(); return; }
        LinearLayout page = page(false);
        if (setupComplete) {
            Button back = textButton("‹ Back to dashboard");
            back.setOnClickListener(v -> showHome());
            page.addView(back, wrap());
        }
        TextView step = label("DEVICE SETUP  ·  PHASE 1 OF 2", 11); step.setTextColor(BLUE); page.addView(step);
        page.addView(title(hasAssignment ? "Connect your NeuroSense" : "Identify NeuroSense", 30), margin(-1,-2,0,12,0,8));
        page.addView(body("Keep the device powered and close to this phone. NeuroVibe creates a private Bluetooth link and installs the assignment issued by your doctor.", 15), margin(-1,-2,0,0,0,20));

        LinearLayout assignment = card(SKY);
        assignment.addView(label(hasAssignment ? "ASSIGNED BY YOUR DOCTOR" : "IDENTIFICATION MODE", 10));
        assignment.addView(title(hasAssignment ? patientSession.assignedDeviceId : "Unassigned device", 22), margin(-1,-2,0,7,0,3));
        assignment.addView(body(hasAssignment ? patientName + " · " + patientCode : "Motor control remains locked.", 13));
        page.addView(assignment, margin(-1,-2,0,0,0,16));

        page.addView(setupPhaseCard("1", "Bluetooth connection",
                connected ? "Connected to " + connectedName : "Find and connect the nearby NeuroSense",
                connected, true));
        page.addView(setupPhaseCard("2", "Secure device assignment",
                serverVerified ? "Assignment active for " + patientName : "Available after Bluetooth verification",
                serverVerified, connected && deviceVerified));

        Button scan = primary(connected ? "Find a different NeuroSense" : "Scan for NeuroSense");
        scan.setOnClickListener(v -> requestBleAndScan());
        page.addView(scan, margin(-1,54,0,18,0,12));
        deviceList = column(Gravity.CENTER_HORIZONTAL, 10); page.addView(deviceList);
        if (connected) {
            LinearLayout connectedCard = card(Color.WHITE);
            connectedCard.addView(title("Bluetooth link ready", 20));
            connectedCard.addView(body(deviceVerified
                    ? "Hardware " + hardwareDeviceId + " has responded. Secure assignment can now be installed."
                    : "Reading the device identity and connection state…", 13), margin(-1,-2,0,6,0,0));
            if (!deviceVerified) {
                ProgressBar progress = new ProgressBar(this);
                progress.setIndeterminate(true);
                connectedCard.addView(progress, margin(42,42,0,14,0,8));
                Button retryStatus = outline("Retry device handshake");
                retryStatus.setOnClickListener(v -> ble.sendType("get_status"));
                connectedCard.addView(retryStatus, margin(-1,48,0,4,0,8));
            } else if (!serverVerified) {
                Button provision = accent("Install doctor assignment");
                provision.setOnClickListener(v -> beginSecureProvisioning());
                connectedCard.addView(provision, margin(-1,52,0,16,0,8));
            } else {
                Button continueButton = accent("Continue to dashboard");
                continueButton.setOnClickListener(v -> showHome());
                connectedCard.addView(continueButton, margin(-1,52,0,16,0,8));
                Button wifi = outline(wifiOnline ? "Direct Wi-Fi settings" : "Add optional direct Wi-Fi");
                wifi.setOnClickListener(v -> showWifiSetup());
                connectedCard.addView(wifi, margin(-1,48,0,0,0,8));
            }
            Button disconnect = outline("Disconnect Bluetooth");
            disconnect.setOnClickListener(v -> { ble.disconnect(); connected = false; deviceVerified = false; showConnect(); });
            connectedCard.addView(disconnect, margin(-1,48,0,0,0,0));
            deviceList.addView(connectedCard);
        } else deviceList.addView(body("No scan is running. Tap “Scan for NeuroSense” to begin.", 14));
        setScrollable(page);
    }

    private void requestBleAndScan() {
        if (Build.VERSION.SDK_INT >= 31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 44); return;
        }
        beginScan();
    }

    private void requestAppPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 45); else postAppointmentReminder();
    }

    @SuppressLint("MissingPermission") private void beginScan() {
        scanResults.clear(); if (deviceList != null) { deviceList.removeAllViews(); deviceList.addView(body("Searching for NeuroSense devices…", 14)); } ble.startScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 44 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) beginScan();
        else if (requestCode == 44) toast("Bluetooth permission is required to control NeuroSense.");
        if (requestCode == 45) { toast("Permissions updated. You can change them later in Android Settings."); postAppointmentReminder(); }
    }

    private void startSession() {
        if (!isDeviceReady() || patientSession == null) { toast("Connect NeuroSense and install the active doctor assignment first."); showConnect(); return; }
        try { ble.send(new JSONObject().put("type", "start_session").put("target_hz", selectedHz).put("duration_seconds", selectedDurationMinutes * 60)); }
        catch (Exception error) { toast(error.getMessage()); }
        showActiveSession();
    }

    private void showActiveSession() {
        int sessionDuration = selectedDurationMinutes * 60;
        if (sessionDuration <= 0) { toast("The care plan duration is invalid."); showHome(); return; }
        if (sessionTimer != null) sessionTimer.cancel(); remainingSeconds = sessionDuration;
        LinearLayout page = page(false); addHeader(page, "Vibration Running");
        TextView status = label("● BLUETOOTH CONNECTED", 11); status.setTextColor(GREEN); status.setGravity(Gravity.CENTER); page.addView(status);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(sessionDuration); progress.setProgress(0); page.addView(progress, margin(-1,8,0,16,0,26));
        TextView timer = title(String.format(Locale.US, "%02d:%02d", sessionDuration / 60, sessionDuration % 60), 48); timer.setGravity(Gravity.CENTER); page.addView(timer);
        TextView frequency = title(selectedHz + "\nHz", 52); frequency.setGravity(Gravity.CENTER); frequency.setTextColor(NAVY); frequency.setBackground(round(Color.WHITE, 100)); page.addView(frequency, margin(dp(190),dp(190),0,22,0,24));
        int minimumHz = 1; int maximumHz = 230;
        LinearLayout controls = card(Color.WHITE); controls.addView(title("Frequency", 20)); controls.addView(body("Available range · 1–230 Hz", 12));
        SeekBar slider = new SeekBar(this); slider.setMax(Math.max(maximumHz - minimumHz, 0)); slider.setProgress(Math.max(0, selectedHz - minimumHz)); slider.setEnabled(patientSession.manualControlAllowed); controls.addView(slider, margin(-1,44,0,18,0,0));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) { selectedHz = value + minimumHz; frequency.setText(selectedHz + "\nHz"); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) { try { ble.send(new JSONObject().put("type", "set_frequency").put("hz", selectedHz)); } catch (Exception e) { toast(e.getMessage()); } }
        });
        Button end = outline("Stop vibration"); end.setOnClickListener(v -> confirmStop(false)); controls.addView(end, margin(-1,50,0,18,0,0)); page.addView(controls);
        Button emergency = danger("⚠  EMERGENCY STOP"); emergency.setOnClickListener(v -> confirmStop(true)); page.addView(emergency, margin(-1,58,0,22,0,8));
        TextView safety = body("Emergency stop immediately terminates all vibration output.", 12); safety.setGravity(Gravity.CENTER); page.addView(safety);
        setScrollable(page);
        sessionTimer = new CountDownTimer(sessionDuration * 1000L, 1000) {
            public void onTick(long millis) { remainingSeconds = (int)(millis / 1000); timer.setText(String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)); progress.setProgress(sessionDuration - remainingSeconds); }
            public void onFinish() {
                ble.sendType("stop_session");
                handler.postDelayed(() -> { if (ble.isConnected()) ble.sendType("get_pending"); }, 700);
                showCompletion(false);
            }
        }.start();
    }

    private void confirmStop(boolean emergency) {
        if (sessionTimer != null) sessionTimer.cancel();
        ble.sendType(emergency ? "emergency_stop" : "stop_session");
        handler.postDelayed(() -> { if (ble.isConnected()) ble.sendType("get_pending"); }, 700);
        showCompletion(true);
    }

    private void showCompletion(boolean stopped) {
        LinearLayout page = page(false); page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView check = title(stopped ? "!" : "✓", 54); check.setTextColor(Color.WHITE); check.setGravity(Gravity.CENTER); check.setBackground(round(stopped ? RED : GREEN, 70)); page.addView(check, margin(110,110,0,70,0,22));
        page.addView(title(stopped ? "Vibration stopped safely" : "Vibration complete", 28)); TextView text = body(stopped ? "The device has stopped and the partial usage record will synchronize." : "Your prescribed vibration cycle finished successfully.", 15); text.setGravity(Gravity.CENTER); page.addView(text, margin(-1,-2,18,12,18,28));
        Button home = primary("Return to Dashboard"); home.setOnClickListener(v -> showHome()); page.addView(home, margin(-1,54,0,0,0,12));
        Button symptoms = outline("Log how I feel"); symptoms.setOnClickListener(v -> symptomDialog()); page.addView(symptoms, margin(-1,54,0,0,0,0)); setScrollable(page);
    }

    @SuppressLint("MissingPermission") @Override public void onScanResult(BluetoothDevice device, int rssi) {
        String key = device.getAddress(); if (scanResults.containsKey(key)) return; scanResults.put(key, device);
        if (deviceList == null) return; if (scanResults.size() == 1) deviceList.removeAllViews();
        String name = device.getName() == null ? "NeuroSense" : device.getName();
        Button item = outline("◉  " + name + "\n     Signal " + rssi + " dBm  ·  Tap to connect");
        item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> { toast("Connecting to " + name + "…"); ble.connect(device); });
        deviceList.addView(item, margin(-1,68,0,0,0,10));
    }

    @Override public void onConnectionChanged(boolean isConnected, String name) {
        connected = isConnected;
        connectedName = isConnected ? name : "Not connected";
        if (!isConnected) { deviceVerified = false; setupInProgress = false; wifiAttemptPending = false; }
        toast(isConnected ? "Phase 1 complete: Bluetooth connected." : name);
        if (isConnected) { showConnect(); ble.sendType("get_status"); }
    }

    @Override public void onMessage(String json) {
        try {
            JSONObject message = new JSONObject(json);
            String type = message.optString("type");
            if ("error".equals(type)) {
                setupInProgress = false;
                if (wifiAttemptPending || "set_wifi".equals(message.optString("command"))) {
                    wifiAttemptPending = false;
                    lastWifiDiagnostic = message.optString("code", "device_rejected_wifi");
                    showWifiResult(false, message);
                } else toast(message.optString("message", "Device rejected the command."));
                return;
            }
            if ("status".equals(type)) { handleDeviceStatus(message); return; }
            if ("ok".equals(type)) { handleDeviceCommandAccepted(message.optString("command")); return; }
            if ("wifi_result".equals(type)) {
                boolean online = message.optBoolean("connected", false);
                boolean verified = message.optBoolean("server_verified", false);
                wifiOnline = online;
                serverVerified = verified;
                lastWifiDiagnostic = nullableString(message, "diagnostic", "");
                wifiAttemptPending = false;
                setupInProgress = false;
                syncMessage = online ? "Wi-Fi connected" : "Wi-Fi unable to connect";
                showWifiResult(online, message);
                return;
            }
            if ("sync_result".equals(type)) { pendingRecords = message.optInt("pending_sessions", pendingRecords); syncMessage = message.optBoolean("uploaded", false) ? "Latest usage uploaded successfully" : "No upload completed; records remain safely queued"; showHome(); return; }
            if (message.has("session_id") && message.has("device_id") && !message.has("command")) { relayUsageRecord(message); return; }
        }
        catch (Exception ignored) { }
    }
    @Override public void onError(String message) {
        if (wifiAttemptPending) {
            wifiAttemptPending = false;
            lastWifiDiagnostic = message;
            try { showWifiResult(false, new JSONObject().put("diagnostic", message)); }
            catch (Exception ignored) { toast(message); }
        } else toast(message);
    }

    private void handleDeviceStatus(JSONObject status) {
        physicalDeviceId = nullableString(status, "device_id", "");
        hardwareDeviceId = nullableString(status, "hardware_id", "Unknown hardware");
        factoryDeviceEmpty = physicalDeviceId.isEmpty();
        savedWifiSsid = nullableString(status, "wifi_ssid", "");
        wifiOnline = status.optBoolean("wifi_connected", false);
        serverVerified = status.optBoolean("server_verified", false) &&
                status.optBoolean("assignment_lease_active", false);
        long leaseValidUntil = status.optLong("assignment_valid_until_epoch", 0);
        boolean leaseNeedsRefresh = serverVerified && leaseValidUntil > 0 &&
                leaseValidUntil - System.currentTimeMillis() / 1000L < 2 * 24 * 60 * 60L;
        pendingRecords = status.optInt("pending_sessions", 0);
        if (patientSession == null || patientSession.assignedDeviceId == null) {
            identificationMode = true;
            setupInProgress = false;
            toast("Device identified: " + physicalDeviceId);
            showConnect();
            return;
        }
        if (!factoryDeviceEmpty && !patientSession.assignedDeviceId.equalsIgnoreCase(physicalDeviceId)) {
            deviceVerified = false;
            if (wasPreviouslyAssignedToPatient(physicalDeviceId)) showPreviousDeviceSync();
            else showWrongDevice();
            return;
        }
        deviceVerified = true;
        if (!factoryDeviceEmpty && serverVerified && !leaseNeedsRefresh) {
            setupComplete = true;
            preferences.edit()
                    .putString("enrolled_device_id", patientSession.assignedDeviceId)
                    .putString("enrolled_assignment_id", patientSession.assignmentId)
                    .apply();
            syncMessage = wifiOnline ? "Device and assignment verified online" : "Bluetooth relay ready; direct device Wi-Fi is optional";
            ble.sendType("get_pending"); showHome(); return;
        }
        if (!factoryDeviceEmpty && serverVerified && leaseNeedsRefresh) {
            setupInProgress = true;
            syncMessage = "Renewing the device assignment lease through this app";
            requestSecureProvisioning();
            return;
        }
        if (setupComplete || setupInProgress) return;
        setupInProgress = true;
        boolean apiConfigured = status.optBoolean("api_configured", false);
        boolean enrolledLocally = patientSession.assignedDeviceId.equals(preferences.getString("enrolled_device_id", "")) &&
                patientSession.assignmentId.equals(preferences.getString("enrolled_assignment_id", ""));
        if (factoryDeviceEmpty || !apiConfigured || !enrolledLocally || !serverVerified) requestSecureProvisioning();
        else sendAssignmentConfiguration();
    }

    private String nullableString(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) return fallback;
        String value = object.optString(key, fallback);
        return "null".equalsIgnoreCase(value.trim()) ? fallback : value.trim();
    }

    private boolean wasPreviouslyAssignedToPatient(String deviceId) {
        if (patientSession == null || deviceId == null || deviceId.isEmpty()) return false;
        JSONArray history = patientSession.assignmentHistory;
        for (int index = 0; index < history.length(); index++) {
            JSONObject assignment = history.optJSONObject(index);
            if (assignment != null && "closed".equals(assignment.optString("status")) &&
                    deviceId.equalsIgnoreCase(assignment.optString("device_id"))) return true;
        }
        return false;
    }

    private void showPreviousDeviceSync() {
        LinearLayout page = page(false);
        TextView badge = label("PREVIOUSLY ASSIGNED DEVICE", 10); badge.setTextColor(BLUE); page.addView(badge);
        page.addView(title("Recover stored usage only", 28), margin(-1,-2,0,12,0,8));
        page.addView(body("This is " + physicalDeviceId + ", a previous NeuroSense for " + patientName +
                ". Motor control is locked, but usage recorded before replacement can still be transferred safely.", 15),
                margin(-1,-2,0,0,0,20));
        LinearLayout status = card(SKY);
        status.addView(title(pendingRecords + " stored record" + (pendingRecords == 1 ? "" : "s"), 21));
        status.addView(body("Records remain linked to their original device and assignment.", 13));
        page.addView(status);
        Button transfer = primary("Transfer stored records");
        transfer.setEnabled(pendingRecords > 0);
        transfer.setOnClickListener(v -> {
            ble.sendType("get_pending");
            toast("Stored records are transferring. Motor control remains disabled.");
        });
        page.addView(transfer, margin(-1,54,0,18,0,10));
        Button disconnect = outline("Disconnect previous device");
        disconnect.setOnClickListener(v -> { ble.disconnect(); showConnect(); });
        page.addView(disconnect, margin(-1,52,0,0,0,0));
        setScrollable(page);
    }

    private void requestSecureProvisioning() {
        authClient.provisionDevice(patientSession, patientSession.assignedDeviceId, hardwareDeviceId, new SupabaseAuthClient.ProvisioningCallback() {
            public void onSuccess(SupabaseAuthClient.DeviceProvisioning provisioning) { runOnUiThread(() -> {
                pendingProvisioning = provisioning;
                try { if (factoryDeviceEmpty) ble.send(new JSONObject().put("type", "set_identity").put("device_id", patientSession.assignedDeviceId)); else sendServerConfiguration(); }
                catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
            }); }
            public void onError(String message) { runOnUiThread(() -> {
                setupInProgress = false;
                toast(message);
                if (serverVerified) showHome(); else showConnect();
            }); }
        });
    }

    private void handleDeviceCommandAccepted(String command) {
        if ("set_identity".equals(command)) { physicalDeviceId = patientSession.assignedDeviceId; factoryDeviceEmpty = false; sendServerConfiguration(); return; }
        if ("set_server".equals(command)) { sendAssignmentConfiguration(); return; }
        if ("set_assignment".equals(command)) { sendCarePlanConfiguration(); return; }
        if ("set_limits".equals(command)) { ble.sendType("activate_assignment"); return; }
        if ("activate_assignment".equals(command)) { finishSecureConfiguration(); return; }
        if ("set_wifi".equals(command)) { toast("Wi-Fi saved. NeuroSense is connecting…"); return; }
    }

    private void sendServerConfiguration() {
        if (pendingProvisioning == null) { setupInProgress = false; toast("Provisioning credentials are unavailable. Retry device setup."); return; }
        try { ble.send(new JSONObject().put("type", "set_server").put("api_base_url", pendingProvisioning.apiBaseUrl).put("api_token", pendingProvisioning.apiToken)); }
        catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void sendAssignmentConfiguration() {
        try {
            long validUntil = pendingProvisioning != null
                    ? pendingProvisioning.assignmentValidUntilEpoch
                    : patientSession.assignmentValidUntilEpoch;
            long serverTime = pendingProvisioning != null
                    ? pendingProvisioning.serverTimeEpoch
                    : System.currentTimeMillis() / 1000L;
            ble.send(new JSONObject()
                    .put("type", "set_assignment")
                    .put("patient_id", patientSession.patientId)
                    .put("assignment_id", patientSession.assignmentId)
                    .put("assignment_valid_until_epoch", validUntil)
                    .put("server_time_epoch", serverTime));
        }
        catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void sendCarePlanConfiguration() {
        try {
            ble.send(new JSONObject().put("type", "set_limits")
                    .put("min_hz", 0).put("target_hz", Math.max(1, selectedHz)).put("max_hz", 230)
                    .put("max_duration_seconds", 90 * 60).put("manual_control_allowed", true));
        } catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void finishSecureConfiguration() {
        setupInProgress = false;
        setupComplete = true;
        serverVerified = true;
        preferences.edit()
                .putString("enrolled_device_id", patientSession.assignedDeviceId)
                .putString("enrolled_assignment_id", patientSession.assignmentId)
                .apply();
        syncMessage = wifiOnline ? "Assignment active and direct Wi-Fi connected" : "Assignment active; usage will synchronize through this app";
        ble.sendType("get_pending");
        showAssignmentReady();
    }

    private void beginSecureProvisioning() {
        if (!connected || !deviceVerified) { showConnect(); return; }
        setupInProgress = true;
        LinearLayout page = page(false);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        page.addView(progress, margin(72,72,0,80,0,22));
        TextView heading = title("Finishing secure setup", 27); heading.setGravity(Gravity.CENTER); page.addView(heading);
        TextView copy = body("NeuroVibe is securely applying the doctor-assigned identity, renewable assignment and care limits. Device Wi-Fi is not required.", 15);
        copy.setGravity(Gravity.CENTER); page.addView(copy, margin(-1,-2,12,10,12,22));
        page.addView(setupPhaseCard("1", "Bluetooth connection", connectedName + " connected", true, true));
        page.addView(setupPhaseCard("2", "Secure device assignment", "Downloading from the NeuroVibe server", false, true));
        setScrollable(page);
        requestSecureProvisioning();
    }

    private void showAssignmentReady() {
        LinearLayout page = page(false);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView symbol = title("✓", 48);
        symbol.setTextColor(Color.WHITE);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(GREEN, 64));
        page.addView(symbol, margin(96,96,0,58,0,20));
        TextView heading = title("NeuroSense is ready", 29); heading.setGravity(Gravity.CENTER); page.addView(heading);
        TextView copy = body("The doctor assignment is active. You can use the device through Bluetooth and NeuroVibe will relay records to the server.", 15);
        copy.setGravity(Gravity.CENTER); page.addView(copy, margin(-1,-2,12,10,12,24));
        Button dashboard = primary("Open dashboard");
        dashboard.setOnClickListener(v -> showHome());
        page.addView(dashboard, margin(-1,54,0,0,0,10));
        Button wifi = outline("Add optional direct Wi-Fi");
        wifi.setOnClickListener(v -> showWifiSetup());
        page.addView(wifi, margin(-1,52,0,0,0,0));
        setScrollable(page);
    }

    private void wifiDialog() {
        showWifiSetup();
    }

    private void showWifiSetup() {
        if (!connected || !deviceVerified) { showConnect(); return; }
        LinearLayout page = page(false);
        TextView step = label("OPTIONAL DIRECT SYNCHRONIZATION", 11); step.setTextColor(BLUE); page.addView(step);
        page.addView(title("Connect NeuroSense to Wi-Fi", 30), margin(-1,-2,0,12,0,8));
        page.addView(body("NeuroSense already works through Bluetooth. Add Wi-Fi only if you also want the device to upload records directly without the app.", 15), margin(-1,-2,0,0,0,20));
        page.addView(setupPhaseCard("1", "Bluetooth connection", connectedName + " connected", true, true));
        page.addView(setupPhaseCard("2", "Doctor assignment", "Active and ready", serverVerified, true));

        LinearLayout form = card(Color.WHITE);
        TextView formLabel = label("NETWORK DETAILS", 10); formLabel.setTextColor(BLUE); form.addView(formLabel);
        EditText ssid = input("Wi-Fi network name (SSID)");
        ssid.setText(savedWifiSsid);
        form.addView(ssid, margin(-1,56,0,12,0,12));
        EditText password = input("Wi-Fi password");
        password.setInputType(0x81);
        form.addView(password, margin(-1,56,0,0,0,8));
        form.addView(body("Use the exact uppercase/lowercase spelling. For an iPhone hotspot, enable “Maximize Compatibility”.", 12));
        page.addView(form, margin(-1,-2,0,16,0,16));

        Button connectButton = primary("Send details and connect");
        connectButton.setOnClickListener(v -> {
            String network = ssid.getText().toString().trim();
            if (network.isEmpty()) { toast("Enter the Wi-Fi network name."); return; }
            String wifiPassword = password.getText().toString();
            if (!wifiPassword.isEmpty() && wifiPassword.length() < 8) { toast("Wi-Fi passwords normally contain at least 8 characters."); return; }
            connectButton.setEnabled(false);
            savedWifiSsid = network;
            preferences.edit().putString("last_wifi_ssid", network).apply();
            wifiAttemptPending = true;
            showWifiConnecting();
            try {
                ble.send(new JSONObject().put("type","set_wifi").put("ssid",network).put("password",wifiPassword));
            } catch(Exception error) {
                wifiAttemptPending = false;
                toast(error.getMessage());
                showWifiSetup();
            }
        });
        page.addView(connectButton, margin(-1,54,0,0,0,12));
        Button back = textButton("Skip · Continue without device Wi-Fi");
        back.setOnClickListener(v -> { if (serverVerified) showHome(); else showConnect(); });
        page.addView(back);
        setScrollable(page);
    }

    private void showWifiConnecting() {
        LinearLayout page = page(false);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        page.addView(progress, margin(78,78,0,72,0,22));
        TextView heading = title("Connecting to " + savedWifiSsid, 27); heading.setGravity(Gravity.CENTER); page.addView(heading);
        TextView copy = body("NeuroSense is scanning and authenticating. Keep Bluetooth connected; this can take up to 30 seconds.", 15);
        copy.setGravity(Gravity.CENTER); page.addView(copy, margin(-1,-2,10,10,10,24));
        LinearLayout notice = card(SKY);
        notice.addView(label("PHASE 2 IN PROGRESS", 10));
        notice.addView(body("The ESP32-C3 will report either “Wi-Fi connected” or “Wi-Fi unable to connect”.", 14), margin(-1,-2,0,6,0,0));
        page.addView(notice);
        Button cancel = outline("Cancel and disconnect");
        cancel.setOnClickListener(v -> { wifiAttemptPending = false; ble.disconnect(); showConnect(); });
        page.addView(cancel, margin(-1,52,0,22,0,0));
        setScrollable(page);
    }

    private void showWifiResult(boolean connectedToWifi, JSONObject result) {
        LinearLayout page = page(false);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView symbol = title(connectedToWifi ? "✓" : "!", 48);
        symbol.setTextColor(Color.WHITE);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(connectedToWifi ? GREEN : RED, 64));
        page.addView(symbol, margin(96,96,0,62,0,20));

        TextView heading = title(connectedToWifi ? "Wi-Fi connected" : "Wi-Fi unable to connect", 28);
        heading.setGravity(Gravity.CENTER); page.addView(heading);
        String ssid = nullableString(result, "ssid", savedWifiSsid);
        String detail = connectedToWifi
                ? "NeuroSense joined " + ssid + ". The network has been saved and will be reused after restart."
                : wifiFailureMessage(result);
        TextView copy = body(detail, 15); copy.setGravity(Gravity.CENTER);
        page.addView(copy, margin(-1,-2,12,10,12,24));

        page.addView(setupPhaseCard("1", "Bluetooth connection", connectedName + " connected", true, true));
        page.addView(setupPhaseCard("2", "Optional direct Wi-Fi",
                connectedToWifi ? ssid + " connected" : "Connection failed — details can be corrected",
                connectedToWifi, true));

        if (connectedToWifi && serverVerified) {
            setupComplete = true;
            preferences.edit()
                    .putString("enrolled_device_id", patientSession.assignedDeviceId)
                    .putString("enrolled_assignment_id", patientSession.assignmentId)
                    .apply();
            ble.sendType("sync_now");
            ble.sendType("get_pending");
            Button done = primary("Open NeuroVibe dashboard");
            done.setOnClickListener(v -> showHome());
            page.addView(done, margin(-1,54,0,22,0,0));
        } else if (connectedToWifi) {
            Button next = primary("Return to device setup");
            next.setOnClickListener(v -> showConnect());
            page.addView(next, margin(-1,54,0,22,0,8));
        } else {
            Button retry = primary("Check details and retry");
            retry.setOnClickListener(v -> showWifiSetup());
            page.addView(retry, margin(-1,54,0,22,0,8));
            Button bluetooth = textButton(serverVerified ? "Continue without device Wi-Fi" : "Back to Bluetooth");
            bluetooth.setOnClickListener(v -> { if (serverVerified) showHome(); else showConnect(); });
            page.addView(bluetooth);
        }
        setScrollable(page);
    }

    private String wifiFailureMessage(JSONObject result) {
        String reason = nullableString(result, "disconnect_reason_name", lastWifiDiagnostic);
        if (reason.contains("password") || reason.contains("authentication") || reason.contains("handshake"))
            return "NeuroSense found " + savedWifiSsid + " but authentication failed. Check the password exactly and retry.";
        if (reason.contains("not_found") || reason.contains("not_visible") || reason.contains("access_point"))
            return "NeuroSense could not see " + savedWifiSsid + ". Confirm the name and enable a 2.4 GHz network or hotspot compatibility mode.";
        if (reason.contains("weak") || reason.contains("beacon"))
            return "The Wi-Fi signal was lost. Move NeuroSense closer to the router or phone and retry.";
        return "NeuroSense could not join " + savedWifiSsid + ". Check the network name, password, and 2.4 GHz availability.";
    }

    private void relayUsageRecord(JSONObject record) {
        if (patientSession == null) return;
        saveLocalRecord(record);
        uploadLocalRecord(record);
    }

    private void uploadLocalRecord(JSONObject record) {
        syncMessage = "Uploading a queued device record through this phone…";
        authClient.uploadRelayedUsage(patientSession, record, new SupabaseAuthClient.SyncCallback() {
            public void onSuccess(String sessionId) { runOnUiThread(() -> { removeLocalRecord(sessionId); try { if (ble.isConnected()) ble.send(new JSONObject().put("type", "ack_session").put("session_id", sessionId)); } catch (Exception ignored) {} pendingRecords = Math.max(0, pendingRecords - 1); syncMessage = "Usage synchronized through the app"; retryLocalRecords(); }); }
            public void onError(String message) { runOnUiThread(() -> { syncMessage = "Saved on device; upload will retry when internet is available"; toast(message); }); }
        });
    }

    private synchronized void saveLocalRecord(JSONObject record) {
        try {
            JSONArray queue = new JSONArray(preferences.getString("relay_queue", "[]"));
            String id = record.optString("session_id");
            for (int index = 0; index < queue.length(); index++) { JSONObject item = queue.optJSONObject(index); if (item != null && id.equals(item.optString("session_id"))) return; }
            queue.put(record); preferences.edit().putString("relay_queue", queue.toString()).apply();
        } catch (Exception error) { toast("Could not save the local sync copy. The ESP still retains the original record."); }
    }

    private synchronized void removeLocalRecord(String sessionId) {
        try {
            JSONArray source = new JSONArray(preferences.getString("relay_queue", "[]")); JSONArray kept = new JSONArray();
            for (int index = 0; index < source.length(); index++) { JSONObject item = source.optJSONObject(index); if (item != null && !sessionId.equals(item.optString("session_id"))) kept.put(item); }
            preferences.edit().putString("relay_queue", kept.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void retryLocalRecords() {
        if (patientSession == null) return;
        try { JSONArray queue = new JSONArray(preferences.getString("relay_queue", "[]")); JSONObject first = queue.optJSONObject(0); if (first != null) uploadLocalRecord(first); }
        catch (Exception ignored) { }
    }

    private void symptomDialog() { showSimplePage("How are you feeling?", "Choose one option. This prototype keeps the note on this phone until symptom sync is enabled.", new String[]{"Comfortable","Tingling","Tired","Dizzy","Pain or discomfort"}); }
    private void carePlanDialog() {
        showSimplePage("Vibration controls", "You may choose 1–230 Hz and 1–90 minutes. Setting frequency to 0 or pressing Stop ends vibration immediately.", new String[]{});
    }
    private void helpDialog() { showSimplePage("Help and safety", "Use only your assigned NeuroSense. Stop immediately if you feel pain, dizziness, numbness, or unusual discomfort. Contact your care team for clinical questions.", new String[]{}); }

    private void showWrongDevice() {
        LinearLayout page = page(false); TextView warning = label("DEVICE MISMATCH", 11); warning.setTextColor(RED); page.addView(warning);
        page.addView(title("This device is not assigned to you", 28), margin(-1,-2,0,12,0,8));
        page.addView(body("Connected: " + physicalDeviceId + "\nAssigned by doctor: " + patientSession.assignedDeviceId + "\n\nMotor control is locked.", 15), margin(-1,-2,0,0,0,24));
        Button disconnect = danger("Disconnect wrong device"); disconnect.setOnClickListener(v -> { ble.disconnect(); showConnect(); }); page.addView(disconnect, margin(-1,54,0,0,0,12));
        setScrollable(page);
    }

    private void showSignOut() {
        LinearLayout page = page(false); page.addView(title("Sign out of NeuroVibe?", 28));
        page.addView(body("Your private login will be removed from this tablet. The ESP32 Wi-Fi configuration and stored usage records will not be erased.", 15), margin(-1,-2,0,12,0,24));
        Button cancel = primary("Stay signed in"); cancel.setOnClickListener(v -> showMore()); page.addView(cancel, margin(-1,54,0,0,0,12));
        Button signOut = outline("Sign out"); signOut.setOnClickListener(v -> { if (sessionTimer != null) sessionTimer.cancel(); ble.disconnect(); patientSession = null; preferences.edit().remove("refresh_token").apply(); showWelcome(); }); page.addView(signOut, margin(-1,54,0,0,0,0));
        setScrollable(page);
    }

    private void showSimplePage(String heading, String message, String[] options) {
        LinearLayout page = page(false); Button back = textButton("‹ Back"); back.setOnClickListener(v -> { if (patientSession == null) showWelcome(); else showMore(); }); page.addView(back, wrap());
        page.addView(title(heading, 28), margin(-1,-2,0,18,0,8)); page.addView(body(message, 15), margin(-1,-2,0,0,0,20));
        for (String option : options) { Button item = outline(option); item.setOnClickListener(v -> { toast(option + " saved."); showHome(); }); page.addView(item, margin(-1,52,0,0,0,10)); }
        Button done = primary("Done"); done.setOnClickListener(v -> { if (patientSession == null) showWelcome(); else showMore(); }); page.addView(done, margin(-1,52,0,14,0,0)); setScrollable(page);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("appointment_reminders", "Doctor appointment reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts for clinic, video, phone, and home-visit appointments");
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission") private void postAppointmentReminder() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        JSONObject appointment = nextAppointment();
        if (appointment == null) return;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "appointment_reminders")
                .setSmallIcon(io.alphaion.neurovibe.R.drawable.ic_neurovibe)
                .setContentTitle(appointment.optString("title", "Upcoming doctor appointment"))
                .setContentText(formatAppointment(appointment))
                .setContentIntent(pending).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(1001, notification);
    }

    private void addHeader(LinearLayout page, String heading) {
        LinearLayout header = row(); TextView brand = brandTitle(21); header.addView(brand, weight()); TextView badge = label(isDeviceReady() ? "● VERIFIED" : "○ NOT READY", 10); badge.setTextColor(isDeviceReady() ? GREEN : MUTED); badge.setGravity(Gravity.CENTER); header.addView(badge, new LinearLayout.LayoutParams(dp(105), dp(40))); page.addView(header); page.addView(title(heading, 28), margin(-1,-2,0,24,0,0));
    }

    private void addBottomNav(String active) {
        LinearLayout nav = row(); nav.setPadding(dp(10), dp(7), dp(10), dp(7)); nav.setBackground(round(Color.WHITE, 18));
        nav.addView(navButton("⌂\nHome", active.equals("home"), this::showHome), weight()); nav.addView(navButton("□\nAppointments", active.equals("schedule"), this::showSchedule), weight()); nav.addView(navButton("◷\nUsage", active.equals("history"), this::showHistory), weight()); nav.addView(navButton("•••\nMore", active.equals("more"), this::showMore), weight());
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM); np.setMargins(dp(12),0,dp(12),dp(10)); root.addView(nav,np);
    }

    private JSONObject nextAppointment() {
        JSONArray values = patientSession == null ? null : patientSession.appointments;
        if (values == null) return null;
        long now = System.currentTimeMillis();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            Date date = parseIso(value.optString("scheduled_for"));
            if (date != null && date.getTime() >= now) return value;
        }
        return null;
    }

    private String formatAppointment(JSONObject appointment) {
        String date = formatIso(appointment.optString("scheduled_for"));
        int minutes = appointment.optInt("duration_minutes", 30);
        String location = appointment.optString("location", "");
        return date + " · " + minutes + " min" + (location.isEmpty() ? "" : "\n" + location);
    }

    private Date parseIso(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String pattern : patterns) {
            try { SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US); parser.setTimeZone(TimeZone.getTimeZone("UTC")); return parser.parse(value); }
            catch (Exception ignored) { }
        }
        return null;
    }

    private String formatIso(String value) {
        Date date = parseIso(value);
        if (date == null) return "Date unavailable";
        return new SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault()).format(date);
    }

    private View emptyCard(String heading, String message) {
        LinearLayout view = card(Color.WHITE);
        view.addView(title(heading, 19));
        view.addView(body(message, 14), margin(-1,-2,0,7,0,0));
        return view;
    }
    private View historyCard(String when,String hz,String duration,String status){LinearLayout c=card(Color.WHITE);LinearLayout r=row();LinearLayout info=column(Gravity.START,3);info.addView(title(when,16));info.addView(body(hz+" · "+duration,13));r.addView(info,weight());TextView s=label(status,10);s.setTextColor(status.equals("Completed")?GREEN:RED);r.addView(s);c.addView(r);return c;}
    private View metric(String value,String name,String icon){LinearLayout c=card(Color.WHITE);TextView i=title(icon,20);i.setTextColor(BLUE);c.addView(i);c.addView(title(value,26));c.addView(body(name,12));return c;}
    private View setupPhaseCard(String number, String heading, String message, boolean complete, boolean enabled) {
        LinearLayout card = card(complete ? Color.rgb(232, 247, 240) : enabled ? Color.WHITE : Color.rgb(239, 244, 247));
        LinearLayout line = row();
        TextView badge = title(complete ? "✓" : number, 17);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(round(complete ? GREEN : enabled ? BLUE : Color.rgb(142, 154, 164), 30));
        line.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = column(Gravity.START, 2);
        copy.addView(title(heading, 17));
        copy.addView(body(message, 12));
        LinearLayout.LayoutParams copyParams = weight();
        copyParams.leftMargin = dp(13);
        line.addView(copy, copyParams);
        TextView state = label(complete ? "DONE" : enabled ? "READY" : "NEXT", 9);
        state.setTextColor(complete ? GREEN : enabled ? BLUE : MUTED);
        line.addView(state);
        card.addView(line);
        LinearLayout.LayoutParams cardParams = margin(-1,-2,0,0,0,10);
        card.setLayoutParams(cardParams);
        return card;
    }
    private Button menuButton(String name,String sub,Runnable action){Button b=outline(name+"\n"+sub+"   ›");b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setAllCaps(false);b.setOnClickListener(v->action.run());b.setMinHeight(dp(68));return b;}
    private Button navButton(String text,boolean active,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(active?Color.WHITE:MUTED);b.setBackground(round(active?NAVY_CARD:Color.TRANSPARENT,14));b.setOnClickListener(v->action.run());return b;}
    private LinearLayout page(boolean bottomPadding){LinearLayout p=column(Gravity.TOP,0);p.setPadding(dp(20),dp(18),dp(20),dp(bottomPadding?98:30));return p;}
    private LinearLayout card(int color){LinearLayout c=column(Gravity.TOP,5);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(color,18));return c;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout column(int gravity,int gap){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(gravity);if(Build.VERSION.SDK_INT>=29)l.setDividerPadding(gap);return l;}
    private TextView title(String text,int sp){TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(NAVY);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView brandTitle(int sp){TextView v=title("NeuroVibe",sp);Drawable icon=getDrawable(io.alphaion.neurovibe.R.drawable.ic_neurovibe);icon.setBounds(0,0,dp(sp+8),dp(sp+8));v.setCompoundDrawables(icon,null,null,null);v.setCompoundDrawablePadding(dp(9));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView body(String text,int sp){TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(MUTED);v.setLineSpacing(0,1.15f);return v;}
    private TextView label(String text,int sp){TextView v=body(text,sp);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setLetterSpacing(.07f);return v;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(15);e.setSingleLine(true);e.setPadding(dp(15),0,dp(15),0);e.setBackground(round(Color.WHITE,12));return e;}
    private Button primary(String text){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(NAVY_CARD,14));return b;}
    private Button outline(String text){Button b=primary(text);b.setTextColor(BLUE);b.setBackground(stroke(Color.WHITE,BLUE,14));return b;}
    private Button accent(String text){Button b=primary(text);b.setTextColor(NAVY);b.setBackground(round(Color.rgb(142,205,255),14));return b;}
    private Button textButton(String text){Button b=outline(text);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private Button danger(String text){Button b=primary(text);b.setBackground(round(RED,14));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable stroke(int color,int line,int radius){GradientDrawable d=round(color,radius);d.setStroke(dp(1),line);return d;}
    private LinearLayout.LayoutParams margin(int width,int height,int left,int top,int right,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));p.setMargins(dp(left),dp(top),dp(right),dp(bottom));return p;}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-2,-2);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private LinearLayout.LayoutParams weightWithLeft(){LinearLayout.LayoutParams p=weight();p.leftMargin=dp(10);return p;}
    private FrameLayout.LayoutParams match(){return new FrameLayout.LayoutParams(-1,-1);}
    private void setScrollable(LinearLayout page){root.removeAllViews();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,match());}
    private String formatHz(double value){return Math.abs(value-Math.rint(value))<0.001?String.valueOf((int)Math.rint(value)):String.format(Locale.US,"%.1f",value);}
    private boolean isDeviceReady(){return connected && deviceVerified && serverVerified;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){if(sessionTimer!=null)sessionTimer.cancel();if(authClient!=null)authClient.close();ble.disconnect();super.onDestroy();}
}
