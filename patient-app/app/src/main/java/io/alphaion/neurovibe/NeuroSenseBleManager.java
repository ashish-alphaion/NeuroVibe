package io.alphaion.neurovibe;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class NeuroSenseBleManager {
    public interface Listener {
        void onScanResult(BluetoothDevice device, int rssi);
        void onConnectionChanged(boolean connected, String deviceName);
        void onMessage(String json);
        void onError(String message);
    }

    public static final UUID SERVICE_UUID = UUID.fromString("7b1e0001-7f34-4fd8-a912-6c38ef4a5201");
    private static final UUID COMMAND_UUID = UUID.fromString("7b1e0002-7f34-4fd8-a912-6c38ef4a5201");
    private static final UUID RESPONSE_UUID = UUID.fromString("7b1e0003-7f34-4fd8-a912-6c38ef4a5201");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic command;
    private String pendingDeviceName = "NeuroSense";
    private boolean scanning;
    private final StringBuilder framedJson = new StringBuilder();
    private boolean receivingJson;

    public NeuroSenseBleManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    public boolean isBluetoothAvailable() { return adapter != null && adapter.isEnabled(); }
    public boolean isConnected() { return gatt != null && command != null; }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (!isBluetoothAvailable()) { listener.onError("Turn on Bluetooth to find NeuroSense."); return; }
        stopScan();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { listener.onError("Bluetooth scanner is unavailable."); return; }
        scanning = true;
        scanner.startScan(scanCallback);
        main.postDelayed(this::stopScan, 12_000);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanning && scanner != null) scanner.stopScan(scanCallback);
        scanning = false;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        stopScan(); disconnect();
        listener.onConnectionChanged(false, "Connecting…");
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        command = null;
        if (gatt != null) { gatt.disconnect(); gatt.close(); gatt = null; }
    }

    @SuppressLint("MissingPermission")
    public boolean send(JSONObject payload) {
        if (gatt == null || command == null) { listener.onError("Connect NeuroSense before sending a command."); return false; }
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 512) { listener.onError("The device command is too large."); return false; }
        if (Build.VERSION.SDK_INT >= 33) {
            return gatt.writeCharacteristic(command, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0;
        }
        command.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        command.setValue(bytes);
        return gatt.writeCharacteristic(command);
    }

    public void sendType(String type) {
        try { send(new JSONObject().put("type", type)); }
        catch (Exception error) { listener.onError(error.getMessage()); }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.startsWith("NeuroSense")) main.post(() -> listener.onScanResult(device, result.getRssi()));
        }
        @Override public void onScanFailed(int errorCode) { main.post(() -> listener.onError("Device scan failed (" + errorCode + ").")); }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                current.requestMtu(247);
                current.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                command = null;
                main.post(() -> listener.onConnectionChanged(false, "Disconnected"));
            }
        }

        @SuppressLint("MissingPermission")
        @Override public void onServicesDiscovered(BluetoothGatt current, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { main.post(() -> listener.onError("NeuroSense service discovery failed.")); return; }
            BluetoothGattService service = current.getService(SERVICE_UUID);
            if (service == null) { main.post(() -> listener.onError("This is not a compatible NeuroSense device.")); return; }
            command = service.getCharacteristic(COMMAND_UUID);
            BluetoothGattCharacteristic response = service.getCharacteristic(RESPONSE_UUID);
            if (command == null || response == null) { main.post(() -> listener.onError("NeuroSense BLE characteristics are missing.")); return; }
            current.setCharacteristicNotification(response, true);
            BluetoothGattDescriptor descriptor = response.getDescriptor(CCCD_UUID);
            String name = current.getDevice().getName();
            pendingDeviceName = name == null ? "NeuroSense" : name;
            if (descriptor == null) { main.post(() -> listener.onError("NeuroSense notification channel is unavailable.")); return; }
            if (Build.VERSION.SDK_INT >= 33) {
                int result = current.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (result != 0) main.post(() -> listener.onError("Could not enable NeuroSense responses."));
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!current.writeDescriptor(descriptor)) main.post(() -> listener.onError("Could not enable NeuroSense responses."));
            }
        }

        @Override public void onDescriptorWrite(BluetoothGatt current, BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) main.post(() -> listener.onConnectionChanged(true, pendingDeviceName));
            else main.post(() -> listener.onError("NeuroSense responses could not be enabled."));
        }

        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic, byte[] value) { consume(value); }
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) { consume(characteristic.getValue()); }
    };

    private void consume(byte[] value) {
        if (value == null) return;
        String part = new String(value, StandardCharsets.UTF_8);
        if (part.startsWith("#JSONBEGIN:")) { receivingJson = true; framedJson.setLength(0); return; }
        if ("#JSONEND".equals(part)) { receivingJson = false; String json = framedJson.toString(); main.post(() -> listener.onMessage(json)); return; }
        if (receivingJson) framedJson.append(part); else main.post(() -> listener.onMessage(part));
    }
}
