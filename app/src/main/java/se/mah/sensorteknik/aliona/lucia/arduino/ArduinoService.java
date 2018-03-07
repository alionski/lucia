package se.mah.sensorteknik.aliona.lucia.arduino;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

/**
 * Created by aliona on 2018-02-27.
 * http://nilhcem.com/android-things/bluetooth-low-energy
 * https://github.com/googlesamples/android-BluetoothLeGatt/blob/master/Application/src/main/java/com/example/android/bluetoothlegatt/BluetoothLeService.java
 *
 */

public class ArduinoService extends Service {
    private static final String TAG = ArduinoService.class.getSimpleName();
    // https://www.bluetooth.com/specifications/gatt/descriptors
    // org.bluetooth.descriptor.gatt.client_characteristic_configuration
    private static final UUID DESCRIPTOR_PROXIMITY_UUID = UUID.fromString(GattAttributes.PROXIMITY_CHARACTERISTICS_UUID);
    private static final UUID DESCRIPTOR_PHOTOCELL_UUID = UUID.fromString(GattAttributes.PHOTOCELL_CHARACTERISTICS_UUID);
    private final UUID SERVICE_UUID = UUID.fromString(GattAttributes.SERVICE_UUID);
    private final UUID PROXIMITY_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.PROXIMITY_CHARACTERISTICS_UUID);
    private final UUID LED_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.LED_CHARACTERISTICS_UUID);
    private final UUID PHOTOCELL_CHARACTERISTIC_UUID = UUID.fromString(GattAttributes.PHOTOCELL_CHARACTERISTICS_UUID);
    private final IBinder mBinder = new LocalBinder();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private Handler handler = new Handler();
    private ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC , ToneGenerator.MAX_VOLUME);
    private boolean beeping = false;
    private boolean beepingOn = false;
    private boolean darkOutside;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                BluetoothGattCharacteristic characteristicProximity = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PROXIMITY_CHARACTERISTICS_UUID);

                BluetoothGattCharacteristic charactersiticLight = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PHOTOCELL_CHARACTERISTIC_UUID);

                // Enable notifications for this characteristic locally
                gatt.setCharacteristicNotification(characteristicProximity, true);
                gatt.setCharacteristicNotification(charactersiticLight, true);

                // Write on the config descriptor to be notified when the value changes
                for (BluetoothGattDescriptor descriptor : characteristicProximity.getDescriptors()) {
                    if ((descriptor.getUuid().getMostSignificantBits() >> 32) == 0x2902) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }

                for (BluetoothGattDescriptor descriptor : charactersiticLight.getDescriptors()) {
                    if ((descriptor.getUuid().getMostSignificantBits() >> 32) == 0x2902) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (DESCRIPTOR_PROXIMITY_UUID.equals(descriptor.getUuid())) {
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PROXIMITY_CHARACTERISTICS_UUID);
                gatt.readCharacteristic(characteristic);
            } else if (DESCRIPTOR_PHOTOCELL_UUID.equals(descriptor.getUuid())) {
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PHOTOCELL_CHARACTERISTIC_UUID);
                gatt.readCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                manageResults(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            readCharacteristic(characteristic);
        }
    };


    private void manageResults(final BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid() == PROXIMITY_CHARACTERISTICS_UUID) {
            final int dataInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            Log.d(TAG, "Int value:" + dataInt);
            if (dataInt != 0) {
                if(beepingOn) {
                    playProximityBeep(dataInt);
                }
            }
        } else if (characteristic.getUuid() == PHOTOCELL_CHARACTERISTIC_UUID) {
            int dark = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            darkOutside = dark != 0;
            Log.i(TAG, "Is it dark? " + (dark == 0 ? "NO" : "YES"));
        }
    }

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();  // returns boolean
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Turns on or off the leds on the Arduino device.
     * @param on True or false for on or off.
     */
    public boolean ledsOnOff(boolean on) {
        if (on) {
            if (darkOutside) {
                writeLEDCharacteristic(1);
                return true;
            } else {
                return false;
            }
        } else {
            writeLEDCharacteristic(0);
            return false;
        }
    }

    /**
     * Turns on or off the proximity sensor on the Arduino device.
     * @param isOn True or false for on or off.
     */
    public void proximityOnOff(boolean isOn) {
        if(isOn) {
            writeProximityCharacteristic(1);
        } else {
            writeProximityCharacteristic(0);
        }
    }

    /**
     * Turns on or off the beeping sound, when objects are near, on the Android device.
     * @param isOn true or false for on or off.
     */
    public void beepingOnOff(boolean isOn) {
        beepingOn = isOn;
    }



    public class LocalBinder extends Binder {
        public ArduinoService getService() {
            return ArduinoService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        disconnect();
        close();
        return super.onUnbind(intent);
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    private void writeProximityCharacteristic(int onOff) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(SERVICE_UUID);
        if(mCustomService == null){
            Log.w(TAG, "Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(PROXIMITY_CHARACTERISTICS_UUID);
        mWriteCharacteristic.setValue(onOff, BluetoothGattCharacteristic.FORMAT_UINT16,0);
        boolean success;
        do {
            success = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
        } while (!success);
    }

    private void writeLEDCharacteristic(int onOff) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(SERVICE_UUID);
        if(mCustomService == null){
            Log.w(TAG, "Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(LED_CHARACTERISTICS_UUID);
        mWriteCharacteristic.setValue(onOff, BluetoothGattCharacteristic.FORMAT_UINT16,0);
        boolean success;
        do {
            success = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
        } while (!success);
    }

    /**
     * Plays a sound representing some proximity detected by the BLE device.
     * Plays a beep as long as one is already not playing.
     * The distance input determines the sleep after the beep.
     * @param distance Distance value from sensor
     */
    private void playProximityBeep(int distance) {
        if(!beeping) {
            beeping = true;
            final long interval = distance * 10;
            handler.post(new Runnable() {
                public void run() {
                    Log.d(TAG, "Beeping");
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 250);
                    try {
                        Thread.sleep(interval);
                        beeping = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
