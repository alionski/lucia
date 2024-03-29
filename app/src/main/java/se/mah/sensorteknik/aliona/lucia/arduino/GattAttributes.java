package se.mah.sensorteknik.aliona.lucia.arduino;

/**
 * Static values for Arduino UUID characteristics. Used in the service.
 */

public class GattAttributes {

    public static String SERVICE_UUID = "d68b43f7-dbdf-4496-badb-0c59f8e7a5ac";
    public static String PROXIMITY_CHARACTERISTICS_UUID = "250416ca-a580-4a39-959d-32bdab46403b"; // proximity data -- read/notify
    public static String SENSOR_ON_OFF_UUID = "b4ded119-98a3-4a4c-a001-015ce5142cc1"; // switch on/off sensor -- write
    public static String LED_CHARACTERISTICS_UUID = "5377a75b-0b55-41f2-a415-bcf8e7510921"; // leds -- write
    public static String PHOTOCELL_CHARACTERISTICS_UUID = "77198360-91bb-421b-9626-564a5c3704f8";
    public static String VIBRATION_CHARACTERISTIC_UUID = "c88f5ba0-dee3-4d6c-8e33-38ad5261cc85";}
