package com.interview.poc.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRoleTest {

    @ParameterizedTest
    @CsvSource({
            "READ,   GET,    true",
            "READ,   PUT,    false",
            "READ,   DELETE, false",
            "WRITE,  GET,    true",
            "WRITE,  PUT,    true",
            "WRITE,  DELETE, false",
            "ADMIN,  GET,    true",
            "ADMIN,  PUT,    true",
            "ADMIN,  DELETE, true",
    })
    void allowsReflectsEachRolesDocumentedPermissions(UserRole role, String method, boolean expected) {
        assertEquals(expected, role.allows(method));
    }

    @Test
    void allowsIsCaseInsensitiveOnTheHttpMethod() {
        assertTrue(UserRole.WRITE.allows("put"));
        assertTrue(UserRole.WRITE.allows("Put"));
    }

    @Test
    void allowsReturnsFalseRatherThanThrowingOnANullMethod() {
        // Regression test: allows() used to call method.toUpperCase() unconditionally
        // and threw a NullPointerException for a null method instead of denying it.
        assertFalse(UserRole.READ.allows(null));
        assertFalse(UserRole.ADMIN.allows(null));
    }

    @Test
    void fromClientIdMatchesOnAnAdminSubstringCaseInsensitively() {
        assertEquals(UserRole.ADMIN, UserRole.fromClientId("Service-Admin-01"));
    }

    @Test
    void fromClientIdMatchesOnAWriteSubstringWhenNotAdmin() {
        assertEquals(UserRole.WRITE, UserRole.fromClientId("batch-write-job"));
    }

    @Test
    void fromClientIdDefaultsToReadForAnUnrecognizedClientId() {
        assertEquals(UserRole.READ, UserRole.fromClientId("some-other-service"));
    }

    @Test
    void fromClientIdDefaultsToReadForANullClientId() {
        assertEquals(UserRole.READ, UserRole.fromClientId(null));
    }
}
