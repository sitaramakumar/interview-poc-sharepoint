package com.interview.poc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void getFallsBackToThePropertiesFileValueWhenNoEnvVarIsSet() {
        // jwt.secret is set in the test/main application.properties and there is no
        // JWT_SECRET environment variable in this test run, so the file value wins.
        assertEquals("CHANGE-ME-LOCAL-DEV-JWT-SECRET-32BYTES-MIN", AppConfig.get("jwt.secret"));
    }

    @Test
    void getReturnsTheSuppliedDefaultWhenAKeyIsAbsentEverywhere() {
        assertEquals("fallback", AppConfig.get("no.such.key.anywhere", "fallback"));
    }

    @Test
    void toEnvVarNameConvertsDotsAndHyphensToUnderscoresAndUppercases() {
        assertEquals("JWT_SECRET", AppConfig.toEnvVarName("jwt.secret"));
        assertEquals("SHAREPOINT_CLIENT_SECRET", AppConfig.toEnvVarName("sharepoint-client.secret"));
    }
}
