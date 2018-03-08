package se.mah.sensorteknik.aliona.lucia;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;
import se.mah.sensorteknik.aliona.lucia.arduino.ArduinoService;

/**
 * Created by aliona on 2018-02-27.
 */

public class MainController implements MainFragment.OnFragmentInteractionListener {
    private final String TAG = MainController.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;
    private ArduinoService mBluetoothLeService;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private ScanCallback mScanCallback;
    private MainActivity mActivity;
    private boolean ledsOn = false;
    private boolean proximityOn = false;
    private boolean beepingOn = false;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((ArduinoService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
//                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            Log.i(TAG, "Starting connection");
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * Checks which button has been pressed in the MainFragment, and acts
     * accordingly.
     * @param command
     */
    @Override
    public void onFragmentInteraction(int command) {
        switch (command) {
            case MainFragment.TOGGLE_LEDS:
                ledsOn = !ledsOn;
                ledsOn = mBluetoothLeService.ledsOnOff(ledsOn);
                break;
            case MainFragment.TOGGLE_BEEPING:
                beepingOn = !beepingOn;
                mBluetoothLeService.beepingOnOff(beepingOn);
                break;
            case MainFragment.TOGGLE_DISTANCE_SENSOR:
                proximityOn = !proximityOn;
                mBluetoothLeService.proximityOnOff(proximityOn);
                break;
            case MainFragment.SHOW_INFO_FRAG:
                mActivity.showInfoFragment();
                break;
        }
    }

    public MainController(MainActivity activity) {
        mActivity = activity;
        initBluetooth();
        Intent gattServiceIntent = new Intent(mActivity, ArduinoService.class);
        mActivity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initBluetooth() {
        if (!mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(mActivity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            mActivity.finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.i(TAG, "On scan result");
                super.onScanResult(callbackType, result);
                if (("Lucia").equals(result.getScanRecord().getDeviceName())) {
                    mDeviceAddress = result.getDevice().getAddress();
                    startService(result.getDevice().getAddress());
                    Log.i("DEVICE", callbackType + " " + result);
                    Log.i("LUCIA FOUND, ADDRESS", result.getDevice().getAddress());
                    mBLEScanner.stopScan(mScanCallback);
                }
            }
        };

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (ContextCompat.checkSelfPermission(mActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                startScan();
            } else {
                ActivityCompat.requestPermissions(mActivity, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_FINE_LOCATION);
            }
        }
    }

    private void startService(String address) {
        if (mBluetoothLeService != null) {
            Log.i(TAG, "Connecting to device");
            mBluetoothLeService.connect(address);
        } else {
            Log.i(TAG, "Service is null FAILURE");
        }
    }

    public void startScan() {
        Log.i(TAG, "Starting scan");
//        ScanSettings settings = new ScanSettings.Builder()
//                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//                .setReportDelay(1)
//                .build();

//        ScanFilter scanFilter = new ScanFilter.Builder()
//                .setServiceUuid(ParcelUuid.fromString(GattAttributes.SERVICE_UUID)).build();
        mBLEScanner.startScan(mScanCallback);
    }

    public boolean isBluetoothAdapterEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    public void disconnect() {
        mActivity.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
}
