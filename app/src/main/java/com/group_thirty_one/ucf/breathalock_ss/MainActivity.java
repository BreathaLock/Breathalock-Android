package com.group_thirty_one.ucf.breathalock_ss;

import android.app.Activity;
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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    // Service Constants
    private static final String DEVICE_MAC_ADDR = "FD:A9:AC:D0:46:2D";
    private static final String DEVICE_NAME = "Breathalock";
    public static final String UUID_SERVICE     = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX          = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    private static String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private ScanFilter filterSettings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private int mBluetoothState;

    protected BluetoothGattService mUartService;
    protected BluetoothGattCharacteristic mDataCharacteristic;

    private TextView mBleStatus;
    private ImageView mBleImgStatus;
    private TextView mSignalStatus;
    private ImageView mSignalImgStatus;
    private TextView mSensorDisplay;

    private boolean signalStatusUnlocked = false;
    private double mSensorValueRead = 0.00;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBleStatus = (TextView) findViewById(R.id.settings_ble_summary);
        mBleImgStatus = (ImageView) findViewById(R.id.settings_ble_img);

        mSignalStatus = (TextView) findViewById(R.id.settings_door_status);
        mSignalImgStatus = (ImageView) findViewById(R.id.settings_door_status_img);

        mSensorDisplay = (TextView) findViewById(R.id.sensor_display_textview);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }
    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    public void updateUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mBluetoothState == BluetoothProfile.STATE_CONNECTED) {
                    mBleImgStatus.setImageResource(R.drawable.ic_status_ble_connected);
                    mBleStatus.setText(R.string.settings_ble_connection_status_connected);
                }else if( mBluetoothState == BluetoothProfile.STATE_DISCONNECTED) {
                    mBleImgStatus.setImageResource(R.drawable.ic_status_ble_disconnected);
                    mBleStatus.setText(R.string.settings_ble_connection_status_disconnected);
                    mSensorValueRead=0.00;
                    signalStatusUnlocked = false;
                }

                if(signalStatusUnlocked == true) {
                    mSignalStatus.setText(R.string.settings_door_status_enabled);
                    mSignalImgStatus.setImageResource(R.drawable.ic_status_unlocked);
                }else if (signalStatusUnlocked == false) {
                    mSignalStatus.setText(R.string.settings_door_status_disabled);
                    mSignalImgStatus.setImageResource(R.drawable.ic_status_locked);
                }

                mSensorDisplay.setText(String.valueOf(mSensorValueRead));

            }
        });
    }

//    protected void onResume() {
//        super.onResume();
//        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//    }

    public void uberSelection(View v) {
        Log.i("uberSelection", "uberSelection Successful");
        //@see: https://stackoverflow.com/questions/35913628/open-uber-app-from-my-app-android
        PackageManager pm = getPackageManager();

        Log.d("UberSelection", "clicked!");
        try {
            pm.getPackageInfo("com.ubercab", PackageManager.GET_ACTIVITIES);
            String uri = "uber://?action=setPickup&pickup=my_location";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(uri));
            startActivity(intent);
        } catch (PackageManager.NameNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ubercab")));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=com.ubercab")));
            }
        }
    }

    public void bleSelection(View v) {
        Log.d("bleButton", "clicked!");
        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filterSettings = new ScanFilter.Builder()
                    .setDeviceAddress(DEVICE_MAC_ADDR)
                    .build();
            filters = new ArrayList<ScanFilter>();

            filters.add(filterSettings);
        }
        scanLeDevice(true);

    }
    //Bluetooth
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            Log.i("onScanResult:", btDevice.getName());
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        Log.d("connectToDevice", device.toString());
        if (mGatt == null) {
            Log.d("connectToDevice-cond", device.toString());
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            mBluetoothState = newState;
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    mGatt.disconnect();
                    mGatt=null;
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }
            updateUi();

        }
        private BluetoothGattService getGattService(String Uuid) {
            if(mGatt != null) {
                final UUID serviceUuid = UUID.fromString(Uuid);
                return mGatt.getService(serviceUuid);
            }else {
                return null;
            }
        }

        private void enableRxNotifications() {
            //service = BluetoothGattService mUartService, UUID = UUID_RX, boolean = true;
            final UUID characteristicUuid = UUID.fromString(UUID_RX);
            mDataCharacteristic = mUartService.getCharacteristic(characteristicUuid);

            final UUID clientCharacteristicConfig = UUID.fromString(CHARACTERISTIC_CONFIG);
            final BluetoothGattDescriptor config = mDataCharacteristic.getDescriptor(clientCharacteristicConfig);

            mGatt.setCharacteristicNotification(mDataCharacteristic,true);
            config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(config);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mUartService = getGattService(UUID_SERVICE);
            enableRxNotifications();
        }

        @Override
        // Characteristic notification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
//            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            final byte[] recBytes = characteristic.getValue();
            final String recBytesToStr = bytesToText(recBytes,false);
            final String[] parsedString = recBytesToStr.split(":");
            if(parsedString[1].compareToIgnoreCase("F") == 0) {
                signalStatusUnlocked = false;
            }else if (parsedString[1].compareToIgnoreCase("P") == 0) {
                signalStatusUnlocked = true;
            }
            mSensorValueRead = Double.parseDouble(parsedString[0]);
            Log.d("onCharacteristicChanged", parsedString[0]);
            Log.d("onCharacteristicChanged", parsedString[1]);
            Log.i("onCharacteristicChanged", characteristic.toString());
            Log.i("onCharacteristicChanged", recBytesToStr);
            updateUi();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            gatt.disconnect();
        }
        private String bytesToText(byte[] bytes, boolean simplifyNewLine) {
            String text = new String(bytes, Charset.forName("UTF-8"));
            if (simplifyNewLine) {
                text = text.replaceAll("(\\r\\n|\\r)", "\n");
            }
            return text;
        }

    };
}
