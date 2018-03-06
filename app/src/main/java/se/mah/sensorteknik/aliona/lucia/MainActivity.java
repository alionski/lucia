package se.mah.sensorteknik.aliona.lucia;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import se.mah.sensorteknik.aliona.lucia.services.SensorService;
import se.mah.sensorteknik.aliona.lucia.services.SensorServiceListener;

public class MainActivity extends AppCompatActivity implements
        MainFragment.OnFragmentInteractionListener,
        SensorServiceListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;

    private MainController mController;

    private SensorService boundSensorService;
    private boolean isSensorServiceBound;

    private ServiceConnection sensorServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            boundSensorService = ((SensorService.LocalBinder) binder).getService();
            boundSensorService.setSensorServiceListener(MainActivity.this);
            Log.d(TAG, "onServiceConnected:SensorService is connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundSensorService.setSensorServiceListener(null);
            boundSensorService = null;
            Log.d(TAG, "onServiceDisconnected:SensorService is disconnected");
        }
    };

    private boolean connectedToBle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mController = new MainController(this);
        initUI();

        startService(new Intent(MainActivity.this, SensorService.class));
        doBindService();
        Log.d(TAG, "onCreate:SensorService started");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bleDeviceReceiver, getGATTReceiverFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bleDeviceReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.disconnect();
        doUnbindService();
    }

    private void initUI() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        MainFragment mainFrag = MainFragment.newInstance();
        mainFrag.setListener(mController);
        transaction.add(R.id.fragment_container, mainFrag, null);
        transaction.commit();
    }

    private void doBindService() {
        bindService(new Intent(MainActivity.this, SensorService.class),
                sensorServiceConnection, Context.BIND_AUTO_CREATE);

        isSensorServiceBound = true;
    }

    private void doUnbindService() {
        if (isSensorServiceBound) {
            unbindService(sensorServiceConnection);
            isSensorServiceBound = false;
        }
    }

    @Override
    public void onFragmentInteraction(int command) {

    }

    /**
     * BroadcastReceiver listening for Broadcasts set with getGattReceiverFilter().
     */
    private final BroadcastReceiver bleDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch(action) {
//                case ArduinoService.ACTION_GATT_CONNECTED:
//                    connectedToBle = true;
//                    break;
//                case ArduinoService.ACTION_GATT_DISCONNECTED:
//                    connectedToBle = false;
//                    break;
//                case AduinoService.ACTION_GATT_SERVICES_DISCOVERED:
//                    break;
//                case ArduinoService.ACTION_DATA_AVAILABLE:
//                    break;
            }
        }
    };

    /**
     * Returns an intentFilter with the actions belonging to BluetoothLeService
     * added. Must be set to listen for proper broadcasts.
     * @return IntentFilter with BluetoothLeService actions.
     */
    private IntentFilter getGATTReceiverFilter() {
        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
//        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mController.isBluetoothAdapterEnabled()) {
                    mController.startScan();
                }
            } else {
                Toast.makeText(this, "You didn't give permission to access device location", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                mController.startScan();
            }
        }
    }

    @Override
    public void onLightSensorChanged(float light) {

    }
}
