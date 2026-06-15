package api_utilities;

import java.io.FileInputStream;
import java.util.Properties;

public class ConfigReader {

    public static Properties prop;

    public static void loadProperties() {
        try {
            prop = new Properties();

            FileInputStream fis = new FileInputStream(
                    "src/test/resources/config/config.properties");

            prop.load(fis);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getprop(String key) {

        if (prop == null) {
            loadProperties();
        }

        return prop.getProperty(key);
    }
}