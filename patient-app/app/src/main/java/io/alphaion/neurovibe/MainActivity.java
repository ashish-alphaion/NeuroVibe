package io.alphaion.neurovibe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
    // Enhanced iOS Style Colors
    private static final int SYSTEM_BACKGROUND = Color.rgb(242, 242, 247);
    private static final int CARD_BACKGROUND = Color.WHITE;
    private static final int SYSTEM_BLUE = Color.rgb(0, 122, 255);
    private static final int SYSTEM_GREEN = Color.rgb(52, 199, 89);
    private static final int SYSTEM_RED = Color.rgb(255, 59, 48);
    private static final int TEXT_PRIMARY = Color.BLACK;
    private static final int TEXT_SECONDARY = Color.rgb(142, 142, 147);
    private static final int SEPARATOR_COLOR = Color.rgb(229, 229, 234);

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
        getWindow().setStatusBarColor(SYSTEM_BACKGROUND);
        getWindow().setNavigationBarColor(SYSTEM_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ble = new NeuroSenseBleManager(this, this);
        buildScreen();
        requestBluetoothPermissions();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(SYSTEM_BACKGROUND);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(40), dp(16), dp(40)); // Increased padding for modern feel
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        // Header - iOS Large Title
        TextView brand = text("NeuroVibe", 34, TEXT_PRIMARY, true);
        brand.setGravity(Gravity.START);
        page.addView(brand, margin(8, 0, 0, 32));

        // DEVICE CONNECTION SECTION
        TextView connectionLabel = text("DEVICE CONNECTION", 13, TEXT_SECONDARY, false);
        page.addView(connectionLabel, margin(16, 0, 0, 8));

        LinearLayout connectionCard = card(CARD_BACKGROUND);
        connectionText = text("Not connected", 17, TEXT_PRIMARY, true);
        connectionCard.addView(connectionText);
        connectionCard.addView(text("Keep NeuroSense powered and close to this device.", 15, TEXT_SECONDARY, false), margin(0, 6, 0, 20));
        
        scanButton = primaryButton("Scan for NeuroSense", SYSTEM_BLUE);
        scanButton.setOnClickListener(v -> requestBluetoothPermissions());
        connectionCard.addView(scanButton);
        
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        connectionCard.addView(deviceList, margin(0, 16, 0, 0));
        page.addView(connectionCard);

        // MOTOR CONTROL SECTION
        TextView controlLabel = text("MOTOR CONTROL", 13, TEXT_SECONDARY, false);
        page.addView(controlLabel, margin(16, 32, 0, 8));

        LinearLayout controlCard = card(CARD_BACKGROUND);
        
        frequencyText = text(selectedHz + " Hz", 56, TEXT_PRIMARY, true);
        frequencyText.setGravity(Gravity.CENTER);
        controlCard.addView(frequencyText, margin(0, 16, 0, 4));
        
        motorText = text("Motors stopped", 16, TEXT_SECONDARY, true);
        motorText.setGravity(Gravity.CENTER);
        controlCard.addView(motorText);

        frequencySlider = new SeekBar(this);
        frequencySlider.setMax(230);
        frequencySlider.setProgress(selectedHz);
        frequencySlider.setEnabled(false);
        if (Build.VERSION.SDK_INT >= 21) {
            frequencySlider.setProgressTintList(ColorStateList.valueOf(SYSTEM_BLUE));
            frequencySlider.setThumbTintList(ColorStateList.valueOf(SYSTEM_BLUE));
        }
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
        controlCard.addView(frequencySlider, margin(0, 36, 0, 12));
        
        TextView sliderHint = text("Range: 0–230 Hz. Set 0 Hz to stop.", 13, TEXT_SECONDARY, false);
        sliderHint.setGravity(Gravity.CENTER);
        controlCard.addView(sliderHint, margin(0, 0, 0, 36));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        
        startButton = primaryButton("Start Motors", SYSTEM_GREEN);
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> sendFrequency(selectedHz));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, -2, 1);
        startParams.setMargins(0, 0, dp(6), 0);
        buttonRow.addView(startButton, startParams);
        
        stopButton = primaryButton("Stop Motors", SYSTEM_RED);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> ble.sendType("stop"));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, -2, 1);
        stopParams.setMargins(dp(6), 0, 0, 0);
        buttonRow.addView(stopButton, stopParams);
        
        controlCard.addView(buttonRow);
        page.addView(controlCard, margin(0, 0, 0, 24));

        TextView footerText = text("The displayed value is the requested control value. Exact mechanical vibration frequency requires a vibration sensor and calibration.", 12, TEXT_SECONDARY, false);
        footerText.setGravity(Gravity.CENTER);
        page.addView(footerText, margin(16, 8, 16, 0));
        
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
        deviceList.addView(text("Scanning nearby devices…", 15, TEXT_SECONDARY, false));
        scanButton.setText("Scanning…");
        ble.startScan();
    }

    @SuppressLint("MissingPermission")
    @Override public void onScanResult(BluetoothDevice device, int rssi) {
        String address = device.getAddress();
        if (!discovered.add(address)) return;
        if (discovered.size() == 1) deviceList.removeAllViews();
        String name = device.getName() == null ? "NeuroSense" : device.getName();
        
        Button result = new Button(this);
        result.setText(name + "  ·  " + rssi + " dBm");
        result.setTextSize(16);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setAllCaps(false);
        result.setTextColor(SYSTEM_BLUE);
        result.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        result.setBackgroundColor(Color.TRANSPARENT);
        result.setPadding(dp(4), dp(14), dp(4), dp(14));
        
        result.setOnClickListener(v -> {
            connectionText.setText("Connecting to " + name + "…");
            ble.connect(device);
        });
        
        if (deviceList.getChildCount() > 0) {
            View separator = new View(this);
            separator.setBackgroundColor(SEPARATOR_COLOR);
            deviceList.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        deviceList.addView(result, margin(0, 0, 0, 0));
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
                motorText.setTextColor(running ? SYSTEM_GREEN : TEXT_SECONDARY);
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
        connectionText.setTextColor(connected ? SYSTEM_GREEN : TEXT_PRIMARY);
        
        startButton.setAlpha(connected ? 1.0f : 0.5f);
        stopButton.setAlpha((connected && running) ? 1.0f : 0.5f);
    }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(16)); // Softer, more iOS-like radius
        card.setBackground(background);
        if (Build.VERSION.SDK_INT >= 21) {
            card.setElevation(dp(2)); // Very subtle drop shadow
        }
        return card;
    }

    private Button primaryButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17); 
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(54)); // Slightly taller for better tap targets
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12)); 
        button.setBackground(background);
        button.setTextColor(Color.WHITE);
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
