package com.example.yuan.ble_peripheral;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import static com.example.yuan.ble_peripheral.AdvertiserService.ADVERTISING_FAILED;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 11;
    private static final int REQUEST_ENABLE_BT = 12;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mBroadcast = false;
    private BroadcastReceiver advertisingFailureReceiver;
    private TextView txtView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // android 6.0 up 權限
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect bluetooth. ");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener(){
                    @Override
                    public void onDismiss(DialogInterface dialog){
                        ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, "Not support", Toast.LENGTH_LONG).show();
            finish();
        }
        mBluetoothAdapter.setName("Peripheral");

        txtView = (TextView) findViewById(R.id.txtView);

        advertisingFailureReceiver = new BroadcastReceiver() {

            /**
             * Receives Advertising error codes from {@code AdvertiserService} and displays error messages
             * to the user. Sets the advertising toggle to 'false.'
             */
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive");
                if (intent.getAction() == ADVERTISING_FAILED) {
                    int errorCode =
                        intent.getIntExtra(AdvertiserService.ADVERTISING_FAILED_EXTRA_CODE, -1);

                    mBroadcast = false;

                    String errorMessage = getString(R.string.start_error_prefix);
                    switch (errorCode) {
                        case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                            errorMessage += " " + getString(R.string.start_error_already_started);
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                            errorMessage += " " + getString(R.string.start_error_too_large);
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                            errorMessage += " " + getString(R.string.start_error_unsupported);
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                            errorMessage += " " + getString(R.string.start_error_internal);
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                            errorMessage += " " + getString(R.string.start_error_too_many);
                            break;
                        case AdvertiserService.ADVERTISING_TIMED_OUT:
                            errorMessage = " " + getString(R.string.advertising_timedout);
                            break;
                        default:
                            errorMessage += " " + getString(R.string.start_error_unknown);
                    }

                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }else if(intent.getAction().equals("WRITE")){
                    Log.d(TAG, "write");
                    String value = intent.getStringExtra("value");
                    if (value.equals("0")){
                        txtView.setBackgroundColor(Color.RED);
                    }else if (value.equals("1")){
                        txtView.setBackgroundColor(Color.GREEN);
                    }
                }else if(intent.getAction().equals("OWNER")){
                    Log.d(TAG, "owner");
                    SharedPreferences preferences = getSharedPreferences("peripheral", 0);

                    if (preferences.getString("owner", "").equals("")){
                        String value = intent.getStringExtra("value");
                        preferences.edit()
                            .putString("owner", value)
                            .apply();
                    }
                }else if (intent.getAction().equals("ADD")){
                    String value = intent.getStringExtra("value");
                    Log.d(TAG, "ADD: " + value);
                    //SharedPreferences preferences = getSharedPreferences("peripheral", 0);
                    //
                    //if (preferences.getString("key", "").length() < 30){
                    //    String value = intent.getStringExtra("value");
                    //    String key = preferences.getString("key", "");
                    //    preferences.edit()
                    //        .putString("key", key + value)
                    //        .apply();
                    //    key = preferences.getString("key", "");
                    //    if (key.length() > 15){
                    //        if (key.charAt(0) != '*' || key.charAt(key.length()) != '*'){
                    //            preferences.edit()
                    //                .putString("key", "")
                    //                .apply();
                    //        }
                    //    }
                    //}

            }
            }
        };
    }

    @Override protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        IntentFilter failureFilter = new IntentFilter(ADVERTISING_FAILED);
        failureFilter.addAction("WRITE");
        failureFilter.addAction("OWNER");
        failureFilter.addAction("ADD");
        registerReceiver(advertisingFailureReceiver, failureFilter);
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(advertisingFailureReceiver);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        Log.d(TAG, "onCreateOptionsMenu");
        if (mBroadcast){
            menu.findItem(R.id.menu_broadcast).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        }else{
            menu.findItem(R.id.menu_broadcast).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(MainActivity.this, AdvertiserService.class);
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()){
            case R.id.menu_broadcast:
                this.startService(intent);
                mBroadcast = true;
                invalidateOptionsMenu();
                txtView.setBackgroundColor(Color.RED);
                return true;
            case R.id.menu_stop:
                this.stopService(intent);
                mBroadcast = false;
                invalidateOptionsMenu();
                txtView.setBackgroundColor(Color.WHITE);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
