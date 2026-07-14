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
    private String physicalDeviceId;
    private SupabaseAuthClient.PatientSession patientSession;
    private SupabaseAuthClient.DeviceProvisioning pendingProvisioning;
    private boolean setupInProgress;
    private boolean setupComplete;
    private boolean wifiPromptShown;
    private String savedWifiSsid = "";
    private boolean wifiOnline;
    private int pendingRecords;
    private String syncMessage = "Waiting for device";
    private boolean identificationMode;
    private CountDownTimer sessionTimer;
    private int selectedHz = 85;
    private int remainingSeconds = 600;
    private int selectedDurationMinutes = 15;
    private String patientName = "Patient";
    private String patientCode = "Not linked";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(PALE); window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root = new FrameLayout(this); root.setBackgroundColor(PALE); setContentView(root);
        ble = new NeuroSenseBleManager(this, this);
        authClient = new SupabaseAuthClient();
        preferences = getSharedPreferences("neurovibe_private", MODE_PRIVATE);
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
        else if (!patientSession.assignedDeviceId.equals(preferences.getString("enrolled_device_id", ""))) showDeviceIdSetup();
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
        EditText deviceId = input("Device ID (DEV-NEUROSENSE-XXXXXX)"); page.addView(deviceId, margin(-1,54,0,20,0,12));
        page.addView(body("NeuroVibe continues only when this ID matches the device assigned by your doctor.", 13), margin(-1,-2,0,0,0,20));
        Button verify = primary("Verify Device ID"); verify.setOnClickListener(v -> {
            String entered = deviceId.getText().toString().trim();
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
        Button start = accent(deviceVerified ? "Start vibration" : "Connect assigned device"); start.setOnClickListener(v -> { if (deviceVerified) startSession(); else showConnect(); }); control.addView(start, margin(-1,50,0,16,0,0)); page.addView(control);
        connectionLabel = label(deviceVerified ? "●  " + connectedName + " connected" : "○  Assigned device disconnected", 12); connectionLabel.setTextColor(deviceVerified ? GREEN : MUTED); connectionLabel.setOnClickListener(v -> showConnect()); page.addView(connectionLabel, margin(-1,-2,2,12,0,16));
        LinearLayout sync = card(Color.WHITE); sync.addView(title("Data synchronization", 18)); sync.addView(body((wifiOnline ? "Wi-Fi connected" : "Wi-Fi offline") + " · " + pendingRecords + " pending\n" + syncMessage, 13)); page.addView(sync);
        LinearLayout actions = row(); Button device = outline(deviceVerified ? "Device settings" : "Connect device"); device.setOnClickListener(v -> showConnect()); actions.addView(device, weight()); Button usage = outline("View usage"); usage.setOnClickListener(v -> showHistory()); actions.addView(usage, weightWithLeft()); page.addView(actions, margin(-1,54,0,18,0,0));
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
        page.addView(menuButton("NeuroSense device", patientSession == null || patientSession.assignedDeviceId == null ? "No database assignment" : (deviceVerified ? connectedName + " verified" : patientSession.assignedDeviceName + " · setup required"), this::showConnect), margin(-1,-2,0,16,0,0));
        page.addView(menuButton("Care plan", patientSession == null || patientSession.carePlanName == null ? "No active care plan" : patientSession.carePlanName + " · " + formatHz(patientSession.minHz) + "–" + formatHz(patientSession.maxHz) + " Hz", () -> carePlanDialog()), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Wi-Fi setup", "Send Wi-Fi details securely over Bluetooth", this::wifiDialog), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Help and safety", "Emergency guidance and support", this::helpDialog), margin(-1,-2,0,12,0,0));
        Button signOut = outline("Sign out"); signOut.setOnClickListener(v -> showSignOut()); page.addView(signOut, margin(-1,52,0,22,0,0));
        setScrollable(page); addBottomNav("more");
    }

    @SuppressLint("MissingPermission")
    private void showConnect() {
        boolean hasAssignment = patientSession != null && patientSession.assignedDeviceId != null;
        if (!hasAssignment && !identificationMode) { showAssignmentRequired(); return; }
        LinearLayout page = page(false);
        if (deviceVerified) { Button back = textButton("‹ Back to dashboard"); back.setOnClickListener(v -> showHome()); page.addView(back, wrap()); }
        TextView step = label(deviceVerified ? "DEVICE CONNECTION" : "FIRST-TIME SETUP  ·  2 OF 3", 11); step.setTextColor(BLUE); page.addView(step);
        page.addView(title(hasAssignment ? "Your NeuroSense" : "Identify NeuroSense", 28), margin(-1,-2,0,12,0,8));
        page.addView(body(hasAssignment ? "Assigned by your clinic: " + (patientSession.assignedDeviceName == null ? "NeuroSense" : patientSession.assignedDeviceName) + "\nDevice ID: " + patientSession.assignedDeviceId : "Connect the nearby device only to read its hardware ID. Vibration output remains locked until a doctor registers and assigns it.", 15), margin(-1,-2,0,0,0,20));
        TextView status = label(deviceVerified ? "● VERIFIED · " + connectedName : connected ? "● CONNECTED · VERIFYING DATABASE ID" : "BLUETOOTH DEVICE SCAN", 12); status.setTextColor(deviceVerified ? GREEN : BLUE); page.addView(status);
        Button scan = primary(connected ? "Scan for another device" : "Find NeuroSense"); scan.setOnClickListener(v -> requestBleAndScan()); page.addView(scan, margin(-1,52,0,14,0,18));
        deviceList = column(Gravity.CENTER_HORIZONTAL, 10); page.addView(deviceList);
        if (connected) {
            LinearLayout connectedCard = card(Color.WHITE); connectedCard.addView(title(connectedName, 20)); connectedCard.addView(body(deviceVerified ? "Identity verified against " + patientName + "'s assignment" : physicalDeviceId == null ? "Waiting for the device identity response" : "Device ID · " + physicalDeviceId, 13));
            Button info = outline("Refresh device status"); info.setOnClickListener(v -> ble.sendType("get_status")); connectedCard.addView(info, margin(-1,48,0,14,0,8));
            if (deviceVerified) {
                Button wifi = outline("Wi-Fi settings"); wifi.setOnClickListener(v -> showWifiSetup()); connectedCard.addView(wifi, margin(-1,48,0,0,0,8));
                Button sync = outline("Sync usage now"); sync.setOnClickListener(v -> { ble.sendType("sync_now"); ble.sendType("get_pending"); toast("Synchronization requested."); }); connectedCard.addView(sync, margin(-1,48,0,0,0,8));
            }
            Button disconnect = danger("Disconnect"); disconnect.setOnClickListener(v -> { ble.disconnect(); connected = false; deviceVerified = false; showConnect(); }); connectedCard.addView(disconnect, margin(-1,48,0,0,0,0));
            deviceList.addView(connectedCard);
        } else deviceList.addView(body("No scan started yet.", 14));
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
        if (!deviceVerified || patientSession == null) { toast("Connect your assigned NeuroSense first."); showConnect(); return; }
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
            public void onFinish() { ble.sendType("stop_session"); showCompletion(false); }
        }.start();
    }

    private void confirmStop(boolean emergency) {
        if (sessionTimer != null) sessionTimer.cancel();
        ble.sendType(emergency ? "emergency_stop" : "stop_session");
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
        Button item = outline(name + "\nSignal " + rssi + " dBm"); item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); item.setOnClickListener(v -> { toast("Connecting to " + name + "…"); ble.connect(device); }); deviceList.addView(item, margin(-1,62,0,0,0,10));
    }

    @Override public void onConnectionChanged(boolean isConnected, String name) {
        connected = isConnected;
        connectedName = isConnected ? name : "Not connected";
        if (!isConnected) { deviceVerified = false; setupInProgress = false; setupComplete = false; }
        toast(isConnected ? name + " connected. Verifying assignment…" : name);
        if (isConnected) { showConnect(); ble.sendType("get_status"); }
    }

    @Override public void onMessage(String json) {
        try {
            JSONObject message = new JSONObject(json);
            String type = message.optString("type");
            if ("error".equals(type)) { setupInProgress = false; toast(message.optString("message", "Device rejected the command.")); return; }
            if ("status".equals(type)) { handleDeviceStatus(message); return; }
            if ("ok".equals(type)) { handleDeviceCommandAccepted(message.optString("command")); return; }
            if ("wifi_result".equals(type)) {
                boolean online = message.optBoolean("connected", false);
                wifiOnline = online;
                setupComplete = online;
                setupInProgress = false;
                if (online) {
                    preferences.edit().putString("enrolled_device_id", patientSession.assignedDeviceId).apply();
                    syncMessage = "Connected. Checking for records to upload.";
                    ble.sendType("sync_now"); ble.sendType("get_pending");
                    toast("NeuroSense setup is complete and Wi-Fi is connected."); showHome();
                } else { syncMessage = "Wi-Fi connection failed"; toast("Wi-Fi connection failed. Check the network name and password."); wifiPromptShown = false; showWifiSetup(); }
                return;
            }
            if ("sync_result".equals(type)) { pendingRecords = message.optInt("pending_sessions", pendingRecords); syncMessage = message.optBoolean("uploaded", false) ? "Latest usage uploaded successfully" : "No upload completed; records remain safely queued"; showHome(); return; }
            if (message.has("session_id") && message.has("device_id") && !message.has("command")) { relayUsageRecord(message); return; }
        }
        catch (Exception ignored) { }
    }
    @Override public void onError(String message) { toast(message); }

    private void handleDeviceStatus(JSONObject status) {
        physicalDeviceId = status.optString("device_id", "");
        savedWifiSsid = status.optString("wifi_ssid", "");
        wifiOnline = status.optBoolean("wifi_connected", false);
        pendingRecords = status.optInt("pending_sessions", 0);
        if (patientSession == null || patientSession.assignedDeviceId == null) {
            identificationMode = true;
            setupInProgress = false;
            toast("Device identified: " + physicalDeviceId);
            showConnect();
            return;
        }
        if (!patientSession.assignedDeviceId.equals(physicalDeviceId)) {
            deviceVerified = false;
            showWrongDevice();
            return;
        }
        deviceVerified = true;
        if (setupComplete || setupInProgress) return;
        setupInProgress = true;
        boolean apiConfigured = status.optBoolean("api_configured", false);
        boolean enrolledLocally = patientSession.assignedDeviceId.equals(preferences.getString("enrolled_device_id", ""));
        if (!apiConfigured || !enrolledLocally) requestSecureProvisioning();
        else sendAssignmentConfiguration();
    }

    private void requestSecureProvisioning() {
        authClient.provisionDevice(patientSession, physicalDeviceId, new SupabaseAuthClient.ProvisioningCallback() {
            public void onSuccess(SupabaseAuthClient.DeviceProvisioning provisioning) { runOnUiThread(() -> {
                pendingProvisioning = provisioning;
                try { ble.send(new JSONObject().put("type", "set_server").put("api_base_url", provisioning.apiBaseUrl).put("api_token", provisioning.apiToken)); }
                catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
            }); }
            public void onError(String message) { runOnUiThread(() -> { setupInProgress = false; toast(message); showConnect(); }); }
        });
    }

    private void handleDeviceCommandAccepted(String command) {
        if ("set_server".equals(command)) { sendAssignmentConfiguration(); return; }
        if ("set_assignment".equals(command)) { sendCarePlanConfiguration(); return; }
        if ("set_limits".equals(command)) { finishConfigurationWithWifi(); return; }
        if ("set_wifi".equals(command)) { toast("Wi-Fi saved. NeuroSense is connecting…"); return; }
    }

    private void sendAssignmentConfiguration() {
        try { ble.send(new JSONObject().put("type", "set_assignment").put("patient_id", patientSession.patientId).put("assignment_id", patientSession.assignmentId)); }
        catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void sendCarePlanConfiguration() {
        try {
            ble.send(new JSONObject().put("type", "set_limits")
                    .put("min_hz", 0).put("target_hz", Math.max(1, selectedHz)).put("max_hz", 230)
                    .put("max_duration_seconds", 90 * 60).put("manual_control_allowed", true));
        } catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void finishConfigurationWithWifi() {
        setupInProgress = false;
        if (!wifiOnline) { wifiPromptShown = false; showWifiSetup(); }
        else { setupComplete = true; preferences.edit().putString("enrolled_device_id", patientSession.assignedDeviceId).apply(); syncMessage = "Device ready; requesting queued usage"; ble.sendType("get_pending"); showHome(); }
    }

    private void wifiDialog() {
        showWifiSetup();
    }

    private void showWifiSetup() {
        if (!connected || !deviceVerified) { showConnect(); return; }
        LinearLayout page = page(false);
        TextView step = label("FIRST-TIME SETUP  ·  3 OF 3", 11); step.setTextColor(BLUE); page.addView(step);
        page.addView(title("Connect NeuroSense to Wi-Fi", 28), margin(-1,-2,0,12,0,8));
        page.addView(body("The network is saved securely on the ESP32 and reused automatically after every power-on.", 15), margin(-1,-2,0,0,0,20));
        LinearLayout device = card(SKY); device.addView(label("VERIFIED DEVICE", 11)); device.addView(title(connectedName, 20)); device.addView(body(patientSession.assignedDeviceId, 13)); page.addView(device);
        EditText ssid = input("Wi-Fi network name"); ssid.setText(savedWifiSsid); page.addView(ssid, margin(-1,54,0,18,0,12));
        EditText password = input("Wi-Fi password"); password.setInputType(0x81); page.addView(password, margin(-1,54,0,0,0,12));
        TextView state = body(wifiOnline ? "Currently connected to " + savedWifiSsid : "Not connected. Enter the 2.4 GHz Wi-Fi details.", 13); state.setTextColor(wifiOnline ? GREEN : MUTED); page.addView(state, margin(-1,-2,0,0,0,20));
        Button connectButton = primary("Save and Connect"); connectButton.setOnClickListener(v -> {
            String network = ssid.getText().toString().trim();
            if (network.isEmpty()) { toast("Enter the Wi-Fi network name."); return; }
            connectButton.setEnabled(false); connectButton.setText("Connecting…");
            try { savedWifiSsid = network; ble.send(new JSONObject().put("type","set_wifi").put("ssid",network).put("password",password.getText().toString())); }
            catch(Exception error) { connectButton.setEnabled(true); connectButton.setText("Save and Connect"); toast(error.getMessage()); }
        }); page.addView(connectButton, margin(-1,54,0,0,0,12));
        Button back = textButton("Back to device"); back.setOnClickListener(v -> showConnect()); page.addView(back);
        setScrollable(page);
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
        LinearLayout header = row(); TextView brand = brandTitle(21); header.addView(brand, weight()); TextView badge = label(deviceVerified ? "● VERIFIED" : "○ NOT READY", 10); badge.setTextColor(deviceVerified ? GREEN : MUTED); badge.setGravity(Gravity.CENTER); header.addView(badge, new LinearLayout.LayoutParams(dp(105), dp(40))); page.addView(header); page.addView(title(heading, 28), margin(-1,-2,0,24,0,0));
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
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){if(sessionTimer!=null)sessionTimer.cancel();if(authClient!=null)authClient.close();ble.disconnect();super.onDestroy();}
}
