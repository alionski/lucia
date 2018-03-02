package se.mah.sensorteknik.aliona.lucia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements MainFragment.OnFragmentInteractionListener{
    private boolean connectedToBle;

    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;
    private MainController mController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mController = new MainController(this);
        initUI();

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

    private void initUI() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        MainFragment mainFrag = MainFragment.newInstance();
        transaction.add(R.id.fragment_container, mainFrag, null);
        transaction.commit();
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    //TODO: Move to Service when possible
    /**
     * Plays a sound representing some proximity detected by the BLE device.
     * @param distance Distance value from sensor
     */
    private void playProximityBeep(int distance) {
        final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC , ToneGenerator.MAX_VOLUME);
        final long interval = distance * 2;
        Handler handler = new Handler();
        handler.post(new Runnable() {
            public void run() {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 250);
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 250);
            }
        });
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
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                mController.startScan();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.disconnect();
    }
}
