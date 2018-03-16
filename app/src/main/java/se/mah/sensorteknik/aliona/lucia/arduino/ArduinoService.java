package se.mah.sensorteknik.aliona.lucia.arduino;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
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
    private static final int ON = 1;
    private static final int OFF = 0;
    private static final boolean DARK = true;
    private static final boolean BRIGHT = false;
    private static final int BRIGHTNESS_THRESHOLD = 250;

    private final UUID SERVICE_UUID = UUID.fromString(GattAttributes.SERVICE_UUID);
    private static final UUID DESCRIPTOR_PROXIMITY_UUID = UUID.fromString(GattAttributes.PROXIMITY_CHARACTERISTICS_UUID);
    private static final UUID DESCRIPTOR_PHOTOCELL_UUID = UUID.fromString(GattAttributes.PHOTOCELL_CHARACTERISTICS_UUID);
    private static final UUID VIBRATION_MOTOR_UUID = UUID.fromString(GattAttributes.VIBRATION_CHARACTERISTIC_UUID);
    private static final UUID PROXIMITY_READINGS_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.PROXIMITY_READINGS_CHARACTERISTC_UUID);
    private final UUID PROXIMITY_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.PROXIMITY_CHARACTERISTICS_UUID);
    private final UUID LED_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.LED_CHARACTERISTICS_UUID);
    private final UUID PHOTOCELL_CHARACTERISTIC_UUID = UUID.fromString(GattAttributes.PHOTOCELL_CHARACTERISTICS_UUID);
    private final IBinder mBinder = new LocalBinder();
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private Handler handler = new Handler();
    private ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC , ToneGenerator.MAX_VOLUME);
    private TextToSpeech mTTS;
    private boolean beeping = false;
    private boolean beepingOn = false;
    private boolean ledsControlOn = true;
    private boolean darkOutside = false;
    private boolean ledsOn = false;
    private OnTextToSpeechListener onTextToSpeechListener = new OnTextToSpeechListener();
    private int[] brightnessSamples = new int[10];
    private int index = 0;

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

                // Enable notifications for this characteristic locally
                gatt.setCharacteristicNotification(characteristicProximity, true);

                // Write on the config descriptor to be notified when the value changes
                for (BluetoothGattDescriptor descriptor : characteristicProximity.getDescriptors()) {
                    Log.d(TAG, "REGISTERING FOR PROXIMITY UPDATES");
                    Log.d(TAG, "DESCRIPTOR PROXIMITY UUID: " + descriptor.getUuid());
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
            Log.d(TAG, "DESCRIPTOR WRITE UUID: " + descriptor.getUuid());
            if (DESCRIPTOR_PROXIMITY_UUID.equals(descriptor.getCharacteristic().getUuid())) {
                Log.d(TAG, "DESCRIPTOR PROXIMITY WRITE");
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PROXIMITY_CHARACTERISTICS_UUID);
                gatt.readCharacteristic(characteristic);
                Thread thread = new Thread( new PhotocellReader(gatt));
                thread.start();
            }
            if (DESCRIPTOR_PHOTOCELL_UUID.equals(descriptor.getCharacteristic().getUuid())) {
                Log.d(TAG, "DESCRIPTOR PHOTOCELL WRITE");
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
            Log.d(TAG, "GATT READ STATUS: " +BluetoothGatt.GATT_SUCCESS );
            if (status == BluetoothGatt.GATT_SUCCESS) {
                manageResults(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            readCharacteristic(characteristic);
            Log.d(TAG, "CHARACTERISTIC CHANGED: " + characteristic.getUuid());
        }
    };


    private void manageResults(final BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "CHAR UUID"+characteristic.getUuid());
        if (characteristic.getUuid().equals(PROXIMITY_CHARACTERISTICS_UUID)) {
            final int dataInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            Log.d(TAG, "PROXIMITY VALUE: " + dataInt);
            if (dataInt != 0) {
                if(beepingOn) {
                    playProximityBeep(dataInt);
                }
            }
        } else if (characteristic.getUuid().equals(PHOTOCELL_CHARACTERISTIC_UUID)) {
            int brightness = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            if (index <= brightnessSamples.length) {
                brightnessSamples[index++] = brightness;
                if (index == brightnessSamples.length) {
                    calculateBrightness();
                }
            }
            Log.i(TAG, "PHOTOCELL VALUE: " + brightness);
        }
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
     * Called only when the photocell voltage array is full and we can calculate the current average
     * and decide whether there's need to switch on LEDs.
     */
    private void calculateBrightness() {
        int sum = 0;
        for (int i=0; i<brightnessSamples.length; i++) {
            sum += brightnessSamples[i];
        }
        int average = sum / brightnessSamples.length;
        Log.i(TAG, "PHOTOCELL AVERAGE: " + average);
        if (average < BRIGHTNESS_THRESHOLD) {
            if (ledsControlOn) {
                if (!darkOutside) {
                    brightnessChanged(DARK);
                }
            }
            darkOutside = DARK;
        } else {
            if (darkOutside) {
                brightnessChanged(BRIGHT);
            }
            darkOutside = BRIGHT;
        }
        index = 0;
    }

    /**
     * Method called from calulateBrightness() is the new average of light values is different from the current -->
     * the user might want switch on or off the lights.
     * @param brightness
     */
    private void brightnessChanged(boolean brightness) {
        if (brightness == DARK && !ledsOn) {
                mTTS = new TextToSpeech(this,
                        new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if (status == TextToSpeech.SUCCESS) {
                                    int result = mTTS.setLanguage(Locale.US);
                                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                        Log.v(TAG, "Language is not available.");
                                    } else {
                                        mTTS.speak("It is dark outside. You might want to switch on the lights",
                                                TextToSpeech.QUEUE_ADD, null,
                                                null);
                                    }
                                } else {
                                    Log.v(TAG, "Could not initialize TextToSpeech.");
                                }
                            }
                        }
                );
                mTTS.setOnUtteranceProgressListener(onTextToSpeechListener);
                mTTS.setSpeechRate(0.5f);
        } else if (brightness == BRIGHT && ledsOn) {
            mTTS = new TextToSpeech(this,
                    new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS) {
                                int result = mTTS.setLanguage(Locale.US);
                                if (result == TextToSpeech.LANG_MISSING_DATA ||
                                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    Log.v(TAG, "Language is not available.");
                                } else {
                                    mTTS.speak("It is bright outside. You might want to switch off the lights",
                                            TextToSpeech.QUEUE_ADD, null,
                                            null);
                                }
                            } else {
                                Log.v(TAG, "Could not initialize TextToSpeech.");
                            }
                        }
                    }
            );
            mTTS.setOnUtteranceProgressListener(onTextToSpeechListener);
            mTTS.setSpeechRate(0.5f);
        }
    }

    /**
     * Called when the BLE device has been found and the address is known from MainController.
     * @param adapter
     * @param address
     * @return
     */
    public boolean connect(BluetoothAdapter adapter, final String address) {
        mBluetoothAdapter = adapter;
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
        ledsControlOn = on;
        if (on) {
            if (darkOutside) {
                writeLEDCharacteristic(ON);
                ledsOn = true;
                return true;
            } else {
                ledsOn = false;
                return false;
            }
        } else {
            writeLEDCharacteristic(OFF);
            ledsOn = false;
            return false;
        }
    }

    /**
     * Turns on or off the proximity sensor on the Arduino device.
     * @param isOn True or false for on or off.
     */
    public void proximityOnOff(boolean isOn) {
        if(isOn) {
            writeProximityCharacteristic(ON);
        } else {
            writeProximityCharacteristic(OFF);
        }
    }

    /**
     * Turns on or off the beeping sound, when objects are near, on the Android device.
     * @param isOn true or false for on or off.
     */
    public void beepingOnOff(boolean isOn) {
        beepingOn = isOn;
        switchBzzzzz(isOn ? 0 : 1); // if beeping is on, then switch vibration off, and vice versa
    }

    private void switchBzzzzz(int isOn) {
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
        BluetoothGattCharacteristic writeCharacteristic = mCustomService.getCharacteristic(VIBRATION_MOTOR_UUID);
        writeCharacteristic.setValue(isOn, BluetoothGattCharacteristic.FORMAT_UINT16,0);
        boolean success;
        do {
            success = mBluetoothGatt.writeCharacteristic(writeCharacteristic);
        } while (!success);
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
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(PROXIMITY_READINGS_CHARACTERISTICS_UUID);
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

    private class OnTextToSpeechListener extends UtteranceProgressListener {

        @Override
        public void onStart(String utteranceId) {}

        @Override
        public void onDone(String utteranceId) {
            mTTS.stop();
            mTTS.shutdown();
        }

        @Override
        public void onError(String utteranceId) {}
    }

    private class PhotocellReader implements Runnable {
        private BluetoothGatt gatt;

        public PhotocellReader(BluetoothGatt gatt) {
            this.gatt = gatt;
        }

        @Override
        public void run() {
            while(true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(SERVICE_UUID)
                        .getCharacteristic(PHOTOCELL_CHARACTERISTIC_UUID);
                gatt.readCharacteristic(characteristic);
            }
        }
    }
}
