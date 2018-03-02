package se.mah.sensorteknik.aliona.lucia.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created 2018-03-02.
 *
 * @author Martin Winqvist
 */

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = SensorService.class.getSimpleName();

    private IBinder localBinder = new LocalBinder();

    private SensorServiceListener listener;

    private SensorManager sensorManager;
    private Sensor lightSensor;

    private float oldLightValue;

    public class LocalBinder extends Binder {
        public SensorService getService() {
            return SensorService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        return localBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this, lightSensor);
        sensorManager = null;
        lightSensor = null;
        listener = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener != null) {
            float light = event.values[0];

            if (light != oldLightValue) {
                listener.onLightSensorChanged(light);
                oldLightValue = light;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void setSensorServiceListener(SensorServiceListener listener) {
        this.listener = listener;
    }
}
