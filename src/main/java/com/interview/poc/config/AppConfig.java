package com.interview.poc.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public static String get(String key) {
        return get(key, null);
    }

    /**
     * Resolves a config value, checking an environment-variable override first
     * (e.g. {@code jwt.secret} → {@code JWT_SECRET}) so secrets don't have to be
     * committed to application.properties for real deployments, then the
     * properties file, then the supplied default.
     */
    public static String get(String key, String defaultValue) {
        String envValue = System.getenv(toEnvVarName(key));
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return props.getProperty(key, defaultValue);
    }

    static String toEnvVarName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }
}
