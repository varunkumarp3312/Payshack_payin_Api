package com.payshack.payin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);
    private static final String DEFAULT_ENV = "dev";
    private static Properties properties;

    private ConfigReader() {}

    private static synchronized Properties getProperties() {
        if (properties != null) return properties;

        properties = new Properties();
        String env = System.getProperty("env", DEFAULT_ENV);
        String fileName = DEFAULT_ENV.equals(env)
                ? "config/config.properties"
                : "config/config-" + env + ".properties";

        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new RuntimeException("Config file not found on classpath: " + fileName);
            }
            properties.load(input);
            log.info("Loaded config: {} (env={})", fileName, env);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + fileName, e);
        }
        return properties;
    }

    public static String get(String key) {
        String value = getProperties().getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Missing required config key: " + key);
        }
        return value.trim();
    }

    public static String get(String key, String defaultValue) {
        String value = getProperties().getProperty(key);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}
