package com.example.yuan.ble_peripheral;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
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
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by YUAN on 2017/04/28.
 */

public class AdvertiserService extends Service {

    private static final String TAG = AdvertiserService.class.getSimpleName();
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private long TIMEOUT = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    public static boolean running = false;
    public static final String ADVERTISING_FAILED =
        "com.example.android.bluetoothadvertisements.advertising_failed";
    public static final String ADVERTISING_FAILED_EXTRA_CODE = "failureCode";
    private final String UUID1 = "38400000-8cf0-11bd-b23e-10b96e4efabc";
    private final String UUID2 = "38400000-8cf0-11bd-b23e-10b96e4efdef";
    private final String UUID3 = "38400000-8cf0-11bd-b23e-10b96eabcdef";
    public static final int ADVERTISING_TIMED_OUT = 6;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private Handler mHandler;
    private Runnable timeoutRunnable;

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onCreate() {
        running = true;
        initialize();
        startAdvertising();
        setTimeout();
        super.onCreate();
    }

    @Override public void onDestroy() {
        /**
         * Note that onDestroy is not guaranteed to be called quickly or at all. Services exist at
         * the whim of the system, and onDestroy can be delayed or skipped entirely if memory need
         * is critical.
         */
        running = false;
        stopAdvertising();
        mHandler.removeCallbacks(timeoutRunnable);
        stopForeground(true);
        super.onDestroy();
    }

