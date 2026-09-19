package com.interview.poc.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStoreTest {

    private final UserStore store = new UserStore();

    @Test
    void findReturnsEmptyForAnUnknownClientId() {
        assertTrue(store.find("nobody@poc.local").isEmpty());
    }

    @Test
    void theSeededAdminAccountHasTheAdminRoleAndItsDemoPasswordVerifies() {
        UserAccount account = store.find("admin@poc.local").orElseThrow();

        assertEquals("ADMIN", account.getRole());
        assertTrue(PasswordHasher.verify("admin-demo-pass", account.getPasswordSalt(), account.getPasswordHash()));
    }

    @Test
    void theSeededReadAndWriteAccountsHaveTheirOwnRolesAndPasswords() {
        UserAccount read = store.find("read@poc.local").orElseThrow();
        UserAccount write = store.find("write@poc.local").orElseThrow();

        assertEquals("READ", read.getRole());
        assertTrue(PasswordHasher.verify("read-demo-pass", read.getPasswordSalt(), read.getPasswordHash()));

        assertEquals("WRITE", write.getRole());
        assertTrue(PasswordHasher.verify("write-demo-pass", write.getPasswordSalt(), write.getPasswordHash()));
    }

    @Test
    void theWrongPasswordDoesNotVerifyAgainstASeededAccount() {
        UserAccount account = store.find("admin@poc.local").orElseThrow();

        assertTrue(!PasswordHasher.verify("not-the-password", account.getPasswordSalt(), account.getPasswordHash()));
    }
}
