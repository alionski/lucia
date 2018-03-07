package se.mah.sensorteknik.aliona.lucia.arduino;
import java.util.HashMap;

/**
 * Created by aliona on 2018-03-02.
 */

public class GattAttributes {

    private static HashMap<String, String> attributes = new HashMap();
    public static String SERVICE_UUID = "d68b43f7-dbdf-4496-badb-0c59f8e7a5ac";
    public static String PROXIMITY_CHARACTERISTICS_UUID = "250416ca-a580-4a39-959d-32bdab46403b";
    public static String LED_CHARACTERISTICS_UUID = "5377a75b-0b55-41f2-a415-bcf8e7510921";
    public static String PHOTOCELL_CHARACTERISTICS_UUID = "5377a75b-0b55-41f2-a415-bcf8e7510921";

    static {
        // Sample Services.
//        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
//        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
//        // Sample Characteristics.
//        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