    private void initialize() {
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        //mBluetoothAdapter = mBluetoothManager.getAdapter();

        if (mBluetoothLeAdvertiser == null) {
            BluetoothManager mBluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
                if (mBluetoothAdapter != null) {
                    mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                } else {
                    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.ble_not_supported), Toast.LENGTH_LONG)
                    .show();
            }
        }
    }

    private void startAdvertising() {
        goForeground();

        Log.d(TAG, "Service: Starting Advertising");

        AdvertiseSettings settings = buildAdvertiseSettings();
        AdvertiseData data = buildAdvertiseData();


        mGattServer = mBluetoothManager.openGattServer(this, callback);

        try {
            setupServices(mGattServer);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertising() {
        Log.d(TAG, "Service: Stopping Advertising");
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdvertiseCallback = null;
        }
    }

    public void setupServices(BluetoothGattServer gattServer) throws InterruptedException {
        if (gattServer == null) {
            throw new IllegalArgumentException("gattServer is null");
        }
        mGattServer = gattServer;
        // 设置一个GattService以及BluetoothGattCharacteristic
        {

            //controller
            BluetoothGattService gattService =
                new BluetoothGattService(UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d"),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);
            //alert level char.
            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                UUID.fromString(UUID1),
                BluetoothGattCharacteristic.PROPERTY_READ
                    | BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
                    | BluetoothGattCharacteristic.PERMISSION_WRITE);
            characteristic.setValue("0");
            gattService.addCharacteristic(characteristic);

            //owner
            BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(
                UUID.fromString(UUID2),
                BluetoothGattCharacteristic.PROPERTY_READ
                    | BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
                    | BluetoothGattCharacteristic.PERMISSION_WRITE);
            SharedPreferences sharedPreferences = getSharedPreferences("peripheral", 0);
            String owner = sharedPreferences.getString("owner", "none");
            characteristic2.setValue("000");
            gattService.addCharacteristic(characteristic2);

            //add
            BluetoothGattCharacteristic characteristic3 = new BluetoothGattCharacteristic(
                UUID.fromString(UUID3),
                BluetoothGattCharacteristic.PROPERTY_READ
                    | BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
                    | BluetoothGattCharacteristic.PERMISSION_WRITE);

            //String key = sharedPreferences.getString("key", "");
            characteristic2.setValue("");
            gattService.addCharacteristic(characteristic3);

            if (mGattServer != null && gattService != null) {
                mGattServer.addService(gattService);
            }
        }
    }

    private void goForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification n =
            new Notification.Builder(this).setContentTitle("Advertising device via Bluetooth")
                .setContentText("This device is discoverable to others nearby.")
                .setSmallIcon(R.drawable.peripheral)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(FOREGROUND_NOTIFICATION_ID, n);
    }

    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    private AdvertiseData buildAdvertiseData() {

        /**
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         *  This includes everything put into AdvertiseData including UUIDs, device info, &
         *  arbitrary service or manufacturer data.
         *  Attempting to send packets over this limit will result in a failure with error code
         *  AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         *  onStartFailure() method of an AdvertiseCallback implementation.
         */

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.addServiceUuid(Constants.Service_UUID);
        dataBuilder.setIncludeDeviceName(true);
        //dataBuilder.addServiceData(Constants.Service_UUID, toBytes(new boolean[] {false}));

        /* For example - this will cause advertising to fail (exceeds size limit) */
        //String failureData = "asdghkajsghalkxcjhfa;sghtalksjcfhalskfjhasldkjfhdskf";
        //dataBuilder.addServiceData(Constants.Service_UUID, failureData.getBytes());

        return dataBuilder.build();
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Peripheral Advertise Started.");
        }

        @Override public void onStartFailure(int errorCode) {
            Log.w(TAG, "Peripheral Advertise Failed: " + errorCode);
        }
    };

    private BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            //if (device.getBondState() == BluetoothDevice.BOND_NONE){
            //    device.createBond();
            //}
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,characteristic.getValue());

        }

        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
            BluetoothGattCharacteristic characteristic, boolean preparedWrite,
            boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                responseNeeded, offset, value);
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            Log.d(TAG, "onCharacteristicWriteRequest");
            if (characteristic.getUuid().toString().equals(UUID1)){
                try {
                    String sValue = new String(value, "UTF-8");
                    Log.d(TAG, "UUID1: " + sValue);
                    String username = sValue.split("/")[0];
                    SharedPreferences preferences = getSharedPreferences("peripheral", 0);
                    String owner = preferences.getString("owner", "");
                    String add = preferences.getString("add", "");
                    Log.d(TAG, "auth: " + owner + ".." + add);
                    if (username.equals(owner) || username.equals(add.substring(1))){

                        Intent intent = new Intent();
                        intent.setAction("WRITE");
                        intent.putExtra("value", sValue.split("/")[1]);
                        sendBroadcast(intent);
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            }else if (characteristic.getUuid().toString().equals(UUID2)){
                Log.d(TAG, "UUID2");
                SharedPreferences sharedPreferences = getSharedPreferences("peripheral", 0);
                if (sharedPreferences.getString("owner", "").equals("")){
                    try {
                        String sValue = new String(value, "UTF-8");
                        Intent intent = new Intent();
                        intent.setAction("OWNER");
                        intent.putExtra("value", sValue);
                        sendBroadcast(intent);
                        characteristic.setValue(value);
                        Log.d(TAG, "value: " + sValue);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }else if(characteristic.getUuid().toString().equals(UUID3)){
                try {
                    String sValue = new String(value, "UTF-8");
                    SharedPreferences preferences = getSharedPreferences("peripheral", 0);
                    String add = preferences.getString("add", "");
                    String owner = preferences.getString("owner", "");
                    Log.d(TAG, "---------------- " + sValue);
                    if (add=="" || add.charAt(0)!='/') {
                        Log.d(TAG, "onCharacteristicWriteRequest: 111");
                        if (add.length() < 25) {
                            Log.d(TAG, "onCharacteristicWriteRequest: 222");
                            Log.d(TAG, "add, sValue:" + add + ", " +sValue);
                            preferences.edit().putString("add", add + sValue).apply();
                            add = preferences.getString("add", "");

                            if (owner!="" && add.split("/")[0].equals(owner) && add.length()>25){
                                preferences.edit().putString("add", "/" + add.split("/")[1]).apply();
                                Log.d(TAG, preferences.getString("add", "QQ") + "333");
                            }
                        } else {
                            Log.d(TAG, "444");
                            preferences.edit().putString("add", "").apply();
                        }
                    }
                    //if (add != "" && add.charAt(0) != '/') {
                    //    Log.d(TAG, "add!=" + add);
                    //    preferences.edit().putString("add", add + sValue).apply();
                    //
                    //    add = preferences.getString("add", "");
                    //    String owner = preferences.getString("owner", "");
                    //
                    //    if (add.length() > 20 && (!owner.equals("")) && owner.equals(
                    //        add.split("/")[0])) {
                    //        Log.d(TAG, "add>20: " + add);
                    //        preferences.edit().putString("add", "/" + add.split("/")[1]).apply();
                    //    }else {
                    //        preferences.edit().putString("add", "").apply();
                    //    }
                    //}
                    //else {
                    //    Log.d(TAG, "add: " + add);
                    //    preferences.edit().putString("add", sValue).apply();
                    //}
                    //Intent intent = new Intent();
                    //intent.setAction("ADD");
                    //intent.putExtra("value", sValue);
                    //sendBroadcast(intent);
                    ////characteristic.setValue(value);
                    Log.d(TAG, "ADD: " + sValue);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void sendFailureIntent(int errorCode) {
        Intent failureIntent = new Intent();
        failureIntent.setAction(ADVERTISING_FAILED);
        failureIntent.putExtra(ADVERTISING_FAILED_EXTRA_CODE, errorCode);
        sendBroadcast(failureIntent);
    }

    private void setTimeout() {
        mHandler = new Handler();
        timeoutRunnable = new Runnable() {
            @Override public void run() {
                Log.d(TAG, "AdvertiserService has reached timeout of "
                    + TIMEOUT
                    + " milliseconds, stopping advertising.");
                sendFailureIntent(ADVERTISING_TIMED_OUT);
                stopSelf();
            }
        };
        mHandler.postDelayed(timeoutRunnable, TIMEOUT);
    }
}
