package com.interview.poc.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashThenVerifyRoundTripsForTheCorrectPassword() {
        String salt = PasswordHasher.randomSalt();
        String hash = PasswordHasher.hash("correct-horse-battery-staple", salt);

        assertTrue(PasswordHasher.verify("correct-horse-battery-staple", salt, hash));
    }

    @Test
    void verifyFailsForTheWrongPassword() {
        String salt = PasswordHasher.randomSalt();
        String hash = PasswordHasher.hash("correct-horse-battery-staple", salt);

        assertFalse(PasswordHasher.verify("wrong-password", salt, hash));
    }

    @Test
    void theSamePasswordWithDifferentSaltsProducesDifferentHashes() {
        String saltA = PasswordHasher.randomSalt();
        String saltB = PasswordHasher.randomSalt();

        assertNotEquals(PasswordHasher.hash("same-password", saltA), PasswordHasher.hash("same-password", saltB));
    }

    @Test
    void randomSaltProducesDistinctValues() {
        assertNotEquals(PasswordHasher.randomSalt(), PasswordHasher.randomSalt());
    }

    @Test
    void hashIsDeterministicForTheSamePasswordAndSalt() {
        String salt = PasswordHasher.randomSalt();
        assertEquals(PasswordHasher.hash("same", salt), PasswordHasher.hash("same", salt));
    }
}
