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
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.UUID;

/**
 *
 * The app's service responsible for communicating with the Arduino device and controlling the device from the main controller,
 * and by extension the MainActivity and the frgment's UI.
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
    private static final UUID SENSOR_ON_OFF_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.SENSOR_ON_OFF_UUID);
    private final UUID PROXIMITY_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.PROXIMITY_CHARACTERISTICS_UUID);
    private final UUID LED_CHARACTERISTICS_UUID = UUID.fromString(GattAttributes.LED_CHARACTERISTICS_UUID);
    private final UUID PHOTOCELL_CHARACTERISTIC_UUID = UUID.fromString(GattAttributes.PHOTOCELL_CHARACTERISTICS_UUID);
    private final IBinder mBinder = new LocalBinder();
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private Thread mPhotocellThread;
    private Thread mBeeperThread;
    private ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC , ToneGenerator.MAX_VOLUME);
    private TextToSpeech mTTS;
    private boolean beepingOn = false;
    private boolean darkOutside = false;
    private boolean ledsOn = false;
    private OnTextToSpeechListener onTextToSpeechListener = new OnTextToSpeechListener();
    private int[] brightnessSamples = new int[10];
    private int index = 0;
    /**
     * The callback for the bluetooth connection. Receives updates and delegates decisions to other service methos.
     */
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
                connect(mBluetoothAdapter, mBluetoothDeviceAddress); // RETRY!!!!!!!!!!!!!!!!
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
                mPhotocellThread = new PhotocellReader(gatt);
                mPhotocellThread.start();
                mBeeperThread = new Beeper();
                mBeeperThread.start();
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


    /**
     * Processes the data received from the Arduino. Called from onCharacteristicRead().
     * @param characteristic
     */
    private void manageResults(final BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "CHAR UUID"+characteristic.getUuid());
        if (characteristic.getUuid().equals(PROXIMITY_CHARACTERISTICS_UUID)) {
            final int dataInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            Log.d(TAG, "PROXIMITY VALUE: " + dataInt);
            if (dataInt != 0) {
                if(beepingOn) {
                    ((Beeper)mBeeperThread).setInterval(dataInt*10);
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

    /**
     * Standard bound service method.
     */
    public class LocalBinder extends Binder {
        public ArduinoService getService() {
            return ArduinoService.this;
        }
    }

    /**
     * Standard bound service method.
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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
            if (!darkOutside) {
                brightnessChanged(DARK);
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
     * @param brightness -- whether it has become dark (true) or bright (false).
     */
    private void brightnessChanged(boolean brightness) {
        if (brightness == DARK && !ledsOn) {
            Log.d(TAG, "PHOTOCELL SAY IT'S DARK");
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
            Log.d(TAG, "PHOTOCELL SAY IT'S BRIGHT");
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
     * Established the connection with the Arduino device.
     * @param adapter -- the Bluetooth adapter.
     * @param address -- the device address to connect to.
     * @return -- return true is the connection can be successfully established.
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
     * @param on -- whether it should be switched on or off
     */
    public boolean ledsOnOff(boolean on) {
        if (on) {
            if (darkOutside) {
                writeLEDCharacteristic(ON);
                ledsOn = true;
                Log.d(TAG, "PHOTOCELL Leds ON");
                return true;
            } else {
                ledsOn = false;
                Log.d(TAG, "PHOTOCELL Leds OFF");
                return false;
            }
        } else {
            writeLEDCharacteristic(OFF);
            ledsOn = false;
            Log.d(TAG, "PHOTOCELL Leds OFF");
            return false;
        }
    }

    /**
     * Turns on or off the proximity sensor on the Arduino device.
     * @param isOn -- whether it should be switched on or off
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
     * @param isOn -- whether it should be switched on or off
     */
    public void beepingOnOff(boolean isOn) {
        beepingOn = isOn;
        switchBzzzzz(isOn ? 0 : 1); // if beeping is on, then switch vibration off, and vice versa
    }

    /**
     * Instructs the Arduino to switch on or off the vibration motor.
     * @param isOn -- whether it should be switched on or off
     */
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
     * Called when there is a new characteristic from the Arduino to read.
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Instructs the Arduino to switch on or off readings from the proximity sensor.
     * @param onOff
     */
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
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(SENSOR_ON_OFF_CHARACTERISTICS_UUID);
        mWriteCharacteristic.setValue(onOff, BluetoothGattCharacteristic.FORMAT_UINT16,0);
        boolean success;
        do {
            success = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
        } while (!success);
    }

    /**
     * Instructs the Arduino to switch on or off the LED.
     * @param onOff
     */
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
     * Callback fot the TextToSpeech object (the one that says when it's dark or bright).
     * Stops and closes the utterance, i.e. cleans up.
     */
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

    /**
     * Thread that is responsible for continuously reading the photocell data.
     * The control is then passed on to the GattCallback.
     */
    private class PhotocellReader extends Thread {
        private BluetoothGatt gatt;
        private volatile boolean isRunning = true;

        public PhotocellReader(BluetoothGatt gatt) {
            this.gatt = gatt;
        }

        @Override
        public void run() {
            while(isRunning) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (gatt != null) {
                    BluetoothGattCharacteristic characteristic = gatt
                            .getService(SERVICE_UUID)
                            .getCharacteristic(PHOTOCELL_CHARACTERISTIC_UUID);
                    gatt.readCharacteristic(characteristic);
                }
            }
        }

        public void killThread() {
            isRunning = false;
        }
    }

    /**
     * Thread that is responsible for making the beeping sounds when there are objects withing 2 meters from the user.
     * Only makes sounds if the sensor and the sounds option are on. Started when the app connects to the device.
     */
    private class Beeper extends Thread{
        private int interval;
        private boolean beeping = true;
        private long lastTime;
        private volatile boolean isRunning = true;

        @Override
        public void run() {
            while(isRunning) {
                if(beepingOn) {
                    long time = System.currentTimeMillis();
                    if (time - lastTime >= interval) {
                        lastTime = time;
                        if (beeping) {
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);
                            beeping = false;
                        } else {
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);
                            beeping = true;
                        }
                    }
                }
            }
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }

        public void killThread() {
            isRunning = false;
        }
    }

    /**
     * Called from MainController's disconnect() after MainActivity's onDestroy().
     * Disconnects from the Arduino device and closes the connection.
     * @param intent
     * @return
     */
    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        close();
        return super.onUnbind(intent);
    }


    /**
     * Disconnects an existing connection to the Arduino device.
     * Called from MainControllers's disconnect() via onUnbind().
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app calls this method to ensure resources are
     * released properly. Called from MainControllers's disconnect() via onUnbind().
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Called when the activity unbinds the service, after the service calls stopSelf().
     * Stops the threads to exit gracefully.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBeeperThread != null) {
            ((Beeper) mBeeperThread).killThread();
        }
        if (mPhotocellThread != null) {
            ((PhotocellReader) mPhotocellThread).killThread();
        }
    }
}
