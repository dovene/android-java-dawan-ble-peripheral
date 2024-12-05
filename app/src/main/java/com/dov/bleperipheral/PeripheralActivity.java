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
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.S)
public class PeripheralActivity extends AppCompatActivity {
    private static final String TAG = "BLEPeripheral";
    private static final UUID SERVICE_UUID = UUID.fromString("00000000-1111-2222-3333-444444444444");
    private static final UUID BATTERY_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fc");
    private static final UUID HUMIDITY_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fd");
    private int batteryLevel = 80;
    private int temperatureLevel = 25;
    private int humidityLevel = 60;

    private static final int REQUEST_PERMISSIONS = 1;
    private static final String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothManager bluetoothManager;
    private TextView statusText;
    private boolean isAdvertising = false;
    private TextView informationsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        statusText = findViewById(R.id.statusText);
        informationsText = findViewById(R.id.informations_tv);
        informationsText.setText("Batterie : " + batteryLevel + "%" + "\nTempérature : " + temperatureLevel + "°C" + "\nHumidité : " + humidityLevel + "%");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Demander les permissions
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
        } else {
            //Si les permissions sont déjà accordées, démarrer l'advertising
            setupBLEAdvertising();
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
                //Démarrer l'advertising
                setupBLEAdvertising();
            } else {
                Log.e(TAG, "Permissions denied");
                statusText.setText("Permissions refusées. BLE advertising impossible.");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void setupBLEAdvertising() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            statusText.setText("Bluetooth is not available or enabled");
            return;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            statusText.setText("BLE Advertising not supported on this device");
            return;
        }

        startAdvertising();
        setupGattServer();
    }

    @SuppressLint("MissingPermission")
    private void startAdvertising() {
        if (isAdvertising) {
            Log.w(TAG, "Advertising already started");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(10000)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);

        isAdvertising = true;
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }

        advertiser.stopAdvertising(advertiseCallback);
        isAdvertising = false;
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(TAG, "BLE Advertising started successfully");
            runOnUiThread(() -> statusText.setText("BLE Advertising Started"));
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "BLE Advertising failed with error code: " + errorCode);
            runOnUiThread(() -> statusText.setText("Advertising Failed: " + errorCode));
            isAdvertising = false;
        }
    };

    // Initialiser le serveur GATT
    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        gattServer = bluetoothManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                Log.d(TAG, "Connection state changed: device=" + device + ", status=" + status + ", newState=" + newState);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Device connected");
                        runOnUiThread(() ->
                        statusText.setText("Device connected: " + device.getAddress()));
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Device disconnected");
                        runOnUiThread(() -> {
                                    statusText.setText("Device disconnected" + device.getAddress());
                                    stopAdvertising();
                                    startAdvertising();}
                               );
                    }
                } else {
                    Log.e(TAG, "Connection error: status=" + status);
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                if (characteristic.getUuid().equals(BATTERY_CHARACTERISTIC_UUID)) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{(byte) batteryLevel}); // Example: Battery level 80%
                }
                if (characteristic.getUuid().equals(TEMPERATURE_CHARACTERISTIC_UUID)) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{(byte) temperatureLevel}); // Example: Battery level 80%
                }
                if (characteristic.getUuid().equals(HUMIDITY_CHARACTERISTIC_UUID)) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{(byte) humidityLevel}); // Example: Battery level 80%
                }
            }
        });

        BluetoothGattCharacteristic batteryCharacteristic = new BluetoothGattCharacteristic(
                BATTERY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        BluetoothGattCharacteristic tempCharacteristic  = new BluetoothGattCharacteristic(
                TEMPERATURE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );
        BluetoothGattCharacteristic humidityCharacteristic = new BluetoothGattCharacteristic(
                HUMIDITY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService service = new BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        service.addCharacteristic(batteryCharacteristic);
        service.addCharacteristic(tempCharacteristic);
        service.addCharacteristic(humidityCharacteristic);
        gattServer.addService(service);
    }
}