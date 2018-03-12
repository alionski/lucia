package se.mah.sensorteknik.aliona.lucia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 5868;
    private static final int REQUEST_FINE_LOCATION =888;
    private MainController mController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mController = new MainController(this);
        initUI();
        // The service is bound by the controller in its constructor, so I removed the binding here
    }

    @Override
    protected void onResume() {
        super.onResume();
        mController.initBluetooth();
    }

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
                mController.checkLocationAndScan();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.disconnect();
    }
}
