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
import java.util.UUID;

public final class NeuroSenseBleManager {
    interface Listener {
        void onScanResult(BluetoothDevice device, int rssi);
        void onConnectionChanged(boolean connected, String deviceName);
        void onMessage(String json);
        void onError(String message);
    }

    private static final UUID SERVICE_UUID = UUID.fromString("7b1e0001-7f34-4fd8-a912-6c38ef4a5201");
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
    private boolean scanning;
    private String deviceName = "NeuroSense";

    NeuroSenseBleManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    boolean isBluetoothAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    boolean isConnected() {
        return gatt != null && command != null;
    }

    @SuppressLint("MissingPermission")
    void startScan() {
        if (!isBluetoothAvailable()) {
            listener.onError("Turn on Bluetooth to find NeuroSense.");
            return;
        }
        stopScan();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onError("Bluetooth scanner is unavailable.");
            return;
        }
        scanning = true;
        scanner.startScan(scanCallback);
        main.postDelayed(this::stopScan, 10_000);
    }

    @SuppressLint("MissingPermission")
    void stopScan() {
        if (scanning && scanner != null) scanner.stopScan(scanCallback);
        scanning = false;
    }

    @SuppressLint("MissingPermission")
    void connect(BluetoothDevice device) {
        stopScan();
        disconnect();
        deviceName = device.getName() == null ? "NeuroSense" : device.getName();
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
    }

    @SuppressLint("MissingPermission")
    void disconnect() {
        command = null;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    boolean send(JSONObject payload) {
        if (!isConnected()) {
            listener.onError("Connect NeuroSense first.");
            return false;
        }
        byte[] value = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 33) {
            return gatt.writeCharacteristic(command, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0;
        }
        command.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        command.setValue(value);
        return gatt.writeCharacteristic(command);
    }

    void sendType(String type) {
        try {
            send(new JSONObject().put("type", type));
        } catch (Exception error) {
            listener.onError(error.getMessage());
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.startsWith("NeuroSense")) {
                main.post(() -> listener.onScanResult(device, result.getRssi()));
            }
        }

        @Override public void onScanFailed(int errorCode) {
            main.post(() -> listener.onError("Bluetooth scan failed (" + errorCode + ")."));
        }
    };

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
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
            BluetoothGattService service = current.getService(SERVICE_UUID);
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                main.post(() -> listener.onError("This is not a compatible NeuroSense device."));
                return;
            }
            command = service.getCharacteristic(COMMAND_UUID);
            BluetoothGattCharacteristic response = service.getCharacteristic(RESPONSE_UUID);
            if (command == null || response == null) {
                main.post(() -> listener.onError("NeuroSense Bluetooth controls are missing."));
                return;
            }
            current.setCharacteristicNotification(response, true);
            BluetoothGattDescriptor descriptor = response.getDescriptor(CCCD_UUID);
            if (descriptor == null) {
                main.post(() -> listener.onError("Could not enable device responses."));
                return;
            }
            if (Build.VERSION.SDK_INT >= 33) {
                current.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                current.writeDescriptor(descriptor);
            }
        }

        @Override public void onDescriptorWrite(BluetoothGatt current, BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                main.post(() -> listener.onConnectionChanged(true, deviceName));
            } else {
                main.post(() -> listener.onError("Could not enable device responses."));
            }
        }

        @Override public void onCharacteristicChanged(
                BluetoothGatt current,
                BluetoothGattCharacteristic characteristic,
                byte[] value
        ) {
            deliver(value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) {
            deliver(characteristic.getValue());
        }
    };

    private void deliver(byte[] value) {
        if (value == null) return;
        String json = new String(value, StandardCharsets.UTF_8);
        main.post(() -> listener.onMessage(json));
    }
}
