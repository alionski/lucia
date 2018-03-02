package se.mah.sensorteknik.aliona.lucia.services;

/**
 * Created 2018-03-02.
 *
 * @author Martin Winqvist
 */

public interface SensorServiceListener {
    void onLightSensorChanged(float light);
}
