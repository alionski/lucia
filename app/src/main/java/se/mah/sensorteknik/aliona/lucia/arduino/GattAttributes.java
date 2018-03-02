package se.mah.sensorteknik.aliona.lucia.arduino;
import java.util.HashMap;

/**
 * Created by aliona on 2018-03-02.
 */

public class GattAttributes {

    private static HashMap<String, String> attributes = new HashMap();
    public static String PROXIMITY_UUID = "d68b43f7-dbdf-4496-badb-0c59f8e7a5ac";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

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
