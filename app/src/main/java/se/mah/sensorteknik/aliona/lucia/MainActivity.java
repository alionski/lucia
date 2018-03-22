package se.mah.sensorteknik.aliona.lucia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * The main activity, which contains both fragments.
 * Upon start, created a controller, which in its turn created a service.
 *
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;
    private MainController mController;

    /**
     * Standard method. Creates a controller and initialises the UI.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mController = new MainController(this);
        initUI();
        // The service is bound by the controller in its constructor, so I removed the binding here
    }

    /**
     * Sets the main fragment as the UI.
     */
    private void initUI() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        MainFragment mainFrag = MainFragment.newInstance();
        mainFrag.setListener(mController);
        transaction.add(R.id.fragment_container, mainFrag, null);
        transaction.commit();
    }

    /**
     * Displays the fragment containing information about the application's functionality.
     * Added to back stack for easy backwards navigation.
     */
    public void showInfoFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                InformationFragment.newInstance(), "infoFrag").addToBackStack(null).commit();
    }

    /**
     * Called if the app didn't have the access to location, after it has been determined in the MainController and
     * after a corresponding request activity has been shown.
     * If the permission is received and thee BT is on, instructs the MainController to start scanning for devices.
     * @param requestCode -- the code associated with the location request.
     * @param permissions -- what permissions have been granted.
     * @param grantResults -- whether the use gave the permission to access location.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mController.isBluetoothAdapterEnabled()) {
                    mController.startScan();
                }
            } else {
                Toast.makeText(this, "You didn't give permission to access device location, the app will not work", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Called if the app didn't have the access to Bluetooth, after it has been determined in the MainController and
     * after a corresponding request activity has been shown.
     * If the permission is received, instructs the MainController to check if the app has permission to access location and
     * start scanning for devices.
     * @param requestCode -- the code associated with the bluetooth request.
     * @param resultCode -- what permissions have been granted.
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                mController.checkLocationAndScan();
            }
        }
    }

    /**
     * Disconnects the app from the Arduino device before dying.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.disconnect();
    }
}
