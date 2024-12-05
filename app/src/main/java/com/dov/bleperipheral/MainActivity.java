package com.dov.bleperipheral;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;
    private static final String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private BluetoothManager bluetoothManager;
    private BluetoothGattServer gattServer;
    private TextView bleNameTextView;
    private TextView signalStrengthTextView;
    private TextView batteryLevelTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bleNameTextView = findViewById(R.id.bleName);
        signalStrengthTextView = findViewById(R.id.signalStrength);
        batteryLevelTextView = findViewById(R.id.batteryLevel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
        } else {
            setupBLE();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                setupBLE();
            } else {
                Log.e("BLE", "Permissions denied");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void setupBLE() {
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothAdapter.setName("BLE Client");

        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Log.e("BLE", "BLE advertising not supported");
            Toast.makeText(this, "BLE advertising not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        @SuppressLint("MissingPermission") String deviceName = bluetoothAdapter.getName();

        bleNameTextView.setText("BLE Name: " + deviceName);

        signalStrengthTextView.setText("Signal Strength: Not Available (Peripheral Mode)");
        batteryLevelTextView.setText("Battery Level: 80%"); // Example dummy value

        startAdvertising();
        setupGattServer();
    }

    @SuppressLint("MissingPermission")


    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            String advertisedName = "BLE Client";

            bluetoothAdapter.getBluetoothLeAdvertiser().startAdvertising(
                    new AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                            .setConnectable(true)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                            .build(),
                    new AdvertiseData.Builder()
                            .setIncludeDeviceName(false) // Exclude the default Bluetooth device name
                            .addServiceData(
                                    new ParcelUuid(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")),
                                    advertisedName.getBytes()
                            ) // Embed the custom name in service data
                            .build(),
                    new AdvertiseCallback() {
                        @Override
                        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                            Log.d("BLE", "Advertising started successfully");
                        }

                        @Override
                        public void onStartFailure(int errorCode) {
                            Log.e("BLE", "Advertising failed: " + errorCode);
                        }
                    });
        } else {
            Log.e("BLE", "Bluetooth adapter not available or not enabled");
        }
    }



    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        gattServer = bluetoothManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                Log.d("BLE", "Connection state changed: " + newState);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                if (characteristic.getUuid().equals(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{(byte) 80}); // Example: Battery level 80%
                }
            }
        });

        BluetoothGattCharacteristic batteryLevelCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService service = new BluetoothGattService(
                UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        service.addCharacteristic(batteryLevelCharacteristic);
        gattServer.addService(service);
    }
}
