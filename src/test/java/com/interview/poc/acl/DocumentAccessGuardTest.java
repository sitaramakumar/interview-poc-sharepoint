package com.interview.poc.acl;

import com.interview.poc.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentAccessGuardTest {

    @Test
    void allowsAnyoneWhenTheDocumentHasNoAclRecord() {
        // Untagged content (never uploaded through createOrUpdateDocument) is
        // gated by the coarse role/method check alone, same as before ACLs existed.
        assertTrue(DocumentAccessGuard.allows(null, "some-caller", UserRole.READ));
    }

    @Test
    void allowsTheOwnerRegardlessOfRole() {
        DocumentAcl acl = new DocumentAcl("owner-42", List.of("ADMIN"));

        assertTrue(DocumentAccessGuard.allows(acl, "owner-42", UserRole.READ));
    }

    @Test
    void allowsACallerWhoseRoleIsInAllowedRoles() {
        DocumentAcl acl = new DocumentAcl("owner-42", List.of("WRITE", "ADMIN"));

        assertTrue(DocumentAccessGuard.allows(acl, "someone-else", UserRole.WRITE));
    }

    @Test
    void allowedRolesMatchIsCaseInsensitive() {
        DocumentAcl acl = new DocumentAcl("owner-42", List.of("write"));

        assertTrue(DocumentAccessGuard.allows(acl, "someone-else", UserRole.WRITE));
    }

    @Test
    void deniesANonOwnerWhoseRoleIsNotInAllowedRoles() {
        // Regression case for the "coarse role check passes, resource check
        // doesn't" scenario: an ADMIN globally can call DELETE, but this
        // specific document restricts DELETE-capable access to a different
        // owner/role set.
        DocumentAcl acl = new DocumentAcl("owner-42", List.of("WRITE"));

        assertFalse(DocumentAccessGuard.allows(acl, "another-admin", UserRole.ADMIN));
    }

    @Test
    void deniesWhenAllowedRolesIsNull() {
        DocumentAcl acl = new DocumentAcl("owner-42", null);

        assertFalse(DocumentAccessGuard.allows(acl, "someone-else", UserRole.ADMIN));
    }

    @Test
    void checkThrowsAccessDeniedExceptionInsteadOfReturningFalse() {
        DocumentAcl acl = new DocumentAcl("owner-42", List.of("WRITE"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> DocumentAccessGuard.check(acl, "another-caller", UserRole.READ, "doc-7"));
        assertTrue(ex.getMessage().contains("doc-7"));
    }

    @Test
    void checkDoesNotThrowWhenAccessIsAllowed() {
        DocumentAccessGuard.check(null, "anyone", UserRole.READ, "doc-7");
    }
}
