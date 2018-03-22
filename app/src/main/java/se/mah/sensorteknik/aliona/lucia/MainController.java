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
 *
 * The Main Controller, which is created once, when the MainActivity is created.
 * it first starts the service, then initialises bluetooth and scans for Arduino devices.
 * Once it has found Lucia, it tells the service to connect, after which all communication with Arduino
 * is handled by the service.
 */

public class MainController implements MainFragment.OnFragmentInteractionListener {
    private final String TAG = MainController.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;
    private ArduinoService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private ScanCallback mScanCallback;
    private MainActivity mActivity;
    private boolean ledsOn = false;
    private boolean proximityOn = false;
    private boolean beepingOn = false;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        /**
         * Called when the service is ready and binds to the activity.
         * After that, we can safely initialise the bluetooth and start the scan.
         * @param componentName -- unused
         * @param service -- reference to the ArduinoService
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((ArduinoService.LocalBinder) service).getService();
            initBluetooth(); // init bluetooth only after the service is ready
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * Constructor. Binds the service.
     * @param activity -- reference to the MainActivity.
     */
    public MainController(MainActivity activity) {
        mActivity = activity;
        Intent gattServiceIntent = new Intent(mActivity, ArduinoService.class);
        mActivity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Checks which button has been pressed in the MainFragment, and acts
     * accordingly.
     * @param command -- the command associated with the button pressed.
     */
    @Override
    public void onFragmentInteraction(int command) {
        switch (command) {
            case MainFragment.TOGGLE_LEDS:
                ledsOn = mBluetoothLeService.ledsOnOff(!ledsOn);
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

    /**
     * Called from MainActivity's onResume.
     * If BT is off, a dialog is shown, and when the user clicks Ok, checkLocationAndScan is called.
     * If BT is on, calls checkLocationAndScan, which
     */
    public void initBluetooth() {
        if (!mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(mActivity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            mActivity.finish();
        }

        mBluetoothManager =
                (BluetoothManager) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothManager.getAdapter() == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkLocationAndScan();
        }
    }

    /**
     * Called either from here or MainActivity after BT is switched on.
     * Checks if the app has all the needed permissions.
     */
    public void checkLocationAndScan() {
        if (ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            startScan();
        } else {
            ActivityCompat.requestPermissions(mActivity, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION);
        }
    }

    /**
     * Called from startScan(). After this method is called, the service controls all communication with
     * the Arduino.
     */
    private void connectServiceToArduino() {
        if (mBluetoothLeService != null) {
            Log.i(TAG, "Connecting to device");
            mBluetoothLeService.connect(mBluetoothAdapter, mDeviceAddress);
        } else {
            Log.i(TAG, "Service is null FAILURE");
        }
    }

    /**
     * Starts scanning for available BT devices, only looking for Lucia.
     * Quits after it's found it, and instructs the service to connect to the Lucia device.
     * Called either from checkLocationAndScan() or MainActivity after the user gave permission to use location.
     */
    public void startScan() {
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.i(TAG, "On scan result");
                super.onScanResult(callbackType, result);
                if (("Lucia").equals(result.getScanRecord().getDeviceName())) {

                    mDeviceAddress = result.getDevice().getAddress();
                    connectServiceToArduino();
                    mBLEScanner.stopScan(mScanCallback);
                    Log.i("DEVICE", callbackType + " " + result);
                    Log.i("LUCIA FOUND, ADDRESS: ", result.getDevice().getAddress());
                }
            }
        };
        mBLEScanner.startScan(mScanCallback);
        Log.i(TAG, "Starting scan");
    }

    /**
     * Called from MainActivity after it receives the results of the locations permission request activity.
     * @return true if BT is enabled, false otherwise
     */
    public boolean isBluetoothAdapterEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    /**
     * Called from MainActivity just before the app dies.
     */
    public void disconnect() {
        if (mServiceConnection != null) {
            mActivity.unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
    }
}
