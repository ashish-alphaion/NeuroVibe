package io.alphaion.neurovibe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.Intent;
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

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

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
    private boolean identificationMode;
    private CountDownTimer sessionTimer;
    private int selectedHz = 85;
    private int remainingSeconds = 600;
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
        createNotificationChannel();
        if (!handleAuthIntent(getIntent())) showSplash();
    }

    private void showSplash() {
        root.removeAllViews();
        LinearLayout box = column(Gravity.CENTER, 0); box.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView mark = brandTitle(30); mark.setTextColor(NAVY); box.addView(mark);
        ProgressBar progress = new ProgressBar(this); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42)); pp.topMargin = dp(28); box.addView(progress, pp);
        root.addView(box, match()); handler.postDelayed(this::showWelcome, 1100);
    }

    private void showWelcome() {
        LinearLayout page = page(false); page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView brand = brandTitle(24); brand.setTextColor(NAVY); page.addView(brand, margin(-1, -2, 0, 10, 0, 26));
        FrameLayout hero = new FrameLayout(this); hero.setBackground(round(SKY, 140)); hero.setClipToOutline(true);
        ImageView device = new ImageView(this); device.setImageResource(io.alphaion.neurovibe.R.drawable.neurosense_hero); device.setScaleType(ImageView.ScaleType.CENTER_CROP); hero.addView(device, match());
        TextView badge = label("NEUROSENSE", 12); badge.setTextColor(Color.WHITE); badge.setGravity(Gravity.CENTER); badge.setBackground(round(NAVY_CARD, 20)); FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(150), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); bp.bottomMargin = dp(28); hero.addView(badge, bp);
        page.addView(hero, margin(dp(280), dp(280), 0, 0, 0, 30));
        TextView h = title("Welcome to NeuroVibe", 28); h.setGravity(Gravity.CENTER); page.addView(h);
        TextView p = body("Connect to your assigned NeuroSense device and follow sessions scheduled by your doctor.", 16); p.setGravity(Gravity.CENTER); page.addView(p, margin(-1, -2, 18, 14, 18, 32));
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
        showSetPassword(accessToken);
        return true;
    }

    private void showSetPassword(String accessToken) {
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
            authClient.acceptInvitation(accessToken, value, new SupabaseAuthClient.Callback() {
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
    }

    private void routeAfterLogin() {
        if (patientSession == null || patientSession.assignmentId == null || patientSession.assignedDeviceId == null) showAssignmentRequired();
        else showConnect();
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

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthIntent(intent);
    }

    private void showHome() {
        if (patientSession == null || patientSession.assignmentId == null) { showAssignmentRequired(); return; }
        LinearLayout page = page(true); addHeader(page, "Welcome, " + patientName);
        boolean hasPlan = patientSession.carePlanName != null;
        TextView intro = body(hasPlan ? "Your prescribed care plan is available." : "Your device is assigned, but your doctor has not created an active care plan.", 15); page.addView(intro, margin(-1, -2, 0, 2, 0, 22));
        LinearLayout therapy = card(NAVY_CARD); TextView eyebrow = label(hasPlan ? patientSession.carePlanName.toUpperCase(Locale.US) : "CARE PLAN REQUIRED", 11); eyebrow.setTextColor(Color.rgb(142,205,255)); therapy.addView(eyebrow);
        TextView plan = title(hasPlan ? formatHz(patientSession.targetHz) + " Hz · " + Math.round(patientSession.durationSeconds / 60.0) + " minutes" : "No active prescription", 25); plan.setTextColor(Color.WHITE); therapy.addView(plan, margin(-1,-2,0,10,0,8));
        TextView range = body(hasPlan ? "Doctor-approved range: " + formatHz(patientSession.minHz) + "–" + formatHz(patientSession.maxHz) + " Hz" : "Ask your doctor to create a care plan before using vibration output.", 14); range.setTextColor(Color.rgb(203,230,255)); therapy.addView(range);
        Button start = accent(deviceVerified && hasPlan ? "Start Session" : deviceVerified ? "Care plan required" : "Set up assigned device"); start.setEnabled(hasPlan); start.setOnClickListener(v -> { if (!deviceVerified) showConnect(); else startSession(); }); therapy.addView(start, margin(-1,50,0,22,0,0)); page.addView(therapy);
        connectionLabel = label(deviceVerified ? "●  " + connectedName + " verified" : "○  Assigned NeuroSense not verified", 12); connectionLabel.setTextColor(deviceVerified ? GREEN : RED); connectionLabel.setOnClickListener(v -> showConnect()); page.addView(connectionLabel, margin(-1,-2,2,14,0,22));
        page.addView(title("Assignment", 20), margin(-1,-2,0,0,0,12));
        LinearLayout metrics = row(); metrics.addView(metric(patientSession.assignedDeviceName == null ? "Assigned" : patientSession.assignedDeviceName, "Device", "✓"), weight()); metrics.addView(metric(deviceVerified ? "Verified" : "Setup", "Status", deviceVerified ? "✓" : "!"), weightWithLeft()); page.addView(metrics);
        page.addView(title("Quick actions", 20), margin(-1,-2,0,26,0,12));
        LinearLayout actions = row(); Button device = outline("Connect device"); device.setOnClickListener(v -> showConnect()); actions.addView(device, weight()); Button symptoms = outline("Log symptoms"); symptoms.setOnClickListener(v -> symptomDialog()); actions.addView(symptoms, weightWithLeft()); page.addView(actions);
        setScrollable(page); addBottomNav("home");
    }

    private void showSchedule() {
        LinearLayout page = page(true); addHeader(page, "My Schedule");
        page.addView(body("No synchronized schedules are available yet. New sessions created by your doctor will appear after the next account refresh.", 15), margin(-1,-2,0,2,0,22));
        setScrollable(page); addBottomNav("schedule");
    }

    private void showHistory() {
        LinearLayout page = page(true); addHeader(page, "Session History");
        page.addView(body("No synchronized session history is available yet. Completed device sessions will appear after Wi-Fi synchronization.", 15), margin(-1,-2,0,2,0,22));
        setScrollable(page); addBottomNav("history");
    }

    private void showMore() {
        LinearLayout page = page(true); addHeader(page, "My NeuroVibe");
        LinearLayout profile = card(Color.WHITE); profile.addView(title(patientName, 22)); profile.addView(body("Patient ID · " + patientCode, 13)); page.addView(profile);
        page.addView(menuButton("NeuroSense device", patientSession == null || patientSession.assignedDeviceId == null ? "No database assignment" : (deviceVerified ? connectedName + " verified" : patientSession.assignedDeviceName + " · setup required"), this::showConnect), margin(-1,-2,0,16,0,0));
        page.addView(menuButton("Care plan", patientSession == null || patientSession.carePlanName == null ? "No active care plan" : patientSession.carePlanName + " · " + formatHz(patientSession.minHz) + "–" + formatHz(patientSession.maxHz) + " Hz", () -> carePlanDialog()), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Wi-Fi setup", "Send Wi-Fi details securely over Bluetooth", this::wifiDialog), margin(-1,-2,0,12,0,0));
        page.addView(menuButton("Help and safety", "Emergency guidance and support", this::helpDialog), margin(-1,-2,0,12,0,0));
        Button signOut = outline("Sign out"); signOut.setOnClickListener(v -> showWelcome()); page.addView(signOut, margin(-1,52,0,22,0,0));
        setScrollable(page); addBottomNav("more");
    }

    @SuppressLint("MissingPermission")
    private void showConnect() {
        boolean hasAssignment = patientSession != null && patientSession.assignedDeviceId != null;
        if (!hasAssignment && !identificationMode) { showAssignmentRequired(); return; }
        LinearLayout page = page(false);
        if (deviceVerified) { Button back = textButton("‹ Back to dashboard"); back.setOnClickListener(v -> showHome()); page.addView(back, wrap()); }
        page.addView(title(hasAssignment ? "Set up assigned NeuroSense" : "Identify NeuroSense", 28), margin(-1,-2,0,20,0,8));
        page.addView(body(hasAssignment ? "Assigned by your clinic: " + (patientSession.assignedDeviceName == null ? "NeuroSense" : patientSession.assignedDeviceName) + "\nDevice ID: " + patientSession.assignedDeviceId : "Connect the nearby device only to read its hardware ID. Vibration output remains locked until a doctor registers and assigns it.", 15), margin(-1,-2,0,0,0,20));
        TextView status = label(deviceVerified ? "● VERIFIED · " + connectedName : connected ? "● CONNECTED · VERIFYING DATABASE ID" : "BLUETOOTH DEVICE SCAN", 12); status.setTextColor(deviceVerified ? GREEN : BLUE); page.addView(status);
        Button scan = primary(connected ? "Scan for another device" : "Find NeuroSense"); scan.setOnClickListener(v -> requestBleAndScan()); page.addView(scan, margin(-1,52,0,14,0,18));
        deviceList = column(Gravity.CENTER_HORIZONTAL, 10); page.addView(deviceList);
        if (connected) {
            LinearLayout connectedCard = card(Color.WHITE); connectedCard.addView(title(connectedName, 20)); connectedCard.addView(body(deviceVerified ? "Identity verified against " + patientName + "'s assignment" : physicalDeviceId == null ? "Waiting for the device identity response" : "Device ID · " + physicalDeviceId, 13));
            Button info = outline("Refresh device status"); info.setOnClickListener(v -> ble.sendType("get_status")); connectedCard.addView(info, margin(-1,48,0,14,0,0)); deviceList.addView(connectedCard);
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
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 45); else postDemoReminder();
    }

    @SuppressLint("MissingPermission") private void beginScan() {
        scanResults.clear(); if (deviceList != null) { deviceList.removeAllViews(); deviceList.addView(body("Searching for NeuroSense devices…", 14)); } ble.startScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 44 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) beginScan();
        else if (requestCode == 44) toast("Bluetooth permission is required to control NeuroSense.");
        if (requestCode == 45) { toast("Permissions updated. You can change them later in Android Settings."); postDemoReminder(); }
    }

    private void startSession() {
        if (!deviceVerified || patientSession == null || patientSession.carePlanName == null) { toast("Complete device setup and wait for an active care plan."); showConnect(); return; }
        try { ble.send(new JSONObject().put("type", "start_session").put("target_hz", selectedHz).put("duration_seconds", patientSession.durationSeconds)); }
        catch (Exception error) { toast(error.getMessage()); }
        showActiveSession();
    }

    private void showActiveSession() {
        int sessionDuration = patientSession == null ? 0 : patientSession.durationSeconds;
        if (sessionDuration <= 0) { toast("The care plan duration is invalid."); showHome(); return; }
        if (sessionTimer != null) sessionTimer.cancel(); remainingSeconds = sessionDuration;
        LinearLayout page = page(false); addHeader(page, "Active Session");
        TextView status = label("● BLUETOOTH CONNECTED", 11); status.setTextColor(GREEN); status.setGravity(Gravity.CENTER); page.addView(status);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(sessionDuration); progress.setProgress(0); page.addView(progress, margin(-1,8,0,16,0,26));
        TextView timer = title(String.format(Locale.US, "%02d:%02d", sessionDuration / 60, sessionDuration % 60), 48); timer.setGravity(Gravity.CENTER); page.addView(timer);
        TextView frequency = title(selectedHz + "\nHz", 52); frequency.setGravity(Gravity.CENTER); frequency.setTextColor(NAVY); frequency.setBackground(round(Color.WHITE, 100)); page.addView(frequency, margin(dp(190),dp(190),0,22,0,24));
        int minimumHz = (int)Math.ceil(patientSession.minHz); int maximumHz = (int)Math.floor(patientSession.maxHz);
        LinearLayout controls = card(Color.WHITE); controls.addView(title("Frequency", 20)); controls.addView(body("Doctor-approved range · " + minimumHz + "–" + maximumHz + " Hz", 12));
        SeekBar slider = new SeekBar(this); slider.setMax(Math.max(maximumHz - minimumHz, 0)); slider.setProgress(Math.max(0, selectedHz - minimumHz)); slider.setEnabled(patientSession.manualControlAllowed); controls.addView(slider, margin(-1,44,0,18,0,0));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) { selectedHz = value + minimumHz; frequency.setText(selectedHz + "\nHz"); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) { try { ble.send(new JSONObject().put("type", "set_frequency").put("hz", selectedHz)); } catch (Exception e) { toast(e.getMessage()); } }
        });
        Button end = outline("End Session"); end.setOnClickListener(v -> confirmStop(false)); controls.addView(end, margin(-1,50,0,18,0,0)); page.addView(controls);
        Button emergency = danger("⚠  EMERGENCY STOP"); emergency.setOnClickListener(v -> confirmStop(true)); page.addView(emergency, margin(-1,58,0,22,0,8));
        TextView safety = body("Emergency stop immediately terminates all vibration output.", 12); safety.setGravity(Gravity.CENTER); page.addView(safety);
        setScrollable(page);
        sessionTimer = new CountDownTimer(sessionDuration * 1000L, 1000) {
            public void onTick(long millis) { remainingSeconds = (int)(millis / 1000); timer.setText(String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)); progress.setProgress(sessionDuration - remainingSeconds); }
            public void onFinish() { showCompletion(false); }
        }.start();
    }

    private void confirmStop(boolean emergency) {
        new AlertDialog.Builder(this).setTitle(emergency ? "Emergency stop?" : "End this session?").setMessage(emergency ? "All vibration output will stop immediately." : "The partial session will be saved.").setNegativeButton("Cancel", null).setPositiveButton(emergency ? "STOP" : "End", (d,w) -> {
            if (sessionTimer != null) sessionTimer.cancel(); ble.sendType(emergency ? "emergency_stop" : "stop_session"); showCompletion(true);
        }).show();
    }

    private void showCompletion(boolean stopped) {
        LinearLayout page = page(false); page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView check = title(stopped ? "!" : "✓", 54); check.setTextColor(Color.WHITE); check.setGravity(Gravity.CENTER); check.setBackground(round(stopped ? RED : GREEN, 70)); page.addView(check, margin(110,110,0,70,0,22));
        page.addView(title(stopped ? "Session ended safely" : "Session complete", 28)); TextView text = body(stopped ? "The device has stopped and the partial record will synchronize." : "Your prescribed vibration cycle finished successfully.", 15); text.setGravity(Gravity.CENTER); page.addView(text, margin(-1,-2,18,12,18,28));
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
                setupComplete = online;
                setupInProgress = false;
                if (online) { toast("NeuroSense is configured and connected to Wi-Fi."); showHome(); }
                else { toast("Wi-Fi connection failed. Check the network name and password."); wifiPromptShown = false; wifiDialog(); }
            }
        }
        catch (Exception ignored) { }
    }
    @Override public void onError(String message) { toast(message); }

    private void handleDeviceStatus(JSONObject status) {
        physicalDeviceId = status.optString("device_id", "");
        savedWifiSsid = status.optString("wifi_ssid", "");
        if (patientSession == null || patientSession.assignedDeviceId == null) {
            identificationMode = true;
            setupInProgress = false;
            new AlertDialog.Builder(this).setTitle("NeuroSense identified")
                    .setMessage("Physical device ID:\n" + physicalDeviceId + "\n\nRegister this exact ID in the doctor portal and assign it to patient " + patientCode + ".")
                    .setPositiveButton("Done", null).show();
            showConnect();
            return;
        }
        if (!patientSession.assignedDeviceId.equals(physicalDeviceId)) {
            deviceVerified = false;
            String expected = patientSession.assignedDeviceId;
            new AlertDialog.Builder(this).setTitle("Wrong NeuroSense device")
                    .setMessage("Connected device ID:\n" + physicalDeviceId + "\n\nAssigned device ID:\n" + expected + "\n\nAsk the doctor to register/assign the connected ID, or connect the assigned device.")
                    .setPositiveButton("Disconnect", (d,w) -> ble.disconnect()).show();
            return;
        }
        deviceVerified = true;
        if (setupComplete || setupInProgress) return;
        setupInProgress = true;
        boolean apiConfigured = status.optBoolean("api_configured", false);
        if (!apiConfigured) requestSecureProvisioning();
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
        if (patientSession.carePlanName == null) {
            setupInProgress = false;
            toast("Device assignment saved, but an active care plan is still required.");
            showHome();
            return;
        }
        try {
            ble.send(new JSONObject().put("type", "set_limits")
                    .put("min_hz", patientSession.minHz).put("target_hz", patientSession.targetHz).put("max_hz", patientSession.maxHz)
                    .put("max_duration_seconds", patientSession.maxDurationSeconds).put("manual_control_allowed", patientSession.manualControlAllowed));
        } catch (Exception error) { setupInProgress = false; toast(error.getMessage()); }
    }

    private void finishConfigurationWithWifi() {
        setupInProgress = false;
        if (savedWifiSsid.isEmpty()) { wifiPromptShown = false; wifiDialog(); }
        else { setupComplete = true; toast("Assignment and care plan synchronized."); showHome(); }
    }

    private void wifiDialog() {
        if (!connected || !deviceVerified) { showConnect(); return; }
        if (wifiPromptShown) return;
        wifiPromptShown = true;
        LinearLayout form = column(Gravity.CENTER_HORIZONTAL, 10); EditText ssid = input("Wi-Fi network name"); ssid.setText(savedWifiSsid); EditText password = input("Wi-Fi password"); password.setInputType(0x81); form.addView(ssid); form.addView(password);
        new AlertDialog.Builder(this).setTitle("Connect NeuroSense to Wi-Fi")
                .setMessage("These details are sent directly to the verified assigned device over Bluetooth and saved on the ESP32.")
                .setView(form).setOnCancelListener(d -> wifiPromptShown = false)
                .setNegativeButton("Later", (d,w) -> { wifiPromptShown = false; showHome(); })
                .setPositiveButton("Connect", (d,w) -> {
                    String network = ssid.getText().toString().trim();
                    if (network.isEmpty()) { wifiPromptShown = false; toast("Enter the Wi-Fi network name."); return; }
                    try { savedWifiSsid = network; ble.send(new JSONObject().put("type","set_wifi").put("ssid",network).put("password",password.getText().toString())); }
                    catch(Exception e){wifiPromptShown = false;toast(e.getMessage());}
                }).show();
    }

    private void symptomDialog() { final String[] items={"Comfortable","Tingling","Tired","Dizzy","Pain or discomfort"}; new AlertDialog.Builder(this).setTitle("How are you feeling?").setSingleChoiceItems(items,0,null).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->toast("Symptom note saved on this prototype.")).show(); }
    private void carePlanDialog() {
        if (patientSession == null || patientSession.carePlanName == null) { toast("No active care plan is assigned."); return; }
        new AlertDialog.Builder(this).setTitle(patientSession.carePlanName)
                .setMessage("Target: " + formatHz(patientSession.targetHz) + " Hz\nAllowed range: " + formatHz(patientSession.minHz) + "–" + formatHz(patientSession.maxHz) + " Hz\nDuration: " + Math.round(patientSession.durationSeconds / 60.0) + " minutes\nManual adjustment: " + (patientSession.manualControlAllowed ? "Allowed" : "Locked") + "\n\nOnly your care team can change these limits.")
                .setPositiveButton("Done",null).show();
    }
    private void helpDialog() { new AlertDialog.Builder(this).setTitle("NeuroVibe help and safety").setMessage("Use only the device assigned to you. Stop immediately if you feel pain, dizziness, numbness, or unusual discomfort. This prototype is not a substitute for medical advice. Contact your care team for clinical questions.").setPositiveButton("Understood",null).show(); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("therapy_reminders", "Therapy reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Patient reminders for doctor-scheduled NeuroVibe sessions");
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission") private void postDemoReminder() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "therapy_reminders")
                .setSmallIcon(io.alphaion.neurovibe.R.drawable.ic_neurovibe)
                .setContentTitle("NeuroVibe session ready")
                .setContentText(patientSession != null && patientSession.carePlanName != null ? patientSession.carePlanName + " is ready." : "Open NeuroVibe to review your care plan.")
                .setContentIntent(pending).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(1001, notification);
    }

    private void addHeader(LinearLayout page, String heading) {
        LinearLayout header = row(); TextView brand = brandTitle(21); header.addView(brand, weight()); TextView badge = label(deviceVerified ? "● VERIFIED" : "○ NOT READY", 10); badge.setTextColor(deviceVerified ? GREEN : MUTED); badge.setGravity(Gravity.CENTER); header.addView(badge, new LinearLayout.LayoutParams(dp(105), dp(40))); page.addView(header); page.addView(title(heading, 28), margin(-1,-2,0,24,0,0));
    }

    private void addBottomNav(String active) {
        LinearLayout nav = row(); nav.setPadding(dp(10), dp(7), dp(10), dp(7)); nav.setBackground(round(Color.WHITE, 18));
        nav.addView(navButton("⌂\nHome", active.equals("home"), this::showHome), weight()); nav.addView(navButton("□\nSchedule", active.equals("schedule"), this::showSchedule), weight()); nav.addView(navButton("◷\nHistory", active.equals("history"), this::showHistory), weight()); nav.addView(navButton("•••\nMore", active.equals("more"), this::showMore), weight());
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM); np.setMargins(dp(12),0,dp(12),dp(10)); root.addView(nav,np);
    }

    private View scheduleCard(String when, String name, String prescription, boolean ready) { LinearLayout c=card(Color.WHITE); TextView e=label(when,11);e.setTextColor(BLUE);c.addView(e);c.addView(title(name,19),margin(-1,-2,0,8,0,5));c.addView(body(prescription,14));if(ready){Button b=primary("Start session");b.setOnClickListener(v->{if(connected)startSession();else showConnect();});c.addView(b,margin(-1,48,0,14,0,0));}return c; }
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
