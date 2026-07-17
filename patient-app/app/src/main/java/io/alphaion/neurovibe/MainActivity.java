package io.alphaion.neurovibe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity implements NeuroSenseBleManager.Listener {
    private static final int NAVY = Color.rgb(11, 35, 57);
    private static final int BLUE = Color.rgb(18, 111, 168);
    private static final int PALE = Color.rgb(243, 249, 253);
    private static final int GREEN = Color.rgb(27, 126, 82);
    private static final int RED = Color.rgb(190, 40, 45);
    private static final int MUTED = Color.rgb(88, 103, 115);

    private NeuroSenseBleManager ble;
    private LinearLayout page;
    private LinearLayout deviceList;
    private TextView connectionText;
    private TextView frequencyText;
    private TextView motorText;
    private SeekBar frequencySlider;
    private Button scanButton;
    private Button startButton;
    private Button stopButton;
    private final Set<String> discovered = new HashSet<>();
    private boolean connected;
    private boolean running;
    private int selectedHz = 100;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PALE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ble = new NeuroSenseBleManager(this, this);
        buildScreen();
        requestBluetoothPermissions();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PALE);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView brand = text("NeuroVibe", 31, NAVY, true);
        page.addView(brand);
        page.addView(text("Simple Bluetooth motor control", 15, MUTED, false), margin(0, 2, 0, 24));

        LinearLayout connectionCard = card(Color.WHITE);
        connectionCard.addView(text("DEVICE CONNECTION", 11, BLUE, true));
        connectionText = text("Not connected", 22, NAVY, true);
        connectionCard.addView(connectionText, margin(0, 8, 0, 4));
        connectionCard.addView(text("Keep NeuroSense powered and close to this tablet.", 14, MUTED, false));
        scanButton = button("Scan for NeuroSense", BLUE);
        scanButton.setOnClickListener(v -> requestBluetoothPermissions());
        connectionCard.addView(scanButton, margin(0, 16, 0, 0));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        connectionCard.addView(deviceList, margin(0, 10, 0, 0));
        page.addView(connectionCard);

        LinearLayout controlCard = card(NAVY);
        TextView controlLabel = text("MOTOR CONTROL", 11, Color.rgb(149, 213, 250), true);
        controlCard.addView(controlLabel);
        frequencyText = text(selectedHz + " Hz", 43, Color.WHITE, true);
        controlCard.addView(frequencyText, margin(0, 10, 0, 3));
        motorText = text("Motors stopped", 14, Color.rgb(205, 225, 239), false);
        controlCard.addView(motorText);

        frequencySlider = new SeekBar(this);
        frequencySlider.setMax(230);
        frequencySlider.setProgress(selectedHz);
        frequencySlider.setEnabled(false);
        frequencySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                selectedHz = value;
                frequencyText.setText(value + " Hz");
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (running) sendFrequency(selectedHz);
            }
        });
        controlCard.addView(frequencySlider, margin(0, 18, 0, 10));
        controlCard.addView(text("0 Hz stops the motors. Range: 0–230 Hz.", 13, Color.rgb(205, 225, 239), false));

        startButton = button("Start motors", GREEN);
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> sendFrequency(selectedHz));
        controlCard.addView(startButton, margin(0, 20, 0, 10));
        stopButton = button("STOP", RED);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> ble.sendType("stop"));
        controlCard.addView(stopButton);
        page.addView(controlCard, margin(0, 18, 0, 0));

        page.addView(text(
                "The displayed value is the requested control value. Exact mechanical vibration frequency requires a vibration sensor and calibration.",
                12, MUTED, false), margin(4, 16, 4, 0));
        updateControls();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                }, 44);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
            return;
        }
        beginScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 44) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                toast("Bluetooth permission is required.");
                return;
            }
        }
        beginScan();
    }

    @SuppressLint("MissingPermission")
    private void beginScan() {
        if (!ble.isBluetoothAvailable()) {
            toast("Turn on Bluetooth and try again.");
            return;
        }
        discovered.clear();
        deviceList.removeAllViews();
        deviceList.addView(text("Scanning nearby devices…", 14, MUTED, false));
        scanButton.setText("Scanning…");
        ble.startScan();
    }

    @SuppressLint("MissingPermission")
    @Override public void onScanResult(BluetoothDevice device, int rssi) {
        String address = device.getAddress();
        if (!discovered.add(address)) return;
        if (discovered.size() == 1) deviceList.removeAllViews();
        String name = device.getName() == null ? "NeuroSense" : device.getName();
        Button result = button(name + "   ·   " + rssi + " dBm", Color.WHITE);
        result.setTextColor(NAVY);
        result.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        result.setOnClickListener(v -> {
            connectionText.setText("Connecting to " + name + "…");
            ble.connect(device);
        });
        deviceList.addView(result, margin(0, 4, 0, 6));
    }

    @Override public void onConnectionChanged(boolean isConnected, String name) {
        connected = isConnected;
        if (!connected) running = false;
        connectionText.setText(connected ? "Connected to " + name : name);
        scanButton.setText(connected ? "Connect another device" : "Scan for NeuroSense");
        updateControls();
        if (connected) ble.sendType("get_status");
    }

    @Override public void onMessage(String json) {
        try {
            JSONObject response = new JSONObject(json);
            if ("error".equals(response.optString("type"))) {
                toast(response.optString("message", "Device command failed."));
                return;
            }
            if ("status".equals(response.optString("type"))) {
                running = response.optBoolean("running", false);
                int hz = (int) Math.round(response.optDouble("hz", selectedHz));
                if (running || hz == 0) {
                    selectedHz = hz;
                    frequencySlider.setProgress(hz);
                    frequencyText.setText(hz + " Hz");
                }
                motorText.setText(running ? "Both motors running" : "Motors stopped");
                updateControls();
                String message = response.optString("message", "");
                if (!message.isEmpty()) toast(message);
            }
        } catch (Exception ignored) {
            toast("Unexpected response from NeuroSense.");
        }
    }

    @Override public void onError(String message) {
        scanButton.setText("Scan for NeuroSense");
        toast(message);
    }

    private void sendFrequency(int hz) {
        if (!connected) {
            toast("Connect NeuroSense first.");
            return;
        }
        try {
            ble.send(new JSONObject().put("type", "set_frequency").put("hz", hz));
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }

    private void updateControls() {
        frequencySlider.setEnabled(connected);
        startButton.setEnabled(connected);
        stopButton.setEnabled(connected && running);
        motorText.setText(running ? "Both motors running" : "Motors stopped");
        connectionText.setTextColor(connected ? GREEN : NAVY);
    }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(20));
        card.setBackground(background);
        return card;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(14));
        if (color == Color.WHITE) background.setStroke(dp(1), Color.rgb(207, 220, 228));
        button.setBackground(background);
        button.setTextColor(color == Color.WHITE ? NAVY : Color.WHITE);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        if (ble != null) {
            if (ble.isConnected()) ble.sendType("stop");
            ble.disconnect();
        }
        super.onDestroy();
    }
}
